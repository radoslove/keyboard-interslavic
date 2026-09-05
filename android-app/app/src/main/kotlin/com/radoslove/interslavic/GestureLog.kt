package com.radoslove.interslavic

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL

/**
 * Records real glides — the path, what the decoder offered, and what the user
 * turned out to want — and sends them to medzuucenje.
 *
 * ## Why this exists
 *
 * The decoder is tuned in `tools/swipe_eval.py` against SYNTHETIC finger paths,
 * and on 2026-09-03 that model was caught lying. It insisted `pisati` beat
 * `prati` by 15-25 points on every simulated attempt; the phone produced `prati`
 * ten times in a row. Both cannot be right, and the phone is never wrong about
 * what the finger did. A model of a gesture is not a gesture.
 *
 * Two words whose routes differ only in WHERE the finger slowed are exactly the
 * case a hand-written simulator cannot fake, because the simulator's idea of
 * where a finger slows is the very assumption under test. So we stop guessing
 * and keep the real ones.
 *
 * ## The label is the valuable part
 *
 * A path on its own teaches nothing; a path plus "what was actually wanted" is
 * training data. The outcome is only known AFTER the commit — the user corrects
 * it, deletes it, or moves on — so a gesture is held back until its fate is
 * settled, and each one is sent exactly once, already labelled.
 *
 * ## Guards
 *
 * Same two as [CrashReporter], for the same reason: this ships only in TEST
 * builds (`.debug` package id) and INTERNET lives only in the debug manifest,
 * so the released keyboard physically cannot send a keystroke anywhere. For a
 * keyboard that is a load-bearing promise, not a detail.
 */
object GestureLog {

    /** Sinks, tried in order until one answers.
     *
     *  `ubu` used to be first here and went offline mid-session, taking the log
     *  with it - a server in a flat is not a thing telemetry can depend on. A
     *  sample lost because a machine was asleep has to be reproduced by hand, so
     *  the datacentre box leads and the laptop stands in. Both are tailnet
     *  addresses; nothing here leaves the fleet. */
    private val SINKS = listOf(
        "http://100.91.132.98:30025/api/gesture",   // hetz - a datacentre box, always on
        "http://100.79.220.17:30025/api/gesture",   // mc - stands in while the laptop is awake
    )

    private var enabled = false
    private var device = ""

    /** Held until we know how it ended. */
    private class Pending(
        val trail: String,
        val keys: String,
        val candidates: String,
        val committed: String,
    )

    private var pending: Pending? = null

    fun install(context: Context) {
        enabled = context.packageName.endsWith(".debug")
        device = android.os.Build.MODEL ?: ""
    }

    /**
     * A glide was just committed. Any previous one that nothing happened to was
     * evidently fine, so it is flushed as `kept`.
     */
    fun committed(
        rawPts: List<Float>,
        centers: Map<Char, FloatArray>,
        candidates: List<String>,
        committed: String,
    ) {
        if (!enabled) return
        flush("kept", null)
        val trail = StringBuilder("[")
        var i = 0
        while (i + 2 < rawPts.size) {
            if (i > 0) trail.append(',')
            trail.append('[').append(rawPts[i].toInt()).append(',')
                .append(rawPts[i + 1].toInt()).append(',')
                .append(rawPts[i + 2].toInt()).append(']')
            i += 3
        }
        trail.append(']')
        val keys = StringBuilder("{")
        var first = true
        for ((ch, c) in centers) {
            if (!first) keys.append(',')
            first = false
            keys.append('"').append(ch).append("\":[")
                .append(c[0].toInt()).append(',').append(c[1].toInt()).append(']')
        }
        keys.append('}')
        val cands = candidates.joinToString(",", "[", "]") { quote(it) }
        pending = Pending(trail.toString(), keys.toString(), cands, committed)
    }

    /** The user said what they actually wanted, or threw the guess away. */
    fun resolve(outcome: String, chosen: String?) {
        if (!enabled) return
        flush(outcome, chosen)
    }

    private fun flush(outcome: String, chosen: String?) {
        val p = pending ?: return
        pending = null
        val payload = """{"device":${quote(device)},"trail":${p.trail},"keys":${p.keys},""" +
            """"candidates":${p.candidates},"committed":${quote(p.committed)},""" +
            """"chosen":${if (chosen == null) "null" else quote(chosen)},""" +
            """"outcome":${quote(outcome)}}"""
        // Off the main thread and never joined: unlike a crash report there is
        // no deadline here, and typing must not wait on the network.
        Thread {
            for (sink in SINKS) {
                try {
                    val cx = (URL(sink).openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        connectTimeout = 1500
                        readTimeout = 1500
                        doOutput = true
                        setRequestProperty("Content-Type", "application/json")
                    }
                    cx.outputStream.use { it.write(payload.toByteArray()) }
                    cx.inputStream.use { it.readBytes() }
                    cx.disconnect()
                    return@Thread
                } catch (_: Throwable) {
                    // Try the next one. A missing sink must never disturb typing:
                    // losing a sample is cheap, a keyboard that stutters because
                    // a server is asleep is not.
                }
            }
        }.apply { isDaemon = true }.start()
    }

    private fun quote(s: String): String {
        val out = StringBuilder("\"")
        for (c in s) when (c) {
            '"' -> out.append("\\\"")
            '\\' -> out.append("\\\\")
            '\n' -> out.append("\\n")
            else -> if (c < ' ') out.append("\\u%04x".format(c.code)) else out.append(c)
        }
        return out.append('"').toString()
    }
}
