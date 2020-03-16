package com.vsb.kru13.osmzhttpserver

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Camera
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.net.wifi.WifiManager
import android.os.*
import android.text.InputType
import android.text.format.Formatter
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.android.synthetic.main.activity_main.*
import java.net.NetworkInterface
import java.net.SocketException
import java.util.*
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList


class MainActivity : AppCompatActivity(), View.OnClickListener {
    private var s: SocketServer? = null
    private var started = false

    private companion object {
        private const val READ_EXTERNAL_STORAGE_PLUS_CAMERA = 1
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


    private var cameraDevice: CameraDevice? = null
    private lateinit var cameraId: String
    private val cameraOpenCloseLock = Semaphore(1)
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null
    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground").also { it.start() }
        backgroundHandler = Handler(backgroundThread?.looper)
    }
    private fun checkCameraHardware(context: Context): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    }
    fun getCameraInstance(): Camera? {
        return try {
            Camera.open() // attempt to get a Camera instance
        } catch (e: Exception) {
            // Camera is not available (in use or does not exist)
            null // returns null if camera is unavailable
        }
    }
    override fun onClick(v: View) {
        if (v.id == R.id.button1) {
            val permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
            val permissionCameraCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            if (permissionCheck != PackageManager.PERMISSION_GRANTED || permissionCameraCheck != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                        this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.CAMERA), READ_EXTERNAL_STORAGE_PLUS_CAMERA)
            } else {
                val btn = v as Button
                btn.text = "Started"
                if (!started) {
                    started = true
                    maxThreadsView.inputType = 0
                    val stringThreads = maxThreadsView.text.toString()
                    s = SocketServer(handler, stringThreads.toIntOrNull() ?: 5, this.applicationContext, getCameraInstance()!!)
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
            maxThreadsView.inputType = InputType.TYPE_CLASS_NUMBER
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            READ_EXTERNAL_STORAGE_PLUS_CAMERA -> if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                button1.text = "Started"
                started = true
                val stringThreads = maxThreadsView.text.toString()
                s = SocketServer(handler, stringThreads.toIntOrNull() ?: 5, this.applicationContext, getCameraInstance()!!)
                s!!.start()
            }
            else -> {
            }
        }
    }
}