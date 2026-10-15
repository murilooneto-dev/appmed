package com.appmed.lembrete.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.appmed.lembrete.alarm.AlarmScheduler
import com.appmed.lembrete.data.Medication
import com.appmed.lembrete.data.buildSchedule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

private val FREQUENCY_OPTIONS = listOf(4, 6, 8, 12, 24)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMedicationScreen(onBack: () -> Unit, onSaved: (Long) -> Unit) {
    val context = LocalContext.current
    val repo = rememberRepository()
    val scope = rememberCoroutineScope()

    var name by rememberSaveable { mutableStateOf("") }
    var dosage by rememberSaveable { mutableStateOf("") }
    var interval by rememberSaveable { mutableStateOf("8") }
    var duration by rememberSaveable { mutableStateOf("7") }
    var firstDose by rememberSaveable {
        mutableStateOf(
            Instant.now().truncatedTo(ChronoUnit.MINUTES).toEpochMilli()
        )
    }
    var showErrors by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    val intervalValue = interval.toIntOrNull()?.takeIf { it in 1..168 }
    val durationValue = duration.toIntOrNull()?.takeIf { it in 1..365 }
    val valid = name.isNotBlank() && dosage.isNotBlank() &&
        intervalValue != null && durationValue != null

    val preview = if (intervalValue != null && durationValue != null) {
        buildSchedule(
            Medication(
                name = name, dosage = dosage, intervalHours = intervalValue,
                durationDays = durationValue, firstDoseAt = firstDose,
            )
        )
    } else emptyList()

    val firstDoseLocal = LocalDateTime.ofInstant(Instant.ofEpochMilli(firstDose), ZoneId.systemDefault())
    fun updateFirstDose(dt: LocalDateTime) {
        firstDose = dt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Novo medicamento") },
                navigationIcon = { BackButton(onBack) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nome do medicamento") },
                placeholder = { Text("Ex.: Amoxicilina 500mg") },
                singleLine = true,
                isError = showErrors && name.isBlank(),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = dosage,
                onValueChange = { dosage = it },
                label = { Text("Quantidade por dose") },
                placeholder = { Text("Ex.: 1 comprimido, 10 gotas, 5 ml") },
                singleLine = true,
                isError = showErrors && dosage.isBlank(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Frequência", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FREQUENCY_OPTIONS.forEach { h ->
                    FilterChip(
                        selected = intervalValue == h,
                        onClick = { interval = h.toString() },
                        label = { Text("${h}h") },
                    )
                }
            }
            OutlinedTextField(
                value = interval,
                onValueChange = { interval = it.filter(Char::isDigit).take(3) },
                label = { Text("Tomar a cada (horas)") },
                supportingText = {
                    Text(
                        if (intervalValue != null) describeFrequency(intervalValue)
                        else "Informe um intervalo entre 1 e 168 horas"
                    )
                },
                isError = interval.isNotEmpty() && intervalValue == null || showErrors && intervalValue == null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number, imeAction = ImeAction.Next,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = duration,
                onValueChange = { duration = it.filter(Char::isDigit).take(3) },
                label = { Text("Duração do tratamento (dias)") },
                supportingText = {
                    if (durationValue == null) Text("Informe entre 1 e 365 dias")
                },
                isError = duration.isNotEmpty() && durationValue == null || showErrors && durationValue == null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number, imeAction = ImeAction.Done,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Quando tomou a primeira dose?", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        DatePickerDialog(
                            context,
                            { _, y, m, d ->
                                updateFirstDose(
                                    firstDoseLocal.withYear(y).withMonth(m + 1).withDayOfMonth(d)
                                )
                            },
                            firstDoseLocal.year, firstDoseLocal.monthValue - 1, firstDoseLocal.dayOfMonth,
                        ).show()
                    },
                ) { Text("Dia: ${formatDate(firstDose)}") }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        TimePickerDialog(
                            context,
                            { _, h, min -> updateFirstDose(firstDoseLocal.withHour(h).withMinute(min)) },
                            firstDoseLocal.hour, firstDoseLocal.minute, true,
                        ).show()
                    },
                ) { Text("Hora: ${formatTime(firstDose)}") }
            }

            if (preview.isNotEmpty()) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Resumo da rotina", fontWeight = FontWeight.Bold)
                        Text("${preview.size} doses no total, ${describeFrequency(intervalValue!!).lowercase()}.")
                        val nextOnes = preview.drop(1).take(3)
                        if (nextOnes.isNotEmpty()) {
                            Text("Próximos alarmes: " + nextOnes.joinToString { formatRelative(it) })
                        }
                        Text("Última dose: ${formatDateTime(preview.last())}")
                    }
                }
            }

            Button(
                enabled = !saving,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    if (!valid) {
                        showErrors = true
                        return@Button
                    }
                    saving = true
                    scope.launch {
                        val id = withContext(Dispatchers.IO) {
                            val newId = repo.addMedication(
                                Medication(
                                    name = name.trim(),
                                    dosage = dosage.trim(),
                                    intervalHours = intervalValue!!,
                                    durationDays = durationValue!!,
                                    firstDoseAt = firstDose,
                                )
                            )
                            AlarmScheduler.rescheduleAll(context)
                            newId
                        }
                        onSaved(id)
                    }
                },
            ) { Text("Salvar e criar alarmes") }
        }
    }
}
