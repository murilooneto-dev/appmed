package com.appmed.lembrete.data

data class Medication(
    val id: Long = 0,
    val name: String,
    /** Quantidade por dose, ex.: "1 comprimido", "10 gotas". */
    val dosage: String,
    val intervalHours: Int,
    val durationDays: Int,
    /** Momento (epoch ms) em que a primeira dose foi tomada. */
    val firstDoseAt: Long,
) {
    /** Fim do tratamento: primeira dose + duração em dias. */
    val endsAt: Long get() = firstDoseAt + durationDays * DAY_MS

    fun isFinished(now: Long = System.currentTimeMillis()) = now >= endsAt

    companion object {
        const val HOUR_MS = 60L * 60 * 1000
        const val DAY_MS = 24 * HOUR_MS
    }
}

data class Dose(
    val id: Long = 0,
    val medicationId: Long,
    val scheduledAt: Long,
    /** Momento em que o usuário confirmou a dose; null se ainda não confirmada. */
    val takenAt: Long? = null,
) {
    val isTaken: Boolean get() = takenAt != null
}

/** Dose acompanhada dos dados do medicamento, usada em alarmes e listas. */
data class DoseWithMedication(val dose: Dose, val medication: Medication)

data class MedicationSummary(
    val medication: Medication,
    val totalDoses: Int,
    val takenDoses: Int,
    val nextDoseAt: Long?,
)

/**
 * Gera todos os horários do tratamento: a cada [Medication.intervalHours] a partir
 * da primeira dose, até o fim do período de [Medication.durationDays] dias.
 */
fun buildSchedule(medication: Medication): List<Long> {
    val step = medication.intervalHours * Medication.HOUR_MS
    val times = mutableListOf<Long>()
    var t = medication.firstDoseAt
    while (t < medication.endsAt) {
        times += t
        t += step
    }
    return times
}
