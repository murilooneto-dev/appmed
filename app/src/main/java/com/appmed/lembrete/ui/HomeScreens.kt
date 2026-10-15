package com.appmed.lembrete.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.appmed.lembrete.data.MedicationSummary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    warnings: @Composable () -> Unit,
    onAdd: () -> Unit,
    onOpen: (Long) -> Unit,
    onHistory: () -> Unit,
) {
    val now = rememberNow()
    val summaries by rememberRepoQuery { it.getSummaries() }
    val active = summaries?.filter { !it.medication.isFinished(now) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Meus medicamentos") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                actions = {
                    TextButton(onClick = onHistory) {
                        Icon(
                            Icons.AutoMirrored.Filled.List, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                        Text(
                            "  Histórico", color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Novo medicamento") },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { warnings() }
            when {
                active == null -> item { Loading() }
                active.isEmpty() -> item {
                    EmptyMessage(
                        "Nenhum tratamento em andamento.\n\n" +
                            "Toque em \"Novo medicamento\" para cadastrar um remédio e " +
                            "criar os alarmes automaticamente."
                    )
                }
                else -> items(active, key = { it.medication.id }) { summary ->
                    MedicationCard(summary, now, onClick = { onOpen(summary.medication.id) })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onBack: () -> Unit, onOpen: (Long) -> Unit) {
    val now = rememberNow()
    val summaries by rememberRepoQuery { it.getSummaries() }
    val finished = summaries
        ?.filter { it.medication.isFinished(now) }
        ?.sortedByDescending { it.medication.endsAt }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Histórico") },
                navigationIcon = { BackButton(onBack) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                finished == null -> item { Loading() }
                finished.isEmpty() -> item {
                    EmptyMessage(
                        "Nenhum tratamento concluído ainda.\n\n" +
                            "Ao fim de cada tratamento, o registro de todas as doses " +
                            "confirmadas (dia e hora) aparece aqui."
                    )
                }
                else -> items(finished, key = { it.medication.id }) { summary ->
                    MedicationCard(summary, now, onClick = { onOpen(summary.medication.id) })
                }
            }
        }
    }
}

@Composable
private fun MedicationCard(summary: MedicationSummary, now: Long, onClick: () -> Unit) {
    val med = summary.medication
    val finished = med.isFinished(now)
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (finished) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(med.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("${med.dosage} • ${describeFrequency(med.intervalHours)}")
            Spacer(Modifier.height(8.dp))
            if (finished) {
                Text("Tratamento de ${formatDate(med.firstDoseAt)} a ${formatDate(med.endsAt)}")
            } else {
                val next = summary.nextDoseAt
                Text(
                    if (next != null) "Próxima dose: ${formatRelative(next)}"
                    else "Sem doses futuras",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "Termina em ${formatDateTime(med.endsAt)}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.height(8.dp))
            val progress = if (summary.totalDoses == 0) 0f
            else summary.takenDoses.toFloat() / summary.totalDoses
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            Text(
                "${summary.takenDoses} de ${summary.totalDoses} doses confirmadas",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
    }
}

@Composable
fun Loading() {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
fun EmptyMessage(text: String) {
    Text(
        text,
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 16.dp),
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
