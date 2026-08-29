package com.radoslove.interslavic

import android.content.Context
import java.io.PrintWriter
import java.io.StringWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Sends an uncaught exception to medzuucenje so it can be read on a laptop.
 *
 * An IME cannot be attached to a debugger, and with developer mode off there is
 * no adb either - so a crash shows only as "the keyboard switched itself off"
 * with no way to learn why. This closes that gap for TEST builds.
 *
 * Guarded twice, on purpose:
 *  - it installs only when the package id ends in `.debug`, so the published
 *    keyboard never runs this code path;
 *  - INTERNET permission exists only in the debug manifest, so the released
 *    build could not post anything even if the guard were wrong.
 *
 * The old handler is always called afterwards: reporting must not change how
 * the app dies, only leave a note behind.
 */
object CrashReporter {

    private const val SINK = "http://ubu:30025/api/crash"

    fun install(context: Context) {
        if (!context.packageName.endsWith(".debug")) return
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
                // On a SEPARATE thread and joined: Android forbids network on
                // the main thread, and the crashing thread usually IS the main
                // one - so posting inline threw NetworkOnMainThreadException,
                // which this very catch then swallowed. The report vanished
                // and the failure looked like the phone never tried.
                val sender = Thread { post(context.packageName, "thread=${thread.name}\n$trace") }
                sender.start()
                sender.join(2500)
            } catch (_: Throwable) {
                // Never let reporting mask the real crash.
            }
            previous?.uncaughtException(thread, error)
        }
    }

    private fun post(app: String, trace: String) {
        // Synchronous and short: the process is about to die, so there is no
        // later. Two seconds is enough on a tailnet and short enough not to
        // hang the death of the app if the sink is unreachable.
        val payload = """{"app":${quote(app)},"trace":${quote(trace)}}"""
        val cx = (URL(SINK).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 2000
            readTimeout = 2000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }
        cx.outputStream.use { it.write(payload.toByteArray()) }
        cx.inputStream.use { it.readBytes() }
        cx.disconnect()
    }

    private fun quote(s: String): String {
        val out = StringBuilder("\"")
        for (c in s) when (c) {
            '"' -> out.append("\\\"")
            '\\' -> out.append("\\\\")
            '\n' -> out.append("\\n")
            '\r' -> out.append("\\r")
            '\t' -> out.append("\\t")
            else -> if (c < ' ') out.append("\\u%04x".format(c.code)) else out.append(c)
        }
        return out.append('"').toString()
    }
}
