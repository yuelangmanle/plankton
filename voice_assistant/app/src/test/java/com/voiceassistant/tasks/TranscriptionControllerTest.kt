package com.voiceassistant.tasks

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TranscriptionControllerTest {
    @Test
    fun queueStartsOnlyOneTaskUntilFirstTaskCompletes() = runTest {
        val runner = FakeRunner()
        val controller = TranscriptionController(this, runner)
        controller.enqueue("a")
        controller.enqueue("b")
        runCurrent()
        assertEquals(listOf("a"), runner.started)
        runner.complete("a")
        runCurrent()
        assertEquals(listOf("a", "b"), runner.started)
        runner.complete("b")
        runCurrent()
    }

    private class FakeRunner : TranscriptionTaskRunner {
        val started = mutableListOf<String>()
        private val gates = mutableMapOf<String, CompletableDeferred<Unit>>()
        override suspend fun run(taskId: String) { started += taskId; gates.getOrPut(taskId) { CompletableDeferred() }.await() }
        fun complete(taskId: String) { gates.getOrPut(taskId) { CompletableDeferred() }.complete(Unit) }
    }
}
