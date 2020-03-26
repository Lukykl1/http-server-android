package com.vsb.kru13.osmzhttpserver

import android.content.Context
import android.hardware.Camera
import android.os.Build
import android.os.Handler
import android.os.Messenger
import android.util.Log
import androidx.annotation.RequiresApi
import java.io.IOException
import java.net.ServerSocket
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore

class SocketServer(var messenger: Messenger, val maxThreads: Int, camera: Camera) : Thread() {

    private val cameraServer: CameraServer = CameraServer(camera)
    internal var serverSocket: ServerSocket? = null
    val port = 12345
    internal var bRunning: Boolean = false

    var executorService: ExecutorService = Executors.newCachedThreadPool()
    val semaphore = Semaphore(this.maxThreads)

    fun close() {
        try {
            serverSocket?.close()
        } catch (e: IOException) {
            Log.d("SERVER", "Error, probably interrupted in accept(), see log")
            e.printStackTrace()
        }

        bRunning = false
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    override fun run() {
        try {
            Log.d("SERVER", "Creating Socket")
            serverSocket = ServerSocket(port)
            bRunning = true

            while (bRunning) {
                val s = serverSocket!!.accept()
                /*
                GlobalScope.launch { val thread = HttpThread(s)
                thread.run()
                }
                */
                Log.d("SERVER", "Socket Waiting for connection")
                executorService.execute(HttpThread(s, messenger, semaphore, this.cameraServer))
                Log.d("SERVER", "Socket Closed")
            }
        } catch (e: IOException) {
            if (serverSocket != null && serverSocket!!.isClosed)
                Log.d("SERVER", "Normal exit")
            else {
                Log.d("SERVER", "Error")
                e.printStackTrace()
            }
        } finally {
            serverSocket = null
            bRunning = false
        }
    }

}

