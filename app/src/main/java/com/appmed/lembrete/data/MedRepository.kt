package com.appmed.lembrete.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private class MedDbHelper(context: Context) :
    SQLiteOpenHelper(context, "appmed.db", null, 1) {

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE medications (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                dosage TEXT NOT NULL,
                interval_hours INTEGER NOT NULL,
                duration_days INTEGER NOT NULL,
                first_dose_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE doses (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                medication_id INTEGER NOT NULL REFERENCES medications(id) ON DELETE CASCADE,
                scheduled_at INTEGER NOT NULL,
                taken_at INTEGER
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_doses_med ON doses(medication_id)")
        db.execSQL("CREATE INDEX idx_doses_time ON doses(scheduled_at)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
}

/** Acesso ao banco local. Todas as chamadas são síncronas; use fora da thread principal na UI. */
class MedRepository private constructor(context: Context) {

    private val helper = MedDbHelper(context.applicationContext)

    private val _changes = MutableStateFlow(0)

    /** Incrementa a cada alteração, para a UI recarregar os dados. */
    val changes: StateFlow<Int> = _changes

    private fun notifyChanged() {
        _changes.value = _changes.value + 1
    }

    /** Cadastra o medicamento e cria toda a rotina de doses. Retorna o id do medicamento. */
    fun addMedication(medication: Medication, now: Long = System.currentTimeMillis()): Long {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            val medId = db.insertOrThrow("medications", null, ContentValues().apply {
                put("name", medication.name)
                put("dosage", medication.dosage)
                put("interval_hours", medication.intervalHours)
                put("duration_days", medication.durationDays)
                put("first_dose_at", medication.firstDoseAt)
            })
            buildSchedule(medication).forEachIndexed { index, time ->
                db.insertOrThrow("doses", null, ContentValues().apply {
                    put("medication_id", medId)
                    put("scheduled_at", time)
                    // A primeira dose já foi tomada no horário informado pelo usuário.
                    if (index == 0 && time <= now) put("taken_at", time)
                })
            }
            db.setTransactionSuccessful()
            return medId
        } finally {
            db.endTransaction()
            notifyChanged()
        }
    }

    fun deleteMedication(id: Long) {
        helper.writableDatabase.delete("medications", "id = ?", arrayOf(id.toString()))
        notifyChanged()
    }

    fun markTaken(doseId: Long, at: Long = System.currentTimeMillis()) {
        helper.writableDatabase.update(
            "doses",
            ContentValues().apply { put("taken_at", at) },
            "id = ? AND taken_at IS NULL",
            arrayOf(doseId.toString()),
        )
        notifyChanged()
    }

    fun undoTaken(doseId: Long) {
        helper.writableDatabase.update(
            "doses",
            ContentValues().apply { putNull("taken_at") },
            "id = ?",
            arrayOf(doseId.toString()),
        )
        notifyChanged()
    }

    fun getMedication(id: Long): Medication? =
        helper.readableDatabase.rawQuery(
            "SELECT * FROM medications WHERE id = ?", arrayOf(id.toString())
        ).use { c -> if (c.moveToFirst()) c.toMedication() else null }

    fun getDoses(medicationId: Long): List<Dose> =
        helper.readableDatabase.rawQuery(
            "SELECT * FROM doses WHERE medication_id = ? ORDER BY scheduled_at",
            arrayOf(medicationId.toString()),
        ).use { c -> c.mapAll { it.toDose() } }

    fun getDose(doseId: Long): DoseWithMedication? {
        val dose = helper.readableDatabase.rawQuery(
            "SELECT * FROM doses WHERE id = ?", arrayOf(doseId.toString())
        ).use { c -> if (c.moveToFirst()) c.toDose() else null } ?: return null
        val med = getMedication(dose.medicationId) ?: return null
        return DoseWithMedication(dose, med)
    }

    /** Doses ainda não confirmadas com horário a partir de [from], em ordem cronológica. */
    fun getUpcomingPendingDoses(from: Long, limit: Int): List<Dose> =
        helper.readableDatabase.rawQuery(
            "SELECT * FROM doses WHERE taken_at IS NULL AND scheduled_at >= ? " +
                "ORDER BY scheduled_at LIMIT ?",
            arrayOf(from.toString(), limit.toString()),
        ).use { c -> c.mapAll { it.toDose() } }

    fun getSummaries(): List<MedicationSummary> {
        val now = System.currentTimeMillis()
        val meds = helper.readableDatabase.rawQuery(
            "SELECT * FROM medications ORDER BY first_dose_at DESC", null
        ).use { c -> c.mapAll { it.toMedication() } }
        return meds.map { med ->
            helper.readableDatabase.rawQuery(
                """
                SELECT COUNT(*),
                       SUM(CASE WHEN taken_at IS NOT NULL THEN 1 ELSE 0 END),
                       MIN(CASE WHEN taken_at IS NULL AND scheduled_at >= ? THEN scheduled_at END)
                FROM doses WHERE medication_id = ?
                """.trimIndent(),
                arrayOf(now.toString(), med.id.toString()),
            ).use { c ->
                c.moveToFirst()
                MedicationSummary(
                    medication = med,
                    totalDoses = c.getInt(0),
                    takenDoses = c.getInt(1),
                    nextDoseAt = if (c.isNull(2)) null else c.getLong(2),
                )
            }
        }
    }

    private fun Cursor.toMedication() = Medication(
        id = getLong(getColumnIndexOrThrow("id")),
        name = getString(getColumnIndexOrThrow("name")),
        dosage = getString(getColumnIndexOrThrow("dosage")),
        intervalHours = getInt(getColumnIndexOrThrow("interval_hours")),
        durationDays = getInt(getColumnIndexOrThrow("duration_days")),
        firstDoseAt = getLong(getColumnIndexOrThrow("first_dose_at")),
    )

    private fun Cursor.toDose(): Dose {
        val takenIdx = getColumnIndexOrThrow("taken_at")
        return Dose(
            id = getLong(getColumnIndexOrThrow("id")),
            medicationId = getLong(getColumnIndexOrThrow("medication_id")),
            scheduledAt = getLong(getColumnIndexOrThrow("scheduled_at")),
            takenAt = if (isNull(takenIdx)) null else getLong(takenIdx),
        )
    }

    private inline fun <T> Cursor.mapAll(transform: (Cursor) -> T): List<T> {
        val out = ArrayList<T>(count)
        while (moveToNext()) out += transform(this)
        return out
    }

    companion object {
        @Volatile
        private var instance: MedRepository? = null

        fun get(context: Context): MedRepository =
            instance ?: synchronized(this) {
                instance ?: MedRepository(context).also { instance = it }
            }
    }
}
