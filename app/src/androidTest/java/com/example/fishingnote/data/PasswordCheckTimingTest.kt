package com.example.fishingnote.data

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** パスワードの判定（強さの見積もりを含む）が、端末で入力に追いつく時間で終わるか */
@RunWith(AndroidJUnit4::class)
class PasswordCheckTimingTest {
    @Test fun check_finishes_quickly_on_device() {
        val t0 = System.nanoTime()
        BackupCrypto.warmUp()
        val warm = (System.nanoTime() - t0) / 1_000_000
        Log.i("PwTiming", "warmUp ${warm}ms")
        for (p in listOf("Tanaka19850315", "Tanaka1985031", "Ki7#vQ2!mW9zR", "a".repeat(11) + "B1!", "Hz4-Tq8@Lp3x-Nb-Qw7#Zr2!Mk9\$Vt5&Lp3x-Nb-Qw7#Zr2!Mk9\$Vt5&Lp3x-Nb")) {
            val t = System.nanoTime()
            val r = BackupCrypto.passwordProblem(p)
            val ms = (System.nanoTime() - t) / 1_000_000
            Log.i("PwTiming", "len=${p.length} ${ms}ms -> ${r != null}")
            assertTrue("判定に ${ms}ms かかった（len=${p.length}）", ms < 1_000)
        }
    }
}
