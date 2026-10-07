package com.example

import android.app.Application
import com.example.data.SanaDatabase
import com.example.data.SanaPreferences

class SanaApplication : Application() {

    lateinit var database: SanaDatabase
        private set

    lateinit var preferences: SanaPreferences
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = SanaDatabase.getInstance(this)
        preferences = SanaPreferences(this)
    }

    companion object {
        lateinit var instance: SanaApplication
            private set
    }
}
