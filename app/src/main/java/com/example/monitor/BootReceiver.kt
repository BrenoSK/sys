package com.example.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent?) {
        if (!Config.configurado()) return
        try {
            SyncService.iniciar(ctx)
        } catch (e: Exception) {
            Log.e("BootReceiver", "falha ao iniciar", e)
        }
    }
}