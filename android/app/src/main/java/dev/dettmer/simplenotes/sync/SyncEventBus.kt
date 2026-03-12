package dev.dettmer.simplenotes.sync

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Event bus for sync completion events.
 * Replaces LocalBroadcastManager for SyncWorker → ComposeMainActivity communication.
 *
 * v2.0.0: Migration from deprecated LocalBroadcastManager to SharedFlow.
 */
object SyncEventBus {

    private val _events = MutableSharedFlow<SyncEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<SyncEvent> = _events.asSharedFlow()

    fun emit(event: SyncEvent) {
        _events.tryEmit(event)
    }
}

sealed class SyncEvent {
    data class SyncCompleted(val success: Boolean, val count: Int) : SyncEvent()
}
