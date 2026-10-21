package com.remotepool

import android.util.Log
import com.remotepool.BuildConfig
import com.remotepool.models.AuthUser
import com.remotepool.models.Pool
import com.remotepool.secrets.SecretsManager
import com.remotepool.storage.PoolStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import retrofit2.HttpException

/**
 * Pool is a "virtual tunnel": it looks like a tunnel in the list, but behind it there is only access to a
 * remote source. The real config is downloaded when the tunnel is switched UP (see [fetchConfigText]) and
 * lives in memory only, it is never written to the config store.
 *
 * PoolManager owns pools (storage, secrets, credentials) and delegates everything tunnel-related to
 * [TunnelGateway]. Every pool has a placeholder tunnel with the same name, so a pool name has to be valid
 * and free in both worlds.
 *
 * Threading: [poolMap] is only touched on Dispatchers.Main.immediate (same as TunnelManager), [pools] is its snapshot;
 * disk and network work runs on Dispatchers.IO.
 */
class PoolManager(
    private val poolStore: PoolStore,
    private val secretsManager: SecretsManager,
    private val repository: RemotePoolRepository,
    private val tunnels: TunnelGateway,
    private val scope: CoroutineScope,
    nameComparator: Comparator<String>,
    private val tokenExpiredHours: Int = BuildConfig.TOKEN_EXPIRED_HOURS,
) {
    private val loaded = CompletableDeferred<Unit>()
    private val poolMap = HashMap<String, Pool>()
    private val poolOrder = Comparator<Pool> { a, b -> nameComparator.compare(a.poolName, b.poolName) }

    private val _pools = MutableStateFlow<List<Pool>>(emptyList())

    /** Current pools, sorted by name. Empty until [awaitLoaded] returns. */
    val pools: StateFlow<List<Pool>> = _pools.asStateFlow()

    /** One token refresh / config download at a time, otherwise two connects would race for the token. */
    private val remoteMutex = Mutex()

    // region lifecycle

    fun onCreate() {
        scope.launch {
            try {
                tunnels.awaitReady()
                onPoolsLoaded(withContext(Dispatchers.IO) { loadPoolsFromStorage() })
            } catch (e: Throwable) {
                Log.e(TAG, Log.getStackTraceString(e))
                loaded.completeExceptionally(e)
            }
        }
    }

    /** Suspends until the pools are loaded from disk. */
    suspend fun awaitLoaded() = loaded.await()

    /** Publishes the current state of [poolMap]. Main thread only. */
    private fun publish() {
        _pools.value = poolMap.values.sortedWith(poolOrder)
    }

    private fun loadPoolsFromStorage(): List<Pool> =
        poolStore.enumerate().mapNotNull { name ->
            try {
                // the file name is the authority for the key
                poolStore.load(name).let { if (it.poolName == name) it else it.withName(name) }
            } catch (e: Throwable) {
                Log.e(TAG, "Skipping unreadable pool \"$name\"", e)
                null
            }
        }

    private suspend fun onPoolsLoaded(present: List<Pool>) = withContext(Dispatchers.Main.immediate) {
        for (pool in present) {
            if (tunnels.containsTunnel(pool.poolName)) {
                Log.w(TAG, "Pool \"${pool.poolName}\" collides with an existing tunnel, skipping")
                continue
            }
            try {
                addPool(pool)
            } catch (e: Throwable) {
                Log.e(TAG, "Couldn't add pool \"${pool.poolName}\"", e)
            }
        }
        publish()
        loaded.complete(Unit)
        try {
            tunnels.restoreState(poolMap.keys)
        } catch (e: Throwable) {
            Log.e(TAG, Log.getStackTraceString(e))
        }
    }

    /** Adds the pool to the list and creates its placeholder tunnel. Main thread only. */
    private suspend fun addPool(pool: Pool) {
        poolMap[pool.poolName] = pool
        try {
            tunnels.addVirtualTunnel(pool.poolName)
        } catch (e: Throwable) {
            poolMap.remove(pool.poolName)
            throw e
        }
    }

    // endregion

    // region queries (Main thread)

    fun isPool(name: String): Boolean = poolMap.containsKey(name)

    fun getPool(name: String): Pool? = poolMap[name]

    // endregion

    // region create / delete / rename / credentials

    /**
     * @param verify if true, tries to log in right away: wrong credentials are rejected up front and
     * the first connect doesn't need to fetch a token.
     */
    suspend fun create(
        name: String,
        remoteUser: AuthUser,
        password: String,
        verify: Boolean = true,
    ): Pool = withContext(Dispatchers.Main.immediate) {
        checkNameAvailable(name)
        val passwordWasShared = isUserShared(remoteUser, except = null)
        val pool = Pool(name, remoteUser, tokenExpiredHours)
        var stored = false
        try {
            withContext(Dispatchers.IO) {
                remoteUser.setPassword(password, secretsManager)
                if (verify) pool.setToken(repository.getToken(remoteUser, password), secretsManager)
                poolStore.create(pool)
                stored = true
            }
            addPool(pool)
            publish()
            pool
        } catch (e: Throwable) {
            withContext(NonCancellable + Dispatchers.IO) {
                runCatching {
                    if (stored) poolStore.delete(name)
                    pool.clearToken(secretsManager)
                    // don't wipe the password of an account another pool is still using
                    if (!passwordWasShared) secretsManager.removeSecret(remoteUser.toJson())
                }
            }
            throw e
        }
    }

    suspend fun delete(name: String) = withContext(Dispatchers.Main.immediate) {
        val pool = poolMap[name] ?: throw PoolNotFoundException(name)
        // TunnelManager rolls itself back on failure, so it goes first.
        tunnels.deleteTunnel(name)
        try {
            withContext(Dispatchers.IO) { poolStore.delete(name) }
        } catch (e: Throwable) {
            runCatching { tunnels.addVirtualTunnel(name) }
            throw e
        }
        poolMap.remove(name)
        publish()
        val passwordShared = isUserShared(pool.remoteUser, except = name)
        withContext(NonCancellable + Dispatchers.IO) {
            runCatching {
                pool.clearToken(secretsManager)
                if (!passwordShared) secretsManager.removeSecret(pool.remoteUser.toJson())
            }
        }
    }

    suspend fun setPoolName(name: String, newName: String): String = withContext(Dispatchers.Main.immediate) {
        val old = poolMap[name] ?: throw PoolNotFoundException(name)
        if (newName == name) return@withContext name
        checkNameAvailable(newName)

        // 1. file + poolName inside it. The cached token is bound to the old name and is not migrated: it is
        //    dropped, and the next connect gets a fresh one (obtainToken copes with a missing token).
        val renamed = withContext(Dispatchers.IO) {
            poolStore.rename(name, newName).also { old.clearToken(secretsManager) }
        }
        // 2. the placeholder tunnel. isPool(name) is still true here, which TunnelManager relies on.
        try {
            tunnels.renameTunnel(name, newName)
        } catch (e: Throwable) {
            runCatching { withContext(Dispatchers.IO) { poolStore.rename(newName, name) } }
            throw e
        }
        // 3. in-memory state
        poolMap.remove(name)
        poolMap[newName] = renamed
        publish()
        newName
    }

    /** Replaces the account of the pool. The cached token belongs to the old account and is dropped. */
    suspend fun setCredentials(name: String, remoteUser: AuthUser, password: String) =
        withContext(Dispatchers.Main.immediate) {
            val old = poolMap[name] ?: throw PoolNotFoundException(name)
            val updated = withContext(Dispatchers.IO) {
                remoteUser.setPassword(password, secretsManager)
                old.clearToken(secretsManager)
                old.copy(remoteUser = remoteUser).also { poolStore.save(it) }
            }
            poolMap[name] = updated
            publish()
            if (old.remoteUser != remoteUser && !isUserShared(old.remoteUser, except = null)) {
                withContext(Dispatchers.IO) { runCatching { secretsManager.removeSecret(old.remoteUser.toJson()) } }
            }
        }

    // endregion

    // region remote config

    /**
     * Called by TunnelManager when a pool tunnel goes UP: logs in (if the cached token is missing or expired),
     * downloads the latest config and returns it as awg-quick text.
     *
     * @return null if [name] is not a pool
     */
    suspend fun fetchConfigText(name: String): String? {
        val pool = withContext(Dispatchers.Main.immediate) { poolMap[name] } ?: return null
        return withContext(Dispatchers.IO) {
            remoteMutex.withLock {
                try {
                    repository.getLastConfig(obtainToken(pool, forceRefresh = false))
                } catch (e: HttpException) {
                    // token revoked before our own expiry timer fired: one retry with a fresh one
                    if (e.code() != 401 && e.code() != 403) throw e
                    repository.getLastConfig(obtainToken(pool, forceRefresh = true))
                } ?: throw RemoteConfigNotFoundException(name)
            }
        }
    }

    private suspend fun obtainToken(pool: Pool, forceRefresh: Boolean): String {
        if (!forceRefresh && !pool.checkTokenExpired())
            pool.revealToken(secretsManager)?.let { return it }
        val password = pool.remoteUser.revealPassword(secretsManager) ?: throw RemoteConfigNotFoundException("user password doesn't found: ${pool.poolName}")
        val token = repository.getToken(pool.remoteUser, password)
        pool.setToken(token, secretsManager)
        // persists lastTokenCreated; failing to do so only costs one extra login after a restart
        runCatching { poolStore.save(pool) }.onFailure { Log.w(TAG, "Couldn't persist token timestamp", it) }
        return token
    }

    // endregion

    private suspend fun checkNameAvailable(name: String) {
        if (PoolStore.isNameInvalid(name) || tunnels.isNameInvalid(name))
            throw InvalidPoolNameException(name)
        if (poolMap.containsKey(name) || tunnels.containsTunnel(name))
            throw PoolAlreadyExistsException(name)
    }

    /** Does any pool other than [except] use the same account (and thus the same stored password)? */
    private fun isUserShared(user: AuthUser, except: String?): Boolean =
        poolMap.values.any { it.poolName != except && it.remoteUser == user }

    companion object {
        private const val TAG = "AmneziaWG/PoolManager"
    }
}
