@file:Suppress("DEPRECATION")

package com.vsb.kru13.osmzhttpserver

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.hardware.Camera.PictureCallback
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlinx.coroutines.sync.Semaphore
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
    private var timer: Timer? = null
    private val sockets: ArrayList<SocketHolder> = ArrayList()
    private val surfaceTexture = SurfaceTexture(MODE_PRIVATE)
    private var thread: Thread
    private val safeShootSemaphore = java.util.concurrent.Semaphore(1)

    private val picture = PictureCallback { data, _ ->
        thread {
            val socketsToRemove = ArrayList<SocketHolder>()
            synchronized(this) {
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
            safeShootSemaphore.release()
        }
    }

    init {
        camera.setPreviewTexture(surfaceTexture)
        this.thread = thread {
            while (true) {
                safeShootSemaphore.acquire()
                camera.startPreview()
                Thread.sleep(600)
                camera.takePicture(null, null, picture)
                Thread.sleep(600)
            }

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