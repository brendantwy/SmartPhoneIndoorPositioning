package com.example.tp

import android.app.Application
import android.content.Context

class NKBLEApp : Application() {
    lateinit var mContext: Context

    override fun onCreate() {
        super.onCreate()
        mContext = this
    }

    fun getContext(): Context{
        return mContext
    }
}