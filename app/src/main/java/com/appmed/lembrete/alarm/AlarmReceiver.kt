package com.appmed.lembrete.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.appmed.lembrete.data.MedRepository

/** Disparado pelo AlarmManager no horário de uma dose. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val doseId = intent.getLongExtra(AlarmScheduler.EXTRA_DOSE_ID, -1)
        val item = MedRepository.get(context).getDose(doseId)
        if (item != null && !item.dose.isTaken) {
            Notifications.showDoseAlarm(context, item)
        }
        AlarmScheduler.rescheduleAll(context)
    }
}
