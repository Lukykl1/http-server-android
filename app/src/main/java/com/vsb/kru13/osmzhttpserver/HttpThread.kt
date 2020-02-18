package com.vsb.kru13.osmzhttpserver

import android.os.Environment
import android.util.Log
import java.io.*
import java.net.Socket
import java.nio.file.Files
import java.util.regex.Pattern

class HttpThread(val socket: Socket) : Runnable {

    public override fun run() {
        try {
            val out = socket.getOutputStream()
            val bufferedReader = BufferedReader(InputStreamReader(socket.getInputStream()))

            val path: String? = proccessRequest(bufferedReader)
            if (path == null) {
                socket.close()
                return
            }
            createResponseForPath(path, out)
            out.flush()
        } finally {
            socket.close()
        }
    }

    private fun createResponseForPath(path: String, out: OutputStream) {
        val baseDir = Environment.getExternalStorageDirectory().absolutePath
        val pathFile = baseDir + path

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

    private fun proccessRequest(bufferedReader: BufferedReader): String? {
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
            "<li><a href = ${href(it)}>" + name(it) +
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
        out.write(response.toByteArray())
    }

    private fun response404(out: OutputStream) {
        val body = "<html>\n" +
                "<body>\n" +
                "<h1>Not Found!</h1>\n" +
                "</body>\n" +
                "</html>"
        val response = createHeader(body.length.toLong(), 404) + body
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
        val mimeType = Files.probeContentType(toOpen.toPath())

        var response = createHeader(toOpen.length(), 200, mimeType)
        out.write(response.toByteArray())
        FileInputStream(toOpen).use { inputStream ->
            out.write(inputStream.readBytes())
        }
    }

}