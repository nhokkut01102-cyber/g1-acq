package com.crushcoach.memory

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.crushcoach.Prefs
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/** Hẹn giờ cô đọng sổ tay mỗi ngày khoảng 23h, và chạy bù khi đã quá 1 ngày chưa cô đọng. */
object DigestScheduler {
    private const val DAILY = "crush-digest-daily"
    private const val SOON = "crush-digest-soon"
    private val DIGEST_TIME: LocalTime = LocalTime.of(23, 0)

    private fun network() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** Bật/tắt lịch hằng ngày theo cài đặt. Gọi lại thoải mái, không tạo lịch trùng. */
    fun sync(context: Context) {
        val prefs = Prefs(context)
        val wm = WorkManager.getInstance(context)
        if (!prefs.memoryEnabled || !prefs.dailyDigest) {
            wm.cancelUniqueWork(DAILY)
            return
        }
        val request = PeriodicWorkRequestBuilder<DigestWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(millisUntil(DIGEST_TIME), TimeUnit.MILLISECONDS)
            .setConstraints(network())
            .build()
        wm.enqueueUniquePeriodicWork(DAILY, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Chạy cô đọng sớm nhất có thể (khi có mạng), nếu chưa có lượt nào đang chờ. */
    fun runSoon(context: Context) {
        val request = OneTimeWorkRequestBuilder<DigestWorker>().setConstraints(network()).build()
        WorkManager.getInstance(context).enqueueUniqueWork(SOON, ExistingWorkPolicy.KEEP, request)
    }

    private fun millisUntil(time: LocalTime): Long {
        val now = ZonedDateTime.now()
        var next = now.with(time)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next).toMillis()
    }
}
