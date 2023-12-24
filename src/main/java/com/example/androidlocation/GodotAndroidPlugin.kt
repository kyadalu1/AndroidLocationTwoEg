package com.example.androidlocation

import android.Manifest
import android.app.Activity
import android.app.Activity.RESULT_OK
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.core.app.ActivityCompat
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.LocationSettingsResponse
import com.google.android.gms.location.LocationSettingsStatusCodes
import com.google.android.gms.location.Priority
import com.google.android.gms.location.SettingsClient
import com.google.android.gms.tasks.Task
import org.godotengine.godot.Godot
import org.godotengine.godot.plugin.GodotPlugin
import org.godotengine.godot.plugin.SignalInfo
import org.godotengine.godot.plugin.UsedByGodot


class GodotAndroidPlugin(godot: Godot) : GodotPlugin(godot),LocationCallback {

    private val locationRequestCode = 123
    private val requestCheckSettings = 124

    private var locationService: LocationService? = null
    private var isServiceBound = false

    override fun onMainCreate(activity: Activity?): View? {
        return super.onMainCreate(activity)
    }

    override fun onGodotSetupCompleted() {
        super.onGodotSetupCompleted()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "location",
                "Location",
                NotificationManager.IMPORTANCE_LOW
            )
            val notificationManager =
                activity?.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as LocationService.LocalBinder
            locationService = binder.getService()
            locationService?.setLocationCallback(this@GodotAndroidPlugin)
            isServiceBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            locationService = null
            isServiceBound = false
        }
    }

    override fun getPluginName() = "AndroidLocationPlugin"


    override fun getPluginSignals(): MutableSet<SignalInfo> {
        val signals: MutableSet<SignalInfo> = mutableSetOf();
        signals.add(SignalInfo("locationSignal", String::class.java))
        return signals
    }

    @UsedByGodot
    private fun getLocation() {
        runOnUiThread {
            if (activity?.hasLocationPermission() == true) {
                startLocationService()
            } else {
                requestPermissions()
            }
        }
    }

    @UsedByGodot
    private fun stopLocation() {
        Intent(activity?.applicationContext, LocationService::class.java).apply {
            activity?.stopService(this)
            if (isServiceBound) {
                activity?.unbindService(serviceConnection)
                isServiceBound = false
                emitSignal("locationSignal", "Location stopped")
            }

        }
    }

    private fun requestPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.POST_NOTIFICATIONS
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
        activity?.let { ActivityCompat.requestPermissions(it, permissions, locationRequestCode) }
    }

    override fun onMainRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>?,
        grantResults: IntArray?
    ) {
        super.onMainRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == locationRequestCode) {
            val allLocationPermissionsGranted =
                grantResults?.all { it == PackageManager.PERMISSION_GRANTED }
            if (allLocationPermissionsGranted == true) {
                startLocationService()
            } else {
                Toast.makeText(activity, "Location permission denied", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(activity,"Location permission denied",Toast.LENGTH_LONG).show()
        }
    }

    private fun startService(){
        val serviceIntent = Intent(activity?.applicationContext, LocationService::class.java).apply {
            action = LocationService.ACTION_START
        }
        activity?.startService(serviceIntent)
        activity?.bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun startLocationService() {
        val locationManager =
            activity?.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val isEnabled =
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(
                LocationManager.NETWORK_PROVIDER
            )
        if (isEnabled) {
            startService()

        } else {

            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000L)
                .setWaitForAccurateLocation(false)
                .setMinUpdateIntervalMillis(2000)
                .setMaxUpdateDelayMillis(10000L)
                .build()

            val builder = LocationSettingsRequest.Builder()
                .addLocationRequest(request)
                .setAlwaysShow(true)
            val client: SettingsClient = com.google.android.gms.location.LocationServices.getSettingsClient(
                activity!!
            )
            val settingsTask: Task<LocationSettingsResponse> = client.checkLocationSettings(builder.build())

            settingsTask.addOnCompleteListener { task ->
                try {
                    val response = task.getResult(ApiException::class.java)
                    // Location settings are satisfied, you can proceed
                } catch (exception: ApiException) {
                    if (exception.statusCode == LocationSettingsStatusCodes.RESOLUTION_REQUIRED) {
                        // Location settings are not satisfied, show the user a dialog
                        try {
                            // Show the dialog by calling startResolutionForResult(),
                            // and check the result in onActivityResult().
                            val resolvable = exception as ResolvableApiException
                            resolvable.startResolutionForResult(activity!!,requestCheckSettings)
                        } catch (e: IntentSender.SendIntentException) {
                            Toast.makeText(activity, "${e.message} ", Toast.LENGTH_SHORT).show()
                            e.printStackTrace()
                        }
                    } else {
                        Toast.makeText(activity, "${exception.statusCode} ${exception.status}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    override fun onMainActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onMainActivityResult(requestCode, resultCode, data)
        if(requestCode == requestCheckSettings && resultCode == RESULT_OK){
            startService()
        } else {
            Toast.makeText(activity, "GPS not enabled", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onLocationUpdated(latitude: Double, longitude: Double) {
        runOnUiThread {
            emitSignal("locationSignal", "$latitude $longitude")
        }

    }


}
