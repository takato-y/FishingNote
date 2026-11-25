package com.example.fishingnote.ui.common

import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Build
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** 現在地の取得を、テスト用の位置の提供元で確かめる（実機の GPS に頼らない） */
@RunWith(AndroidJUnit4::class)
class LocationTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val context = inst.targetContext
    private val lm = context.getSystemService(LocationManager::class.java)
    private val provider = LocationManager.FUSED_PROVIDER

    private fun shell(cmd: String) { inst.uiAutomation.executeShellCommand(cmd).close(); SystemClock.sleep(300) }

    @Before fun setUp() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        shell("appops set ${context.packageName} android:mock_location allow")
        shell("pm grant ${context.packageName} android.permission.ACCESS_COARSE_LOCATION")
        lm.addTestProvider(provider, false, false, false, false, true, true, true,
            ProviderProperties.POWER_USAGE_LOW, ProviderProperties.ACCURACY_COARSE)
        lm.setTestProviderEnabled(provider, true)
    }

    @After fun tearDown() {
        runCatching { lm.removeTestProvider(provider) }
        shell("appops set ${context.packageName} android:mock_location default")
    }

    @Test fun returnsCurrentLocationNearTheFix() = runBlocking {
        // 実際の操作と同じく、アプリの画面が出ている状態で取る（裏で動くアプリには Android が位置を渡さない）
        val scenario = androidx.test.core.app.ActivityScenario.launch(com.example.fishingnote.MainActivity::class.java)
        // 位置を求めた後に届く新しい位置で返すかを見る（maxAgeMs = 0 で、前に取れた位置を使う近道を通らない）
        val feeder = Thread {
            try {
                while (!Thread.currentThread().isInterrupted) {
                    lm.setTestProviderLocation(provider, Location(provider).apply {
                        latitude = 35.618; longitude = 139.836; accuracy = 10f
                        time = System.currentTimeMillis(); elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                    })
                    Thread.sleep(500)
                }
            } catch (e: InterruptedException) { }
        }.apply { start() }
        val got = try { currentApproxLocation(context, maxAgeMs = 0) } finally { feeder.interrupt(); feeder.join(); scenario.close() }
        assertNotNull("現在地が取れない", got)
        // おおよその位置の権限では、数km ずらされることがあるため幅を持たせる
        assertEquals(35.618, got!!.latitude, 0.1)
        assertEquals(139.836, got.longitude, 0.1)
    }

    @Test fun returnsNullWhenNoLocationArrivesInTime() = runBlocking {
        val scenario = androidx.test.core.app.ActivityScenario.launch(com.example.fishingnote.MainActivity::class.java)
        try {
            val t0 = SystemClock.elapsedRealtime()
            val got = currentApproxLocation(context, timeoutMs = 2_000, maxAgeMs = 0)
            val waited = SystemClock.elapsedRealtime() - t0
            org.junit.Assert.assertNull("位置を送っていないのに現在地が返った", got)
            // 時間切れで止まること（いつまでも待たない）
            org.junit.Assert.assertTrue("待ち時間 ${waited}ms", waited in 1_500..6_000)
        } finally { scenario.close() }
    }
}
