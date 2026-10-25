package com.shotsort

import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.os.Process
import org.nehuatl.llamacpp.LlamaAndroid

/**
 * Runs the small language model. It lives in its own process (see the manifest) so that a crash or
 * out-of-memory kill of the model can never take down the screenshot watcher. It is started when
 * there is work and is shut down, freeing the memory, once the app unbinds.
 */
class TitleService : Service() {
    companion object {
        const val MSG_REQUEST = 1
        const val MSG_REPLY = 2
    }

    private val thread = HandlerThread("shotsort-llm").apply { start() }
    private val messenger by lazy {
        Messenger(Handler(thread.looper) { msg -> if (msg.what == MSG_REQUEST) handle(msg); true })
    }
    private var llama: LlamaAndroid? = null
    private var ctxId = -1
    private val out = StringBuilder()

    override fun onBind(i: Intent?): IBinder = messenger.binder

    private fun handle(msg: Message) {
        val key = msg.data.getLong("key")
        val prompt = msg.data.getString("prompt") ?: ""
        val reply = Message.obtain(null, MSG_REPLY)
        val b = Bundle().apply { putLong("key", key) }
        try {
            b.putString("text", complete(prompt))
        } catch (e: Throwable) {
            b.putString("error", e.message ?: e.javaClass.simpleName)
        }
        reply.data = b
        try { msg.replyTo?.send(reply) } catch (_: Exception) {}
    }

    private fun ensureLoaded() {
        if (ctxId >= 0) return
        val file = ModelStore.file(this)
        if (!file.exists()) throw IllegalStateException("model file missing")
        val engine = LlamaAndroid(contentResolver)
        val uri = Uri.fromFile(file)
        val fd = contentResolver.openFileDescriptor(uri, "r")?.detachFd()
            ?: throw IllegalStateException("cannot open model file")
        val config = mapOf<String, Any>(
            "model" to uri.toString(),
            "model_fd" to fd,
            "use_mmap" to false,
            "use_mlock" to false,
            "n_ctx" to 2048,
            "embedding" to false,
            "n_batch" to 512,
            "n_threads" to 4,
            "n_gpu_layers" to 0,
            "vocab_only" to false,
            "lora" to "",
            "lora_scaled" to 1.0,
            "rope_freq_base" to 0.0,
            "rope_freq_scale" to 0.0
        )
        val res = engine.startEngine(config) { token -> out.append(token) }
            ?: throw IllegalStateException("model failed to load")
        ctxId = (res["contextId"] as Number).toInt()
        llama = engine
    }

    private fun complete(prompt: String): String {
        ensureLoaded()
        out.setLength(0)
        val res = llama!!.launchCompletion(
            ctxId,
            mapOf(
                "prompt" to prompt,
                "emit_partial_completion" to true,
                "n_predict" to 64,
                "temperature" to 0.1,
                "seed" to 1,
                "stop" to listOf("}", "<|im_end|>")
            )
        ) ?: throw IllegalStateException("generation failed")
        val text = out.toString().ifEmpty { (res["text"] as? String) ?: "" }
        return text
    }

    override fun onDestroy() {
        try { if (ctxId >= 0) llama?.releaseContext(ctxId) } catch (_: Throwable) {}
        thread.quitSafely()
        super.onDestroy()
        // Hand the memory back; this process only exists to host the model.
        Process.killProcess(Process.myPid())
    }
}
