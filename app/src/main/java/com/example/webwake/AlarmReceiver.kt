package com.example.webwake

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // ここでYouTubeを起動する
        val url = "https://youtu.be/oxkI8tgC2bA?si=Jcx6i5fMPqis7s2c"
        val youtubeIntent = Intent(Intent.ACTION_VIEW, url.toUri()).apply {
            setPackage("com.google.android.youtube")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) // バックグラウンド起動に必須
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        try {
            context.startActivity(youtubeIntent)
        } catch (e: Exception) {
            // YouTubeアプリがない場合はブラウザで開く
            youtubeIntent.setPackage(null)
            context.startActivity(youtubeIntent)
        }
    }
}