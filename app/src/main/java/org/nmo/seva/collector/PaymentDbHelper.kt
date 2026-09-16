package org.nmo.seva.collector

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class PaymentDbHelper(context: Context) : SQLiteOpenHelper(context, "nmo_payments.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE payments (
                event_id TEXT PRIMARY KEY,
                amount REAL NOT NULL,
                donor_name TEXT,
                sender_hint TEXT,
                transaction_ref TEXT,
                received_at INTEGER NOT NULL,
                source_app TEXT NOT NULL,
                synced INTEGER NOT NULL DEFAULT 0,
                reconciled INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun insertIfNew(payment: Payment): Boolean {
        val values = ContentValues().apply {
            put("event_id", payment.eventId)
            put("amount", payment.amount)
            put("donor_name", payment.donorName)
            put("sender_hint", payment.senderHint)
            put("transaction_ref", payment.transactionRef)
            put("received_at", payment.receivedAt)
            put("source_app", payment.sourceApp)
            put("synced", if (payment.synced) 1 else 0)
            put("reconciled", if (payment.reconciled) 1 else 0)
        }
        return writableDatabase.insertWithOnConflict("payments", null, values, SQLiteDatabase.CONFLICT_IGNORE) != -1L
    }

    fun updateDonorName(eventId: String, donorName: String) {
        val values = ContentValues().apply {
            put("donor_name", donorName.trim())
            put("synced", 0)
        }
        writableDatabase.update("payments", values, "event_id = ?", arrayOf(eventId))
    }

    fun setReconciled(eventId: String, reconciled: Boolean) {
        val values = ContentValues().apply {
            put("reconciled", if (reconciled) 1 else 0)
            put("synced", 0)
        }
        writableDatabase.update("payments", values, "event_id = ?", arrayOf(eventId))
    }

    fun markSynced(eventIds: List<String>) {
        if (eventIds.isEmpty()) return
        writableDatabase.beginTransaction()
        try {
            val values = ContentValues().apply { put("synced", 1) }
            eventIds.forEach { writableDatabase.update("payments", values, "event_id = ?", arrayOf(it)) }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    fun getPayment(eventId: String): Payment? = readableDatabase.rawQuery(
        "SELECT * FROM payments WHERE event_id = ?", arrayOf(eventId)
    ).use { c -> if (c.moveToFirst()) read(c) else null }

    fun getUnsynced(limit: Int = 100): List<Payment> = readableDatabase.rawQuery(
        "SELECT * FROM payments WHERE synced = 0 ORDER BY received_at ASC LIMIT ?", arrayOf(limit.toString())
    ).use { c -> buildList { while (c.moveToNext()) add(read(c)) } }

    fun getRecent(limit: Int = 20): List<Payment> = readableDatabase.rawQuery(
        "SELECT * FROM payments ORDER BY received_at DESC LIMIT ?", arrayOf(limit.toString())
    ).use { c -> buildList { while (c.moveToNext()) add(read(c)) } }

    fun getPendingNames(limit: Int = 20): List<Payment> = readableDatabase.rawQuery(
        "SELECT * FROM payments WHERE donor_name IS NULL OR TRIM(donor_name) = '' ORDER BY received_at DESC LIMIT ?",
        arrayOf(limit.toString())
    ).use { c -> buildList { while (c.moveToNext()) add(read(c)) } }

    fun totals(): Pair<Double, Int> = readableDatabase.rawQuery(
        "SELECT COALESCE(SUM(amount),0), COUNT(*) FROM payments", null
    ).use { c -> if (c.moveToFirst()) c.getDouble(0) to c.getInt(1) else 0.0 to 0 }

    private fun read(c: android.database.Cursor) = Payment(
        eventId = c.getString(c.getColumnIndexOrThrow("event_id")),
        amount = c.getDouble(c.getColumnIndexOrThrow("amount")),
        donorName = c.getString(c.getColumnIndexOrThrow("donor_name")),
        senderHint = c.getString(c.getColumnIndexOrThrow("sender_hint")),
        transactionRef = c.getString(c.getColumnIndexOrThrow("transaction_ref")),
        receivedAt = c.getLong(c.getColumnIndexOrThrow("received_at")),
        sourceApp = c.getString(c.getColumnIndexOrThrow("source_app")),
        synced = c.getInt(c.getColumnIndexOrThrow("synced")) == 1,
        reconciled = c.getInt(c.getColumnIndexOrThrow("reconciled")) == 1
    )
}
