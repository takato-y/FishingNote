package com.example.fishingnote

import android.app.Application
import androidx.core.content.edit
import com.example.fishingnote.data.AppDatabase
import com.example.fishingnote.data.Repository

/** 部品の組み立ては、ここ1か所で行う */
class FishingNoteApp : Application() {
    val repository: Repository by lazy { Repository(AppDatabase.create(this)) }

    private val prefs by lazy { getSharedPreferences("app", MODE_PRIVATE) }

    /** 最後にバックアップを保存した日（epoch day）。一度も無ければ null */
    var lastBackupDay: Long?
        get() = prefs.getLong("lastBackupDay", -1).takeIf { it >= 0 }
        set(v) = prefs.edit { if (v == null) remove("lastBackupDay") else putLong("lastBackupDay", v) }

    /** 今のチェックリストを作ったセットの名前（準備の画面の見出しに出す）。セットを使わなければ null */
    var checklistTitle: String?
        get() = prefs.getString("checklistTitle", null)
        set(v) = prefs.edit { if (v == null) remove("checklistTitle") else putString("checklistTitle", v) }

    /**
     * 地図アプリなどから共有で受け取った文。釣り場の保存画面が読んだら消す。
     * 受け取った直後にアプリが終了しても失わないよう、端末に保存する。続けて届いたときは新しい方を残す。
     */
    var pendingShare: String?
        get() = prefs.getString("pendingShare", null)
        set(v) = prefs.edit(commit = true) { if (v == null) remove("pendingShare") else putString("pendingShare", v) }

    /** いつもの持ち物の例は最初の1回だけ入れる（利用者が消した後に戻さないため） */
    suspend fun seedOnce() {
        val prefs = getSharedPreferences("app", MODE_PRIVATE)
        if (prefs.getBoolean("seeded", false)) return
        repository.seedIfEmpty()
        prefs.edit { putBoolean("seeded", true) }
    }
}
