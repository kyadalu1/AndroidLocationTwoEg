package com.example.androidlocation

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class LocationService: Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var locationClient: LocationClient

    private val binder = LocalBinder()
    private var locationCallback: LocationCallback? = null

    inner class LocalBinder : Binder() {
        fun getService(): LocationService = this@LocationService
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }


    fun setLocationCallback(callback: LocationCallback) {
        locationCallback = callback
    }

    override fun onCreate() {
        super.onCreate()

        locationClient = DefaultLocationClient(
            applicationContext,
            LocationServices.getFusedLocationProviderClient(applicationContext)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when(intent?.action) {
            ACTION_START -> start()
        }
        return super.onStartCommand(intent, flags, startId)
    }



    private fun start() {
        val notification = NotificationCompat.Builder(this, "location")
            .setContentTitle("Tracking location...")
            .setContentText("Location: null")
            .setSmallIcon(R.drawable.ic_android_black_24dp)
            .setOngoing(true)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager


        locationClient
            .getLocationUpdates(10000L)
            .catch { e -> e.printStackTrace() }
            .onEach { location ->
                val lat = location.latitude
                val long = location.longitude
                locationCallback?.onLocationUpdated(lat, long)

                val updatedNotification = notification.setContentText(
                    "Location: ($lat, $long)"
                )
                notificationManager.notify(1, updatedNotification.build())
            }
            .launchIn(serviceScope)

        startForeground(1, notification.build())
    }


    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        const val ACTION_START = "ACTION_START"
    }
}