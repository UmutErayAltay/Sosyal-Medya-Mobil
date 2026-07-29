package com.umuterayaltay.sosyal.nativeapp

import android.app.Application

class SosyalApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
    }
}
