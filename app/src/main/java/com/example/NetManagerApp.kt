package com.example

import android.app.Application
import com.example.data.network.NetworkUtils

class NetManagerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NetworkUtils.registerContext(this)
    }
}
