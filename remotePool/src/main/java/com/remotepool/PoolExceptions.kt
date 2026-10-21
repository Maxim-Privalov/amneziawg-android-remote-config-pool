package com.remotepool

/** Typed errors, so the UI can map them to localized strings (like TunnelManager does with R.string.*). */
sealed class PoolException(message: String) : Exception(message)

class InvalidPoolNameException(val poolName: String) :
    PoolException("Invalid pool name: \"$poolName\"")

class PoolAlreadyExistsException(val poolName: String) :
    PoolException("Pool or tunnel \"$poolName\" already exists")

class PoolNotFoundException(val poolName: String) :
    PoolException("Pool \"$poolName\" not found")

class RemoteConfigNotFoundException(val poolName: String) :
    PoolException("No matching config found in remote storage for pool \"$poolName\"")
