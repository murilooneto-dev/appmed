package com.appmed.lembrete.alarm

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.appmed.lembrete.R
import com.appmed.lembrete.data.DoseWithMedication
import com.appmed.lembrete.ui.AlarmActivity
import com.appmed.lembrete.ui.formatTime

object Notifications {
    private const val CHANNEL_ALARM = "dose_alarm"

    fun createChannels(context: Context) {
        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val channel = NotificationChannel(
            CHANNEL_ALARM, "Alarmes de medicamentos", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Avisa no horário de tomar cada dose"
            setSound(
                sound,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 800, 400, 800, 400, 800)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun showDoseAlarm(context: Context, item: DoseWithMedication) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return
        createChannels(context)

        val doseId = item.dose.id
        val requestCode = doseId.toInt()
        val openAlarm = PendingIntent.getActivity(
            context, requestCode,
            Intent(context, AlarmActivity::class.java)
                .putExtra(AlarmScheduler.EXTRA_DOSE_ID, doseId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Hora do remédio: ${item.medication.name}")
            .setContentText("Tomar ${item.medication.dosage} — ${formatTime(item.dose.scheduledAt)}")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openAlarm)
            .setFullScreenIntent(openAlarm, true)
            .setAutoCancel(false)
            .setOngoing(true)
            .addAction(0, "Tomei", actionIntent(context, DoseActionReceiver.ACTION_TAKE, doseId))
            .addAction(0, "Adiar 10 min", actionIntent(context, DoseActionReceiver.ACTION_SNOOZE, doseId))
            .build()
        // Repete o som até o usuário interagir, como um despertador.
        notification.flags = notification.flags or Notification.FLAG_INSISTENT

        try {
            NotificationManagerCompat.from(context).notify(requestCode, notification)
        } catch (_: SecurityException) {
            // Permissão de notificação revogada.
        }
    }

    fun cancel(context: Context, doseId: Long) {
        NotificationManagerCompat.from(context).cancel(doseId.toInt())
    }

    private fun actionIntent(context: Context, action: String, doseId: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            doseId.toInt(),
            Intent(context, DoseActionReceiver::class.java)
                .setAction(action)
                .putExtra(AlarmScheduler.EXTRA_DOSE_ID, doseId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
