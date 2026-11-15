package it.vincenzo.tvpilot.mirror

import it.vincenzo.tvpilot.core.Reports
import java.io.BufferedOutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * Piccolo server HTTP sul telefono che manda lo schermo come flusso MJPEG al browser della TV.
 * Gli indirizzi contengono un codice casuale: chi non lo conosce, anche sulla stessa rete, non vede lo schermo.
 */
class MjpegServer(port: Int, private val token: String) {
    @Volatile private var frame: ByteArray? = null
    @Volatile private var frameId = 0
    @Volatile private var closed = false
    private val lock = Object()
    private val server = ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(port)) }
    val clients = AtomicInteger(0)
    val port: Int get() = server.localPort

    fun publish(jpeg: ByteArray) {
        frame = jpeg
        synchronized(lock) { frameId++; lock.notifyAll() }
    }

    fun start() {
        thread(name = "mirror-accept", isDaemon = true) {
            while (!closed) {
                val c = runCatching { server.accept() }.getOrNull() ?: continue
                thread(name = "mirror-client", isDaemon = true) { handle(c) }
            }
        }
    }

    private fun handle(c: Socket) {
        c.use { s ->
            runCatching {
                s.tcpNoDelay = true
                val line = s.getInputStream().bufferedReader().readLine() ?: return
                val path = line.split(" ").getOrNull(1) ?: "/"
                val out = BufferedOutputStream(s.getOutputStream(), 64 * 1024)
                val base = "/$token"
                when {
                    path == "$base/stream" -> stream(out)
                    path.startsWith("$base/frame.jpg") -> single(out)
                    path == base || path == "$base/" -> html(out, base)
                    else -> {
                        out.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                        out.flush()
                    }
                }
            }
        }
    }

    /** Pagina a schermo intero; se il browser non regge l'MJPEG ripiega su un'immagine aggiornata di continuo */
    private fun html(out: BufferedOutputStream, base: String) {
        val page = """<!DOCTYPE html><html><head><meta charset="utf-8"><title>TVPilot</title>
<style>html,body{margin:0;height:100%;background:#000;overflow:hidden}
img{position:absolute;top:0;left:0;width:100%;height:100%;object-fit:contain}</style></head>
<body><img id="s" src="$base/stream">
<script>
var img=document.getElementById('s'),polling=false;
function poll(){polling=true;var n=new Image();n.onload=function(){img.src=n.src;setTimeout(poll,30)};
n.onerror=function(){setTimeout(poll,500)};n.src='$base/frame.jpg?t='+Date.now();}
img.onerror=function(){if(!polling)poll();};
</script></body></html>"""
        val body = page.toByteArray()
        out.write("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${body.size}\r\nCache-Control: no-cache\r\nConnection: close\r\n\r\n".toByteArray())
        out.write(body)
        out.flush()
    }

    private fun single(out: BufferedOutputStream) {
        val f = frame ?: ByteArray(0)
        out.write("HTTP/1.1 200 OK\r\nContent-Type: image/jpeg\r\nContent-Length: ${f.size}\r\nCache-Control: no-cache\r\nConnection: close\r\n\r\n".toByteArray())
        out.write(f)
        out.flush()
    }

    private fun stream(out: BufferedOutputStream) {
        clients.incrementAndGet()
        Reports.log("Mirroring: la TV si è collegata al flusso")
        try {
            out.write("HTTP/1.1 200 OK\r\nContent-Type: multipart/x-mixed-replace; boundary=tvpilotframe\r\nCache-Control: no-cache\r\nPragma: no-cache\r\nConnection: close\r\n\r\n".toByteArray())
            var sent = -1
            while (!closed) {
                synchronized(lock) { if (frameId == sent) lock.wait(1000) }
                val f = frame ?: continue
                val id = frameId
                if (id == sent) continue
                sent = id
                out.write("--tvpilotframe\r\nContent-Type: image/jpeg\r\nContent-Length: ${f.size}\r\n\r\n".toByteArray())
                out.write(f)
                out.write("\r\n".toByteArray())
                out.flush()
            }
        } finally {
            clients.decrementAndGet()
            Reports.log("Mirroring: la TV si è scollegata dal flusso")
        }
    }

    fun stop() {
        closed = true
        synchronized(lock) { lock.notifyAll() }
        runCatching { server.close() }
    }
}
