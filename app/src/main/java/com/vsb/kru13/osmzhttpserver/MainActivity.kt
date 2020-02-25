package com.vsb.kru13.osmzhttpserver

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.text.format.Formatter
import android.util.Log
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.android.synthetic.main.activity_main.*
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketException
import java.util.*
import kotlin.collections.ArrayList


class MainActivity : AppCompatActivity(), View.OnClickListener {
    private var s: SocketServer? = null
    private var started = false

    private companion object {
        private const val READ_EXTERNAL_STORAGE = 1
    }

    private val handler: Handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(inputMessage: Message) {
            val bundle = inputMessage.data as Bundle
            val log = bundle.getCharSequence(HttpThread.LOG_KEY).toString()
            val text = logView.text.toString()
            logView.text = text + "\n " + log
            scrollLogView.fullScroll(View.FOCUS_DOWN)
        }
    }

    fun getLocalIpAddress(): Array<String?>? {
        val addresses = ArrayList<String>()
        try {
            for (intf in NetworkInterface.getNetworkInterfaces()) {
                for (inetAddress in intf.getInetAddresses()) {
                    if (!inetAddress.isLoopbackAddress()) {
                        addresses.add(inetAddress.getHostAddress().toString())
                    }
                }
            }
        } catch (ex: SocketException) {
            val LOG_TAG: String? = null
            Log.e(LOG_TAG, ex.toString())
        }
        return addresses.toArray(arrayOfNulls<String>(0))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val btn1 = findViewById<View>(R.id.button1) as Button
        val btn2 = findViewById<View>(R.id.button2) as Button
        btn1.setOnClickListener(this)
        btn2.setOnClickListener(this)
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ipAddress: String = Formatter.formatIpAddress(wifiManager.connectionInfo.ipAddress)
        ipView.setText("Wifi IP Address: $ipAddress \nOther IP adresses: ${getLocalIpAddress()?.joinToString(",\n")}}")
    }

    override fun onClick(v: View) {
        if (v.id == R.id.button1) {
            val permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
            if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                        this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), READ_EXTERNAL_STORAGE)
            } else {
                val btn = v as Button
                btn.text = "Started"
                if (!started) {
                    started = true
                    s = SocketServer(handler)
                    s!!.start()
                }
            }
        }
        if (v.id == R.id.button2) {
            s?.close()
            try {
                s?.join()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
            button1.text = "STAR HTTP SERVER"
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            READ_EXTERNAL_STORAGE -> if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                s = SocketServer(handler)
                s!!.start()
            }
            else -> {
            }
        }
    }
}