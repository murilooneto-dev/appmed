package com.appmed.lembrete.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.appmed.lembrete.MainActivity
import com.appmed.lembrete.data.MedRepository

/**
 * Agenda os alarmes do sistema para as próximas doses pendentes.
 *
 * O Android limita a quantidade de alarmes por app, então apenas as próximas
 * [MAX_SCHEDULED] doses ficam agendadas; a cada alarme disparado (e após reiniciar
 * o aparelho) a lista é reagendada, cobrindo o tratamento inteiro.
 */
object AlarmScheduler {
    private const val MAX_SCHEDULED = 100
    const val EXTRA_DOSE_ID = "dose_id"

    fun rescheduleAll(context: Context) {
        val repo = MedRepository.get(context)
        repo.getUpcomingPendingDoses(System.currentTimeMillis(), MAX_SCHEDULED)
            .forEach { schedule(context, it.id, it.scheduledAt) }
    }

    fun schedule(context: Context, doseId: Long, triggerAt: Long) {
        val am = context.getSystemService(AlarmManager::class.java)
        val operation = alarmIntent(context, doseId)
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (canExact) {
            // setAlarmClock: alarme exato que dispara mesmo em modo soneca (Doze)
            // e aparece como alarme no relógio do sistema.
            val showIntent = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, showIntent), operation)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
        }
    }

    fun snooze(context: Context, doseId: Long, minutes: Int = 10) {
        schedule(context, doseId, System.currentTimeMillis() + minutes * 60_000L)
    }

    fun cancel(context: Context, doseId: Long) {
        context.getSystemService(AlarmManager::class.java).cancel(alarmIntent(context, doseId))
    }

    private fun alarmIntent(context: Context, doseId: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            doseId.toInt(),
            Intent(context, AlarmReceiver::class.java).putExtra(EXTRA_DOSE_ID, doseId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
