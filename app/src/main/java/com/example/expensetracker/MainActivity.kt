package com.example.expensetracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.expensetracker.ui.theme.ExpenseTrackerTheme

private enum class Screen { SORT, DASH, SETTINGS, CATEGORIES, NICKNAMES, UNKNOWN }

class MainActivity : ComponentActivity() {
    override fun onResume() {
        super.onResume()
        // Catch up on anything the live listener missed (some phones block it while the app is closed)
        Thread {
            try {
                val r = SmsImporter.syncNew(applicationContext)
                if (r.added > 0) TxBus.tick.value++
            } catch (e: Exception) {
            }
        }.start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Categories.load(this)   // your edited categories
        Reminders.schedule(this) // makes sure the next reminder is set
        // Set when you tap a "new transaction" notification: that payment's bubble comes first
        val fromNotification = intent.getLongExtra("txnId", -1L).takeIf { it > 0 }
        setContent {
            ExpenseTrackerTheme {
                var screen by remember { mutableStateOf(Screen.SORT) }
                var startId by remember { mutableStateOf(fromNotification) }
                when (screen) {
                    // The app opens on the unsorted bubbles; the dashboard is one tap away
                    Screen.SORT -> BubbleScreen(startId) { screen = Screen.DASH }
                    Screen.DASH -> {
                        BackHandler { screen = Screen.SORT }
                        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                            Dashboard(
                                onSort = { id -> startId = id; screen = Screen.SORT },
                                onSettings = { screen = Screen.SETTINGS },
                                modifier = Modifier.padding(innerPadding)
                            )
                        }
                    }
                    Screen.SETTINGS -> {
                        BackHandler { screen = Screen.DASH }
                        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                            SettingsScreen(
                                onBack = { screen = Screen.DASH },
                                onCategories = { screen = Screen.CATEGORIES },
                                onNicknames = { screen = Screen.NICKNAMES },
                                onUnknown = { screen = Screen.UNKNOWN },
                                modifier = Modifier.padding(innerPadding)
                            )
                        }
                    }
                    Screen.UNKNOWN -> {
                        BackHandler { screen = Screen.SETTINGS }
                        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                            UnknownScreen(onBack = { screen = Screen.SETTINGS }, modifier = Modifier.padding(innerPadding))
                        }
                    }
                    Screen.CATEGORIES -> {
                        BackHandler { screen = Screen.SETTINGS }
                        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                            CategoriesScreen(onBack = { screen = Screen.SETTINGS }, modifier = Modifier.padding(innerPadding))
                        }
                    }
                    Screen.NICKNAMES -> {
                        BackHandler { screen = Screen.SETTINGS }
                        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                            NicknamesScreen(onBack = { screen = Screen.SETTINGS }, modifier = Modifier.padding(innerPadding))
                        }
                    }
                }
            }
        }
    }
}