package com.vsb.kru13.osmzhttpserver

import android.os.Build
import android.os.Environment
import android.util.Log

import androidx.annotation.RequiresApi
import java.io.*

import java.net.ServerSocket
import java.nio.file.Files
import java.util.regex.Pattern
import kotlinx.coroutines.*

class SocketServer : Thread() {

    internal var serverSocket: ServerSocket? = null
    val port = 12345
    internal var bRunning: Boolean = false

    fun close() {
        try {
            serverSocket!!.close()
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
                val thread = Thread(HttpThread(s))
                thread.run()
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

