package it.vincenzo.tvpilot.core

import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object Http {
    private fun open(url: String, timeoutMs: Int = 15_000): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = timeoutMs
            readTimeout = 30_000
            setRequestProperty("User-Agent", "TVPilot-Android")
        }

    fun get(url: String, timeoutMs: Int = 15_000): String = String(bytes(url, timeoutMs))

    fun bytes(url: String, timeoutMs: Int = 15_000): ByteArray {
        val c = open(url, timeoutMs)
        try {
            if (c.responseCode !in 200..299) error("HTTP ${c.responseCode} su $url")
            return c.inputStream.use { it.readBytes() }
        } finally { c.disconnect() }
    }

    fun post(url: String, body: String, contentType: String = "application/json"): Pair<Int, String> {
        val c = open(url)
        try {
            c.requestMethod = "POST"
            c.doOutput = true
            c.setRequestProperty("Content-Type", contentType)
            c.outputStream.use { it.write(body.toByteArray()) }
            val code = c.responseCode
            val text = runCatching { (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } }
                .getOrNull().orEmpty()
            return code to text
        } finally { c.disconnect() }
    }

    fun download(url: String, dest: File) {
        val c = open(url)
        try {
            if (c.responseCode !in 200..299) error("HTTP ${c.responseCode} su $url")
            val tmp = File(dest.path + ".part")
            c.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            if (!tmp.renameTo(dest)) error("Impossibile salvare ${dest.name}")
        } finally { c.disconnect() }
    }
}
