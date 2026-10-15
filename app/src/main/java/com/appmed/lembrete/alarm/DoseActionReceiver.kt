package com.appmed.lembrete.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.appmed.lembrete.data.MedRepository

/** Trata os botões "Tomei" e "Adiar" da notificação de alarme. */
class DoseActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val doseId = intent.getLongExtra(AlarmScheduler.EXTRA_DOSE_ID, -1)
        if (doseId < 0) return
        when (intent.action) {
            ACTION_TAKE -> DoseActions.confirm(context, doseId)
            ACTION_SNOOZE -> DoseActions.snooze(context, doseId)
        }
    }

    companion object {
        const val ACTION_TAKE = "com.appmed.lembrete.action.TAKE"
        const val ACTION_SNOOZE = "com.appmed.lembrete.action.SNOOZE"
    }
}

object DoseActions {
    fun confirm(context: Context, doseId: Long) {
        MedRepository.get(context).markTaken(doseId)
        AlarmScheduler.cancel(context, doseId)
        Notifications.cancel(context, doseId)
    }

    fun snooze(context: Context, doseId: Long, minutes: Int = 10) {
        Notifications.cancel(context, doseId)
        AlarmScheduler.snooze(context, doseId, minutes)
    }

    fun undo(context: Context, doseId: Long) {
        MedRepository.get(context).undoTaken(doseId)
        AlarmScheduler.rescheduleAll(context)
    }
}
