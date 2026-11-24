package com.crushcoach.memory

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.crushcoach.Prefs
import com.crushcoach.ai.CoachException

/** Việc chạy ngầm mỗi ngày (và khi cần): cô đọng tin nhắn mới vào sổ tay. */
class DigestWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val prefs = Prefs(applicationContext)
        if (!prefs.memoryEnabled || prefs.apiKey.isBlank()) return Result.success()
        return try {
            Digester(applicationContext).runAll()
            Result.success()
        } catch (e: CoachException) {
            if (e.retryable && runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
