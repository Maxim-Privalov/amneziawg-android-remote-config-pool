package org.amnezia.awg.model

import com.remotepool.TunnelGateway
import org.amnezia.awg.backend.Tunnel

/**
 * Lets PoolManager (library, cannot see ui) drive TunnelManager. Calls the low-level TunnelManager methods
 * directly, not ObservableTunnel.setNameAsync/deleteAsync: those route pools back to PoolManager.
 */
class TunnelManagerGateway(private val manager: TunnelManager) : TunnelGateway {
    override suspend fun awaitReady() {
        manager.getTunnels()
    }

    override fun isNameInvalid(name: String) = Tunnel.isNameInvalid(name)

    override suspend fun containsTunnel(name: String) = manager.getTunnels().containsKey(name)

    override suspend fun addVirtualTunnel(name: String) {
        manager.addVirtualTunnel(name)
    }

    override suspend fun renameTunnel(oldName: String, newName: String) {
        manager.setTunnelName(find(oldName), newName)
    }

    override suspend fun deleteTunnel(name: String) {
        manager.delete(find(name))
    }

    override suspend fun restoreState(names: Collection<String>) {
        manager.restoreState(true, names)
    }

    private suspend fun find(name: String): ObservableTunnel =
        manager.getTunnels()[name] ?: throw NoSuchElementException("Tunnel \"$name\" not found")
}
