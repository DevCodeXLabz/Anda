package com.example.anda.core.stability

import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

fun AppCompatActivity.safeLaunch(
    origin: String,
    block: suspend () -> Unit
): Job {
    val handler = CoroutineExceptionHandler { _, throwable ->
        CrashShield.recordRecoverableError(origin, throwable)
        Toast.makeText(this, "Falha em $origin", Toast.LENGTH_SHORT).show()
    }

    return lifecycleScope.launch(handler) {
        block()
    }
}

