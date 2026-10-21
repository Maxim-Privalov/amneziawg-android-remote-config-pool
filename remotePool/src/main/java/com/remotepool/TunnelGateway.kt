package com.remotepool

/**
 * What [PoolManager] needs from the tunnel layer.
 *
 * `remotePool` is a library and cannot see `ui` (where TunnelManager lives), so the dependency is inverted:
 * the module declares the port, the app implements it over TunnelManager.
 *
 * All members are called from Dispatchers.Main.immediate (the tunnel list is UI-bound).
 */
interface TunnelGateway {
    /** Suspends until the regular tunnels are loaded (so that name collisions can be detected). */
    suspend fun awaitReady()

    fun isNameInvalid(name: String): Boolean

    suspend fun containsTunnel(name: String): Boolean

    /** Adds a tunnel with no config and no file on disk, the config is fetched on connect. */
    suspend fun addVirtualTunnel(name: String)

    suspend fun renameTunnel(oldName: String, newName: String)

    /** Brings the tunnel down if needed and removes it from the list. Must not touch the config store. */
    suspend fun deleteTunnel(name: String)

    /** Re-raises tunnels that were running before the process died (only the given ones). */
    suspend fun restoreState(names: Collection<String>)
}
