package com.vsb.kru13.osmzhttpserver

import android.R
import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.hardware.Camera
import android.os.Build
import android.os.Messenger
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.PRIORITY_MIN


class ServerIntentService : IntentService("ServerIntentService") {
    private val ONGOING_NOTIFICATION_ID = 1337
    private var s: SocketServer? = null
    private var running = false;

    fun getCameraInstance(): Camera? {
        return try {
            Camera.open() // attempt to get a Camera instance
        } catch (e: Exception) {
            // Camera is not available (in use or does not exist)
            null // returns null if camera is unavailable
        }
    }

    override fun onHandleIntent(intent: Intent?) {
        val messenger = intent?.getParcelableExtra<Messenger>("messenger")
        if (!running) {
            val channelId =
                    createNotificationChannel("http_service", "Http Service")
            running = true;
            val notificationBuilder = NotificationCompat.Builder(this, channelId)
            val notification = notificationBuilder.setOngoing(true)
                    .setSmallIcon(R.mipmap.sym_def_app_icon)
                    .setPriority(PRIORITY_MIN)
                    .setCategory(Notification.CATEGORY_SERVICE)
                    .build()
            startForeground(ONGOING_NOTIFICATION_ID, notification)
            val stringThreads = intent?.getStringExtra("stringThreads")
            s = SocketServer(messenger!!, stringThreads?.toIntOrNull()
                    ?: 5, getCameraInstance()!!)

            s!!.start()
            s?.join()
        }
    }

    override fun onDestroy() {
        s?.close()
        try {
            s?.join()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (running) {
            val messenger = intent?.getParcelableExtra<Messenger>("messenger")
            s?.messenger = messenger!!
        }
        return super.onStartCommand(intent, flags, startId)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(channelId: String, channelName: String): String {
        val chan = NotificationChannel(channelId,
                channelName, NotificationManager.IMPORTANCE_NONE)
        chan.lightColor = Color.BLUE
        chan.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        val service = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        service.createNotificationChannel(chan)
        return channelId
    }
}