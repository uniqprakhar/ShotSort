package com.shotsort

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger

/**
 * Asks the model process for better titles, one event at a time. Always optional: the event already has
 * a rule-based title, and any failure (no model, crash, timeout, odd answer) simply leaves that title in place.
 * Everything here runs on the main thread.
 */
object TitleClient {
    private const val IDLE_UNBIND_MS = 20_000L
    private const val WATCHDOG_MS = 180_000L
    private const val MAX_FAILURES = 3

    private class Req(val proposalId: String, val ocrText: String, val prompt: String)

    private val main = Handler(Looper.getMainLooper())
    private val pending = LinkedHashMap<Long, Req>()
    private val unsent = ArrayDeque<Long>()
    private var nextKey = 1L
    private var service: Messenger? = null
    private var binding = false
    private lateinit var app: Context

    private val reply = Messenger(Handler(Looper.getMainLooper()) { msg ->
        if (msg.what == TitleService.MSG_REPLY) onReply(msg.data)
        true
    })

    private val idle = Runnable { if (pending.isEmpty()) unbind() }
    private val watchdog = Runnable {
        if (pending.isNotEmpty()) {
            Prefs.setModelNote(app, "The title model took too long and was stopped")
            pending.clear(); unsent.clear()
            unbind()
        }
    }

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(n: ComponentName?, b: IBinder?) {
            service = Messenger(b)
            binding = false
            flush()
        }
        override fun onServiceDisconnected(n: ComponentName?) {
            // The model process died. If it was in the middle of work, count it so a model that keeps
            // crashing gets switched off instead of retried forever.
            if (pending.isNotEmpty()) {
                val f = Prefs.titleFailures(app) + 1
                Prefs.setTitleFailures(app, f)
                Prefs.setModelNote(app, if (f >= MAX_FAILURES) "The title model keeps crashing on this phone, so it was switched off" else "The title model stopped unexpectedly")
                pending.clear(); unsent.clear()
            }
            service = null
            binding = false
        }
    }

    fun enabled(c: Context): Boolean = ModelStore.isReady(c) && Prefs.titleFailures(c) < MAX_FAILURES

    fun request(ctx: Context, proposalId: String, ocrText: String, prompt: String) {
        app = ctx.applicationContext
        if (!enabled(app)) return
        val key = nextKey++
        pending[key] = Req(proposalId, ocrText, prompt)
        unsent.addLast(key)
        main.removeCallbacks(idle)
        main.removeCallbacks(watchdog)
        main.postDelayed(watchdog, WATCHDOG_MS)
        if (service != null) {
            flush()
        } else if (!binding) {
            binding = true
            val ok = try {
                app.bindService(Intent(app, TitleService::class.java), conn, Context.BIND_AUTO_CREATE)
            } catch (_: Exception) { false }
            if (!ok) { binding = false; pending.clear(); unsent.clear() }
        }
    }

    private fun flush() {
        val s = service ?: return
        while (unsent.isNotEmpty()) {
            val key = unsent.removeFirst()
            val r = pending[key] ?: continue
            val m = Message.obtain(null, TitleService.MSG_REQUEST).apply {
                replyTo = reply
                data = Bundle().apply { putLong("key", key); putString("prompt", r.prompt) }
            }
            try { s.send(m) } catch (_: Exception) { pending.remove(key) }
        }
    }

    private fun onReply(d: Bundle) {
        val r = pending.remove(d.getLong("key")) ?: return
        val err = d.getString("error")
        if (err != null) {
            Prefs.setModelNote(app, "Title model error: $err")
        } else {
            val raw = d.getString("text") ?: ""
            val res = TitlePrompt.parse(raw, r.ocrText)
            if (res != null) {
                Proposals.update(app, r.proposalId, res.title, res.location)
                Prefs.setTitleFailures(app, 0)
                Prefs.setModelNote(app, "")
            } else {
                Prefs.setModelNote(app, "The model's last answer was not usable, so the built-in title was kept")
            }
        }
        main.removeCallbacks(watchdog)
        if (pending.isEmpty()) main.postDelayed(idle, IDLE_UNBIND_MS) else main.postDelayed(watchdog, WATCHDOG_MS)
    }

    private fun unbind() {
        main.removeCallbacks(idle)
        main.removeCallbacks(watchdog)
        if (service != null || binding) {
            try { app.unbindService(conn) } catch (_: Exception) {}
        }
        service = null
        binding = false
    }
}
