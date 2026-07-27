package com.voiceassistant.tasks

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceTaskRepositoryTest {
    @Test
    fun marksTheRequestedTaskInsteadOfClaimingAnOlderQueueEntry() = kotlinx.coroutines.test.runTest {
        val dao = FakeVoiceTaskDao(task("first"), task("second"))
        val repository = VoiceTaskRepository(dao)

        assertTrue(repository.markRunning("second"))

        assertEquals(VoiceTaskStatus.QUEUED, dao.get("first")!!.status)
        assertEquals(VoiceTaskStatus.RUNNING, dao.get("second")!!.status)
    }

    @Test
    fun cancellationIsTerminalAndInterruptedRunningTasksRecoverToQueued() = kotlinx.coroutines.test.runTest {
        val dao = FakeVoiceTaskDao(task("running", VoiceTaskStatus.RUNNING), task("cancel"))
        val repository = VoiceTaskRepository(dao)

        assertEquals(1, repository.recoverInterrupted())
        repository.fail("cancel", "已取消转写", cancelled = true)

        assertEquals(VoiceTaskStatus.QUEUED, dao.get("running")!!.status)
        assertEquals(VoiceTaskStatus.CANCELLED, dao.get("cancel")!!.status)
        assertEquals("已取消转写", dao.get("cancel")!!.errorMessage)
    }

    @Test
    fun expiredAudioReferenceIsDeletedWithoutDeletingTranscript() = kotlinx.coroutines.test.runTest {
        val completed = task("complete").copy(
            status = VoiceTaskStatus.COMPLETED,
            transcript = "桡足类 5",
            finishedAtMs = 1L,
        )
        val dao = FakeVoiceTaskDao(completed)
        val repository = VoiceTaskRepository(dao)

        assertEquals(1, repository.deleteExpiredAudio(24L * 60 * 60 * 1000L + 2L))
        assertEquals(null, repository.get("complete")!!.audioPath)
        assertEquals("桡足类 5", repository.get("complete")!!.transcript)
    }

    private fun task(id: String, status: VoiceTaskStatus = VoiceTaskStatus.QUEUED) = VoiceTaskEntity(
        id = id,
        status = status,
        audioPath = "content://test/$id",
        returnAction = "test.action",
        returnPackage = "test.package",
        partnerSessionId = null,
        overridesPayload = null,
        transcript = null,
        errorMessage = null,
        createdAtMs = 1,
        finishedAtMs = null,
    )

    private class FakeVoiceTaskDao(vararg tasks: VoiceTaskEntity) : VoiceTaskDao {
        private val values = tasks.associateBy { it.id }.toMutableMap()
        private val flow = MutableStateFlow(values.values.toList())
        override fun observeAll(): Flow<List<VoiceTaskEntity>> = flow
        override suspend fun get(id: String): VoiceTaskEntity? = values[id]
        override suspend fun nextQueued(): VoiceTaskEntity? = values.values.filter { it.status == VoiceTaskStatus.QUEUED }.minByOrNull { it.createdAtMs }
        override suspend fun queued(): List<VoiceTaskEntity> = values.values.filter { it.status == VoiceTaskStatus.QUEUED }
        override suspend fun upsert(task: VoiceTaskEntity) { values[task.id] = task; publish() }
        override suspend fun finish(id: String, status: VoiceTaskStatus, transcript: String?, errorMessage: String?, finishedAtMs: Long?) {
            val task = values[id] ?: return
            values[id] = task.copy(status = status, transcript = transcript, errorMessage = errorMessage, finishedAtMs = finishedAtMs)
            publish()
        }
        override suspend fun markRunning(id: String): Int {
            val task = values[id] ?: return 0
            if (task.status != VoiceTaskStatus.QUEUED) return 0
            values[id] = task.copy(status = VoiceTaskStatus.RUNNING)
            publish()
            return 1
        }
        override suspend fun recoverRunning(): Int {
            val running = values.values.filter { it.status == VoiceTaskStatus.RUNNING }
            running.forEach { values[it.id] = it.copy(status = VoiceTaskStatus.QUEUED) }
            publish()
            return running.size
        }
        override suspend fun clearExpiredAudio(cutoff: Long): Int {
            val expired = values.values.filter { it.finishedAtMs != null && it.finishedAtMs < cutoff && it.audioPath != null }
            expired.forEach { values[it.id] = it.copy(audioPath = null) }
            publish()
            return expired.size
        }
        private fun publish() { flow.value = values.values.toList() }
    }
}
