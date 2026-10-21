package com.remotepool.exceptions

/** Typed errors, so the UI can map them to localized strings (like TunnelManager does with R.string.*). */
sealed class PoolException(message: String, cause: Throwable? = null) : Exception(message, cause)

class InvalidPoolNameException(val poolName: String) :
    PoolException("Invalid pool name: \"$poolName\"")

class PoolAlreadyExistsException(val poolName: String) :
    PoolException("Pool or tunnel \"$poolName\" already exists")

class PoolNotFoundException(val poolName: String) :
    PoolException("Pool \"$poolName\" not found")

class RemoteConfigNotFoundException(val poolName: String) :
    PoolException("No matching config found in remote storage for pool \"$poolName\"")

/** Name or credentials can't be changed while the pool is connected (or connecting). */
class PoolBusyException(val poolName: String) :
    PoolException("Pool \"$poolName\" is connected, disconnect it first")

class InvalidDsnException(val reason: Reason, val detail: String? = null) :
    PoolException("Invalid DSN: $reason" + (detail?.let { " ($it)" } ?: "")) {
    enum class Reason { FORMAT, PROVIDER, EMPTY_FIELD }
}

/**
+ * A step of the connect pipeline failed. [stage] tells which one, [cause] why
+ * (network, auth, bad config...). Lets the UI say "couldn't download the config: no connection".
+ */
class PoolConnectionException(val stage: Stage, cause: Throwable) :
PoolException("Pool connect failed at $stage: ${cause.message}", cause) {
    enum class Stage {
        /** Token from the pool or login with the stored password. */
        AUTH,
        /** Listing / downloading the latest config from the remote source. */
        DOWNLOAD,
        /** Turning the downloaded text into a config (done by the app, which owns the config format). */
        PARSE,
    }
}