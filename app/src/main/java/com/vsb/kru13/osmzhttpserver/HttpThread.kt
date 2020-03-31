package com.vsb.kru13.osmzhttpserver

import android.os.*
import android.util.Log
import java.io.*
import java.net.InetAddress
import java.net.Socket
import java.net.URLDecoder
import java.nio.file.Files
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern


class HttpThread(private val socket: Socket, private val messenger: Messenger, private val semaphore: Semaphore, private var cameraServer: CameraServer) : Runnable {
    public companion object {
        public const val LOG_KEY = "LOG"
    }

    private var address: InetAddress? = null
    public override fun run() {
        val available = semaphore.tryAcquire()
        var close = true
        try {
            this.address = socket.inetAddress
            sendLogMessage("Client ${address} connected. Remaining free threads ${semaphore.availablePermits()}")
            val out = socket.getOutputStream()
            if (!available) {
                response503(out)
                sendLogMessage("Client ${address} was not served. Server is busy")
            } else {
                val bufferedReader = BufferedReader(InputStreamReader(socket.getInputStream()))

                val path: String? = processRequest(bufferedReader)
                if (path == null) {
                    socket.close()
                    return
                }
                sendLogMessage("Client ${address} Path ${path} requested")
                close = createResponseForPath(path, out, socket)
            }
            if (close) {
                out.flush()
                sendLogMessage("Client ${address} closed. Remaining free threads ${if (!available) 0 else semaphore.availablePermits() + 1}")
            }
        } finally {
            if (available) {
                semaphore.release()
            }
            if (close) {
                socket.close()
            }
        }
    }

    private fun createResponseForPath(path: String, out: OutputStream, socket: Socket): Boolean {
        val baseDir = Environment.getExternalStorageDirectory().absolutePath
        val pathFile = baseDir + path
        if (path.startsWith("/cgi-bin")) {
            this.responseFromCgi(path, out)
            sendLogMessage("Client ${address} executed command")
        } else if (path == "/camera/stream") {
            this.cameraServer.addSocket(out, socket);
            sendLogMessage("Client ${address} added to camera streaming")
            return false
        } else {
            val toOpen = File(pathFile)
            if (toOpen.exists()) {
                if (toOpen.isFile) {
                    responseFromFile(toOpen, out)
                } else {
                    val files = toOpen.listFiles()!!
                    val index = files.firstOrNull { it.nameWithoutExtension == "index" }
                    if (index != null) {
                        responseFromFile(index, out)
                    } else {
                        responseListing(files, out)
                    }
                }
            } else {
                response404(out)
            }
        }
        return true
    }

    private fun responseFromCgi(path: String, out: OutputStream) {
        var params = path.split("/").map { URLDecoder.decode(it, "UTF-8"); }.drop(2)
        val pb = ProcessBuilder(params)
        try {
            pb.redirectErrorStream(true)
            val process = pb.start()
            var body = ""
            process.inputStream.reader(Charsets.UTF_8).use {
                body += it.readText()
            }
            process.waitFor(10, TimeUnit.SECONDS)
            val response = createHeader(body.length.toLong(), 404) + body
            out.write(response.toByteArray())
            out.flush()
        } catch (ex : Exception) {
            val body = "Error in command: ${params.joinToString(",")} \n ${ex.localizedMessage}";
            sendLogMessage("Client ${address} error in cgi : " + body)
            val response = createHeader(body.length.toLong(), 500) + body
            out.write(response.toByteArray())
            out.flush()
        }
    }

    private fun processRequest(bufferedReader: BufferedReader): String? {
        var line = bufferedReader.readLine()
        if (line == "" || line == null) {
            return null
        }
        val pattern = Pattern.compile("GET (.+) HTTP.*")
        val matcher = pattern.matcher(line)

        var path: String? = "/"
        while (matcher.find()) {
            path = matcher.group(1)
        }
        Log.d("SERVER", "Path: " + path!!)

        while (line != null && line != "") {
            Log.d("SERVER", line)
            line = bufferedReader.readLine()
        }
        return path
    }

    private fun responseListing(files: Array<File>, out: OutputStream) {
        val href = { file: File -> "./${file.name}${if (file.isDirectory) "/" else ""}" }
        val name = { file: File -> "${if (file.isDirectory) "Dir:" else "File:"} ${file.name}" }

        val items = files.map {
            "<li><a href = ${href(it)}>" + name(it) + "<span style=\"margin-left:15%\">${Date(it.lastModified())}</span>" +
                    "</a></li>"
        }.joinToString("\n")

        val list = "<li><a href=\"..\">Dir: ..</a></li> $items"
        val body = "<html>\n" +
                "<body>\n" +
                "<h1>Listing</h1>\n" +
                "<ul>$list</ul>\n" +
                "</body>\n" +
                "</html>"
        val response = createHeader(body.length.toLong(), 404) + body
        sendLogMessage("To ${address.toString()}: Sended ${response.toByteArray().size} bytes")
        out.write(response.toByteArray())
    }

    private fun sendLogMessage(response: String) {
        val currentTime: Date = Calendar.getInstance().time
        val msg = Message.obtain()
        val bundle = Bundle()
        val formatter = SimpleDateFormat("HH:mm:ss")
        var formattedDate = formatter.format(currentTime)
        bundle.putString(LOG_KEY, "$formattedDate: $response")
        msg.data = bundle
        messenger.send(msg)
    }

    private fun response503(out: OutputStream) {
        val body = "<html>\n" +
                "<body>\n" +
                "<h1>Server too busy!</h1>\n" +
                "</body>\n" +
                "</html>"
        val response = createHeader(body.length.toLong(), 503) + body
        sendLogMessage("To ${address.toString()}: Sended ${response.toByteArray().size} bytes")
        out.write(response.toByteArray())
    }

    private fun response404(out: OutputStream) {
        val body = "<html>\n" +
                "<body>\n" +
                "<h1>Not Found!</h1>\n" +
                "</body>\n" +
                "</html>"
        val response = createHeader(body.length.toLong(), 404) + body
        sendLogMessage("To ${address.toString()}: Sended ${response.toByteArray().size} bytes")
        out.write(response.toByteArray())
    }

    private fun createHeader(len: Long, code: Int, contentType: String = "text/html"): String {
        return "HTTP/1.1 $code OK\n" +
                "Date: Mon, 27 Jul 2009 12:28:53 GMT\n" +
                "Server: Apache/2.2.14 (Win32)\n" +
                "Last-Modified: Wed, 22 Jul 2009 19:15:56 GMT\n" +
                "Content-Length: ${len}\n" +
                "Content-Type: ${contentType}\n" +
                "\n"
    }

    private fun responseFromFile(toOpen: File, out: OutputStream) {
        val mimeType = Files.probeContentType(toOpen.toPath()).orEmpty()

        var response = createHeader(toOpen.length(), 200, mimeType)
        out.write(response.toByteArray())
        FileInputStream(toOpen).use { inputStream ->
            val fileBytes = inputStream.readBytes()
            sendLogMessage("To ${address.toString()}: Sended ${response.length + fileBytes.size} bytes")
            out.write(fileBytes)
        }
    }

}