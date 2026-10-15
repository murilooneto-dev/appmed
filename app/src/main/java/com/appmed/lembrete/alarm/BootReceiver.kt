package com.appmed.lembrete.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Alarmes do AlarmManager são perdidos ao reiniciar o aparelho; reagenda todos. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AlarmScheduler.rescheduleAll(context)
    }
}
