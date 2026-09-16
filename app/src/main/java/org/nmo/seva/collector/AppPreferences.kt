package org.nmo.seva.collector

import android.content.Context

class AppPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("nmo_prefs", Context.MODE_PRIVATE)

    var collectorName: String
        get() = prefs.getString("collector_name", "") ?: ""
        set(value) = prefs.edit().putString("collector_name", value.trim()).apply()

    var collectorCode: String
        get() = prefs.getString("collector_code", "") ?: ""
        set(value) = prefs.edit().putString("collector_code", value.trim().uppercase()).apply()

    var collectorToken: String
        get() = prefs.getString("collector_token", "") ?: ""
        set(value) = prefs.edit().putString("collector_token", value.trim()).apply()

    var backendUrl: String
        get() = prefs.getString("backend_url", "") ?: ""
        set(value) = prefs.edit().putString("backend_url", value.trim()).apply()

    fun isConfigured(): Boolean = collectorCode.isNotBlank() && collectorToken.isNotBlank() && backendUrl.startsWith("https://")
}
