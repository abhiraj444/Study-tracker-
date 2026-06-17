package com.studyflow.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

class SessionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val subject = intent.getStringExtra("subject")
        val chapter = intent.getStringExtra("chapter")
        val mode = intent.getStringExtra("mode")
        val action = intent.getStringExtra("action")
        val breakDuration = intent.getStringExtra("breakDuration")

        when {
            action == "STOP" -> {
                // TODO: Stop session logic
                Toast.makeText(this, "Stopping session", Toast.LENGTH_SHORT).show()
            }
            breakDuration != null -> {
                // TODO: Start break logic
                Toast.makeText(this, "Taking break for $breakDuration", Toast.LENGTH_SHORT).show()
            }
            subject != null -> {
                // TODO: Start session logic
                Toast.makeText(this, "Starting study: $subject $chapter $mode", Toast.LENGTH_SHORT).show()
            }
            else -> {
                // Normal launch
            }
        }
    }
}
