package org.nmo.seva.collector

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

class NmoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
    }
}
