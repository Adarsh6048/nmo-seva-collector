package org.nmo.seva.collector

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class PaymentDbHelper(context: Context) : SQLiteOpenHelper(context, "nmo_payments.db", null, 2) {
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
                direction TEXT NOT NULL DEFAULT 'INCOMING',
                donation_status TEXT NOT NULL DEFAULT 'PENDING',
                synced INTEGER NOT NULL DEFAULT 0,
                reconciled INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE payments ADD COLUMN direction TEXT NOT NULL DEFAULT 'INCOMING'")
            db.execSQL("ALTER TABLE payments ADD COLUMN donation_status TEXT NOT NULL DEFAULT 'PENDING'")
        }
    }

    fun insertIfNew(payment: Payment): Boolean {
        val values = ContentValues().apply {
            put("event_id", payment.eventId)
            put("amount", payment.amount)
            put("donor_name", payment.donorName)
            put("sender_hint", payment.senderHint)
            put("transaction_ref", payment.transactionRef)
            put("received_at", payment.receivedAt)
            put("source_app", payment.sourceApp)
            put("direction", payment.direction.name)
            put("donation_status", payment.donationStatus.name)
            put("synced", if (payment.synced) 1 else 0)
            put("reconciled", if (payment.reconciled) 1 else 0)
        }
        return writableDatabase.insertWithOnConflict("payments", null, values, SQLiteDatabase.CONFLICT_IGNORE) != -1L
    }

    fun updateClassification(eventId: String, status: DonationStatus, donorName: String? = null) {
        val values = ContentValues().apply {
            put("donation_status", status.name)
            if (status == DonationStatus.DONATION) put("donor_name", donorName?.trim())
            else putNull("donor_name")
            put("synced", 0)
        }
        writableDatabase.update("payments", values, "event_id = ?", arrayOf(eventId))
    }

    fun updateDonorName(eventId: String, donorName: String) {
        val values = ContentValues().apply {
            put("donor_name", donorName.trim())
            put("donation_status", DonationStatus.DONATION.name)
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

    fun getRecent(limit: Int = 30): List<Payment> = readableDatabase.rawQuery(
        "SELECT * FROM payments ORDER BY received_at DESC LIMIT ?", arrayOf(limit.toString())
    ).use { c -> buildList { while (c.moveToNext()) add(read(c)) } }

    fun getPendingClassification(limit: Int = 20): List<Payment> = readableDatabase.rawQuery(
        "SELECT * FROM payments WHERE direction = 'INCOMING' AND donation_status = 'PENDING' ORDER BY received_at DESC LIMIT ?",
        arrayOf(limit.toString())
    ).use { c -> buildList { while (c.moveToNext()) add(read(c)) } }

    fun donationTotal(): Double = readableDatabase.rawQuery(
        "SELECT COALESCE(SUM(amount),0) FROM payments WHERE direction = 'INCOMING' AND donation_status = 'DONATION'", null
    ).use { c -> if (c.moveToFirst()) c.getDouble(0) else 0.0 }

    fun transactionCount(): Int = readableDatabase.rawQuery(
        "SELECT COUNT(*) FROM payments", null
    ).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }

    fun incomingCount(): Int = readableDatabase.rawQuery(
        "SELECT COUNT(*) FROM payments WHERE direction = 'INCOMING'", null
    ).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }

    private fun read(c: android.database.Cursor) = Payment(
        eventId = c.getString(c.getColumnIndexOrThrow("event_id")),
        amount = c.getDouble(c.getColumnIndexOrThrow("amount")),
        donorName = c.getString(c.getColumnIndexOrThrow("donor_name")),
        senderHint = c.getString(c.getColumnIndexOrThrow("sender_hint")),
        transactionRef = c.getString(c.getColumnIndexOrThrow("transaction_ref")),
        receivedAt = c.getLong(c.getColumnIndexOrThrow("received_at")),
        sourceApp = c.getString(c.getColumnIndexOrThrow("source_app")),
        direction = runCatching { TransactionDirection.valueOf(c.getString(c.getColumnIndexOrThrow("direction"))) }.getOrDefault(TransactionDirection.INCOMING),
        donationStatus = runCatching { DonationStatus.valueOf(c.getString(c.getColumnIndexOrThrow("donation_status"))) }.getOrDefault(DonationStatus.PENDING),
        synced = c.getInt(c.getColumnIndexOrThrow("synced")) == 1,
        reconciled = c.getInt(c.getColumnIndexOrThrow("reconciled")) == 1
    )
}
