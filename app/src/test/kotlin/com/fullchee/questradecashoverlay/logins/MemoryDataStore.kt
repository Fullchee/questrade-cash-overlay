package com.fullchee.questradecashoverlay.logins

import androidx.datastore.core.DataStore
import java.io.IOException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal fun memoryDataStore() = MemoryDataStore(StoredLogins())

/** Set [writesBeforeCrash] to make a later write fail, as if the process died before it reached disk. */
internal class MemoryDataStore<T>(initial: T) : DataStore<T> {
    private val state = MutableStateFlow(initial)
    private val writes = Mutex()
    var writesBeforeCrash = Int.MAX_VALUE

    override val data = state

    // Like DataStore, a write from a cancelled coroutine fails instead of reaching disk.
    override suspend fun updateData(transform: suspend (t: T) -> T): T = writes.withLock {
        currentCoroutineContext().ensureActive()
        if (writesBeforeCrash-- == 0) throw IOException("process died")
        transform(state.value).also { state.value = it }
    }
}
