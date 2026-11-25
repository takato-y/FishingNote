package com.example.fishingnote

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf
import androidx.lifecycle.lifecycleScope
import com.example.fishingnote.ui.AppNav
import com.example.fishingnote.ui.theme.FishingNoteTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    /** 共有を受け取るたびに増やす。画面側はこれが変わったら釣り場の保存画面を開く */
    private val shareRequests = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lifecycleScope.launch { (application as FishingNoteApp).seedOnce() }
        // 画面の作り直し（回転など）では、同じ共有をもう一度受け取らない
        if (savedInstanceState == null) receiveShare(intent)
        setContent { FishingNoteTheme { AppNav(shareRequests.intValue) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        receiveShare(intent)
    }

    private fun receiveShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND || intent.type != "text/plain") return
        // 細工した中身で読み出しが失敗しても落ちないようにする。長すぎる物は途中で切らずに受け取らない
        // （切ると URL や名前が途中で変わり、別の場所を保存してしまうため）
        val text = runCatching { intent.getStringExtra(Intent.EXTRA_TEXT) }.getOrNull()
            ?.takeIf { it.isNotBlank() && it.length <= SHARE_MAX } ?: return
        (application as FishingNoteApp).pendingShare = text
        shareRequests.intValue++
    }
}

/** 共有で受け取る文の長さの上限。地図アプリの共有は名前と URL だけなので、これで足りる */
private const val SHARE_MAX = 2_000
