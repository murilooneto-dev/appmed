package com.appmed.lembrete.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.appmed.lembrete.alarm.AlarmScheduler
import com.appmed.lembrete.alarm.DoseActions
import com.appmed.lembrete.alarm.Notifications
import com.appmed.lembrete.data.Dose
import com.appmed.lembrete.data.Medication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Permite confirmar uma dose com até 1 hora de antecedência. */
private const val EARLY_CONFIRM_MS = 60 * 60 * 1000L

private data class DetailData(val medication: Medication?, val doses: List<Dose>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(medicationId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = rememberRepository()
    val scope = rememberCoroutineScope()
    val now = rememberNow()
    val data by rememberRepoQuery(medicationId) {
        DetailData(it.getMedication(medicationId), it.getDoses(medicationId))
    }
    var confirmDelete by remember { mutableStateOf(false) }

    fun runIo(block: () -> Unit) {
        scope.launch { withContext(Dispatchers.IO) { block() } }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(data?.medication?.name ?: "") },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Excluir tratamento")
                    }
                },
            )
        },
    ) { padding ->
        val d = data
        if (d == null) {
            Loading()
            return@Scaffold
        }
        val med = d.medication
        if (med == null) {
            EmptyMessage("Tratamento não encontrado.")
            return@Scaffold
        }
        val finished = med.isFinished(now)
        val byDay = d.doses.groupBy { it.scheduledAt.toLocalDate() }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(med.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("Dose: ${med.dosage}")
                        Text(describeFrequency(med.intervalHours))
                        Text("Duração: ${med.durationDays} dia(s)")
                        Text("Início: ${formatDateTime(med.firstDoseAt)}")
                        Text("Fim: ${formatDateTime(med.endsAt)}")
                        val taken = d.doses.count { it.isTaken }
                        Text(
                            "$taken de ${d.doses.size} doses confirmadas" +
                                if (finished) " • Tratamento concluído" else "",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
            byDay.forEach { (day, doses) ->
                item(key = "day-$day") {
                    Text(
                        formatDayHeader(day),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                    )
                }
                items(doses, key = { it.id }) { dose ->
                    DoseRow(
                        dose = dose,
                        now = now,
                        finished = finished,
                        onConfirm = { runIo { DoseActions.confirm(context, dose.id) } },
                        onUndo = { runIo { DoseActions.undo(context, dose.id) } },
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Excluir tratamento?") },
            text = { Text("Todos os alarmes e o histórico deste medicamento serão apagados.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            repo.getDoses(medicationId).forEach {
                                AlarmScheduler.cancel(context, it.id)
                                Notifications.cancel(context, it.id)
                            }
                            repo.deleteMedication(medicationId)
                        }
                        onBack()
                    }
                }) { Text("Excluir") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") }
            },
        )
    }
}

@Composable
private fun DoseRow(
    dose: Dose,
    now: Long,
    finished: Boolean,
    onConfirm: () -> Unit,
    onUndo: () -> Unit,
) {
    val takenAt = dose.takenAt
    val (status, color) = when {
        takenAt != null -> "Confirmada em ${formatDateTime(takenAt)}" to Color(0xFF2E7D32)
        dose.scheduledAt < now -> "Não confirmada" to MaterialTheme.colorScheme.error
        else -> "Agendada (alarme ativo)" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            formatTime(dose.scheduledAt),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.width(72.dp),
        )
        Text(status, color = color, modifier = Modifier.weight(1f))
        when {
            takenAt != null && !finished ->
                TextButton(onClick = onUndo) { Text("Desfazer") }
            takenAt == null && !finished && dose.scheduledAt <= now + EARLY_CONFIRM_MS ->
                FilledTonalButton(onClick = onConfirm) { Text("Tomei") }
        }
    }
}
