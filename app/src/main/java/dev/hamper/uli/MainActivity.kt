package dev.hamper.uli

import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.hamper.uli.data.FileProgressStore
import dev.hamper.uli.data.FileSessionStore
import dev.hamper.uli.ui.theme.UliTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels {
        viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                AppViewModel(
                    questionsJson = app.assets.open("questions.json").use { it.readBytes().decodeToString() },
                    store = FileProgressStore(app.filesDir),
                    sessionStore = FileSessionStore(app.filesDir),
                    host = AndroidHost(app, createSavedStateHandle()),
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) applyShot(intent)
        setContent {
            UliTheme {
                UliApp(vm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        applyShot(intent)
    }

    private fun applyShot(intent: Intent?) {
        if (!BuildConfig.DEBUG) return
        intent?.getStringExtra("shot")?.let(vm::applyShot)
    }
}

private class AndroidHost(private val app: Application, private val handle: SavedStateHandle) : Host {
    @OptIn(ExperimentalCoroutinesApi::class)
    override val io = Dispatchers.IO.limitedParallelism(1)
    override fun now() = System.currentTimeMillis()
    override fun uptime() = SystemClock.elapsedRealtime()
    override var savedScreen: String?
        get() = handle["screen"]
        set(v) { handle["screen"] = v }

    override fun log(message: String, error: Throwable) {
        Log.e("Uli", message, error)
    }

    override fun saveFailed() = app.mainExecutor.execute {
        Toast.makeText(app, "Postup sa nepodarilo uložiť. Skontroluj voľné miesto v telefóne.", Toast.LENGTH_LONG).show()
    }
}
