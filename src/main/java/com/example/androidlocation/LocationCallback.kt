package com.example.androidlocation

interface LocationCallback {
    fun onLocationUpdated(latitude: Double, longitude: Double)
}