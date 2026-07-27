package com.voiceassistant.tasks

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.ArrayDeque

internal interface TranscriptionTaskRunner {
    suspend fun run(taskId: String)
}

internal class TranscriptionController(
    private val scope: CoroutineScope,
    private val runner: TranscriptionTaskRunner,
) {
    private val queue = ArrayDeque<String>()
    private var current: Job? = null

    fun enqueue(taskId: String) {
        if (taskId.isBlank() || queue.contains(taskId)) return
        queue.addLast(taskId)
        drain()
    }

    fun cancel(taskId: String) {
        if (queue.remove(taskId)) return
        if (currentTaskId == taskId) current?.cancel()
    }

    private var currentTaskId: String? = null
    private fun drain() {
        if (current?.isActive == true) return
        val next = if (queue.isEmpty()) null else queue.removeFirst()
        if (next == null) return
        currentTaskId = next
        current = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try { runner.run(next) } finally { currentTaskId = null; current = null; drain() }
        }
    }
}
