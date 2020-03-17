@file:Suppress("DEPRECATION")

package com.vsb.kru13.osmzhttpserver

import android.R.attr
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.graphics.*
import android.hardware.Camera
import android.hardware.Camera.PictureCallback
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.util.*
import kotlin.collections.ArrayList
import kotlin.concurrent.thread


data class SocketHolder(
        val socket: Socket,
        val outputStream: OutputStream,
        val inputStream: InputStream)

class CameraServer(context: Context, private val camera: Camera) {
    private val byTakePicture: Boolean = false
    private var timer: Timer? = null
    private val sockets: ArrayList<SocketHolder> = ArrayList()
    private val surfaceTexture = SurfaceTexture(MODE_PRIVATE)
    private var thread: Thread? = null
    private val safeShootSemaphore = java.util.concurrent.Semaphore(1)

    private val picture = PictureCallback { data, _ ->
        thread {
            sendPhoto(data)
            safeShootSemaphore.release()
        }
    }
    private val picturePreviewCallback = Camera.PreviewCallback { data, _ ->
        thread {
            if (safeShootSemaphore.tryAcquire()) { //against concurent previews
                val parameters = camera.parameters
                val width = parameters.previewSize.width
                val height = parameters.previewSize.height

                val yuv = YuvImage(data, parameters.previewFormat, width, height, null)

                val out = ByteArrayOutputStream()
                yuv.compressToJpeg(Rect(0, 0, width, height), 90, out)

                val bytes: ByteArray = out.toByteArray()
                sendPhoto(bytes)
                safeShootSemaphore.release()
            }
        }
    }

    private fun sendPhoto(data: ByteArray?) {
        val socketsToRemove = ArrayList<SocketHolder>()
        synchronized(this) {
            //adding new socket to list
            for (holder in sockets) {
                try {
                    holder.outputStream.write("--frame\n".toByteArray())
                    holder.outputStream.write(
                            "Content-Type: image/jpeg\n\n".toByteArray())
                    holder.outputStream.write(data)
                    holder.outputStream.flush()
                } catch (ex: IOException) {
                    socketsToRemove.add(holder)
                }
            }
            for (holder in socketsToRemove) {
                holder.socket.close()
                holder.outputStream.close()
                holder.inputStream.close()
            }
            sockets.removeAll(socketsToRemove)
        }
    }

    init {
       // camera.parameters.previewFormat = PixelFormat.JPEG not working
        camera.setPreviewTexture(surfaceTexture)
        if (this.byTakePicture) {
            this.thread = thread {
                while (true) {
                    safeShootSemaphore.acquire()
                    camera.startPreview()
                    Thread.sleep(600)
                    camera.takePicture(null, null, picture)
                    Thread.sleep(600)
                }
            }
        } else {
            camera.startPreview()
            Thread.sleep(600)
            camera.setPreviewCallback(picturePreviewCallback)
        }


    }

    fun addSocket(out: OutputStream, socket: Socket) {
        val header = "HTTP/1.1 200 OK\n" +
                "Date: Mon, 27 Jul 2009 12:28:53 GMT\n" +
                "Server: Apache/2.2.14 (Win32)\n" +
                "Last-Modified: Wed, 22 Jul 2009 19:15:56 GMT\n" +
                "Content-Type: multipart/x-mixed-replace; boundary=frame\n" +
                "\n"
        out.write(header.toByteArray())
        synchronized(this) {
            this.sockets.add(SocketHolder(socket, out, socket.getInputStream()))
        }
    }
}