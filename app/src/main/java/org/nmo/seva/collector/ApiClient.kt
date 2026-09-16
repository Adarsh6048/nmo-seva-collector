package org.nmo.seva.collector

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object ApiClient {
    fun upload(url: String, collectorCode: String, token: String, payments: List<Payment>): Boolean {
        if (payments.isEmpty()) return true
        val events = JSONArray()
        payments.forEach { p ->
            events.put(JSONObject().apply {
                put("eventId", p.eventId)
                put("amount", p.amount)
                put("donorName", p.donorName ?: JSONObject.NULL)
                put("expenseNote", p.expenseNote ?: JSONObject.NULL)
                put("senderHint", p.senderHint ?: JSONObject.NULL)
                put("transactionRef", p.transactionRef ?: JSONObject.NULL)
                put("receivedAt", p.receivedAt)
                put("sourceApp", p.sourceApp)
                put("direction", p.direction.name)
                put("donationStatus", p.donationStatus.name)
                put("expenseStatus", p.expenseStatus.name)
                put("reconciled", p.reconciled)
            })
        }
        val body = JSONObject().apply {
            put("collectorCode", collectorCode)
            put("token", token)
            put("events", events)
        }.toString()

        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 15_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        return try {
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            code in 200..299 && runCatching { JSONObject(response).optBoolean("ok", false) }.getOrDefault(false)
        } finally {
            conn.disconnect()
        }
    }
}
