package com.example.di

import android.content.Context
import com.example.ads.AdMobManager
import com.example.data.local.MultiCalculatorDatabase
import com.example.data.network.ConnectivityMonitor
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.MultiCalculatorRepository
import com.example.domain.math.PrecisionMathEngine

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: MultiCalculatorDatabase by lazy {
        MultiCalculatorDatabase.getInstance(appContext)
    }

    val repository: MultiCalculatorRepository by lazy {
        MultiCalculatorRepository(
            historyDao = database.historyDao(),
            noteDao = database.noteDao()
        )
    }

    val preferencesRepository: UserPreferencesRepository by lazy {
        UserPreferencesRepository(appContext)
    }

    val mathEngine: PrecisionMathEngine by lazy {
        PrecisionMathEngine()
    }

    val connectivityMonitor: ConnectivityMonitor by lazy {
        ConnectivityMonitor(appContext)
    }

    val adMobManager: AdMobManager by lazy {
        AdMobManager(appContext)
    }
}
