package com.voiceassistant.tasks

import kotlinx.coroutines.flow.Flow

internal class VoiceTaskRepository(private val dao: VoiceTaskDao) {
    val tasks: Flow<List<VoiceTaskEntity>> = dao.observeAll()
    suspend fun enqueue(id: String, audioPath: String?, returnAction: String?, returnPackage: String?, partnerSessionId: String? = null): String {
        dao.upsert(VoiceTaskEntity(id, VoiceTaskStatus.QUEUED, audioPath, returnAction, returnPackage, partnerSessionId, null, null, System.currentTimeMillis(), null))
        return id
    }
    suspend fun claimNext(): VoiceTaskEntity? {
        val next = dao.nextQueued() ?: return null
        return if (dao.markRunning(next.id) == 1) dao.get(next.id) else null
    }
    suspend fun queued() = dao.queued()
    suspend fun complete(id: String, transcript: String) = dao.finish(id, VoiceTaskStatus.COMPLETED, transcript, null, System.currentTimeMillis())
    suspend fun fail(id: String, message: String, cancelled: Boolean = false) = dao.finish(id, if (cancelled) VoiceTaskStatus.CANCELLED else VoiceTaskStatus.FAILED, null, message, System.currentTimeMillis())
    suspend fun recoverInterrupted() = dao.recoverRunning()
    suspend fun deleteExpiredAudio(nowMs: Long): Int {
        val cutoff = nowMs - 24L * 60 * 60 * 1000
        return dao.clearExpiredAudio(cutoff)
    }
}
