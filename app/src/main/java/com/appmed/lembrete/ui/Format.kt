package com.appmed.lembrete.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ptBR: Locale = Locale.forLanguageTag("pt-BR")
private val timeFmt = DateTimeFormatter.ofPattern("HH:mm", ptBR)
private val dateFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy", ptBR)
private val shortDateFmt = DateTimeFormatter.ofPattern("dd/MM", ptBR)
private val dayHeaderFmt = DateTimeFormatter.ofPattern("EEEE, dd/MM/yyyy", ptBR)

private fun Long.toZoned() = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault())

fun Long.toLocalDate(): LocalDate = toZoned().toLocalDate()

fun formatTime(ms: Long): String = timeFmt.format(ms.toZoned())

fun formatDate(ms: Long): String = dateFmt.format(ms.toZoned())

fun formatDateTime(ms: Long): String = "${formatDate(ms)} às ${formatTime(ms)}"

/** "hoje às 14:00", "amanhã às 08:00" ou "17/10 às 20:00". */
fun formatRelative(ms: Long): String {
    val today = LocalDate.now()
    val day = ms.toLocalDate()
    val prefix = when (day) {
        today -> "hoje"
        today.plusDays(1) -> "amanhã"
        today.minusDays(1) -> "ontem"
        else -> shortDateFmt.format(ms.toZoned())
    }
    return "$prefix às ${formatTime(ms)}"
}

fun formatDayHeader(day: LocalDate): String {
    val base = dayHeaderFmt.format(day).replaceFirstChar { it.titlecase(ptBR) }
    val today = LocalDate.now()
    return when (day) {
        today -> "Hoje — $base"
        today.plusDays(1) -> "Amanhã — $base"
        else -> base
    }
}

fun describeFrequency(intervalHours: Int): String {
    val perDay = if (24 % intervalHours == 0) " (${24 / intervalHours}x ao dia)" else ""
    return if (intervalHours == 1) "A cada 1 hora" else "A cada $intervalHours horas$perDay"
}
