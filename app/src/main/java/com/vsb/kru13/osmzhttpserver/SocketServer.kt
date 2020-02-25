package com.vsb.kru13.osmzhttpserver

import android.os.Build
import android.os.Handler
import android.util.Log
import androidx.annotation.RequiresApi
import java.io.IOException
import java.net.ServerSocket
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class SocketServer(val handler: Handler) : Thread() {

    internal var serverSocket: ServerSocket? = null
    val port = 12345
    internal var bRunning: Boolean = false

    var executorService: ExecutorService = Executors.newCachedThreadPool()

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
                executorService.execute(HttpThread(s, handler))
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

