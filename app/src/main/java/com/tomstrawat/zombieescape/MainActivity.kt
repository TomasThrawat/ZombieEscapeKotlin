package com.tomstrawat.zombieescape

import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.TextView
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

class MainActivity : Activity() {

    private val crashReportFile = "zombie_escape_crash.txt"

    override fun onCreate(savedInstanceState: Bundle?) {
        installCrashHandler()
        super.onCreate(savedInstanceState)

        try {
            hideSystemUi()
            setContentView(ZombieEscapeView(this))
        } catch (throwable: Throwable) {
            showCrashScreen("Startup crash", throwable)
        }

        showPreviousCrash()
    }

    private fun installCrashHandler() {
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                File(filesDir, crashReportFile).writeText(formatCrash("Uncaught crash", throwable))
            } catch (_: Throwable) {
            }
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun showPreviousCrash() {
        val file = File(filesDir, crashReportFile)
        if (!file.exists()) return

        val report = try {
            file.readText()
        } catch (_: Throwable) {
            ""
        }
        file.delete()

        if (report.isNotBlank()) {
            showCrashScreen("Last crash report", report)
        }
    }

    private fun showCrashScreen(title: String, throwable: Throwable) {
        showCrashScreen(title, formatCrash(title, throwable))
    }

    private fun showCrashScreen(title: String, report: String) {
        val textView = TextView(this).apply {
            setBackgroundColor(Color.BLACK)
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(32, 32, 32, 32)
            text = title + "\n\n" + report
            movementMethod = ScrollingMovementMethod()
            isVerticalScrollBarEnabled = true
        }
        setContentView(textView)
    }

    private fun formatCrash(title: String, throwable: Throwable): String {
        val writer = StringWriter()
        throwable.printStackTrace(PrintWriter(writer))
        return title + "\n" + writer.toString()
    }

    private fun hideSystemUi() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUi()
    }
}
