package org.amnezia.awg.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class PoolOverlayViewModel : ViewModel() {
    private val _open = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val open: SharedFlow<Unit> = _open.asSharedFlow()

    fun requestOpen() {
        _open.tryEmit(Unit)
    }
}