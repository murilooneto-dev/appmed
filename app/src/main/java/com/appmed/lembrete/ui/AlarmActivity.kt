package com.appmed.lembrete.ui

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appmed.lembrete.alarm.AlarmScheduler
import com.appmed.lembrete.alarm.DoseActions
import com.appmed.lembrete.data.MedRepository

/** Tela cheia exibida no horário da dose, inclusive com o celular bloqueado. */
class AlarmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val doseId = intent.getLongExtra(AlarmScheduler.EXTRA_DOSE_ID, -1)
        val item = MedRepository.get(this).getDose(doseId)
        if (item == null || item.dose.isTaken) {
            finish()
            return
        }

        setContent {
            AppMedTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text("Hora do remédio", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            formatTime(item.dose.scheduledAt),
                            fontSize = 64.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            item.medication.name,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            "Tomar ${item.medication.dosage}",
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(48.dp))
                        Button(
                            onClick = {
                                DoseActions.confirm(this@AlarmActivity, doseId)
                                finish()
                            },
                            modifier = Modifier.fillMaxWidth().height(64.dp),
                        ) { Text("Tomei", fontSize = 22.sp) }
                        Spacer(Modifier.height(16.dp))
                        OutlinedButton(
                            onClick = {
                                DoseActions.snooze(this@AlarmActivity, doseId)
                                finish()
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                        ) { Text("Adiar 10 minutos", fontSize = 18.sp) }
                    }
                }
            }
        }
    }
}
