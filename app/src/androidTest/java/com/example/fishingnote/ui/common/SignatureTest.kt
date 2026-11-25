package com.example.fishingnote.ui.common

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** 現在地や釣り場を渡す先の Google マップを、署名で確かめる処理のテスト */
@RunWith(AndroidJUnit4::class)
class SignatureTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun genuine_google_maps_is_trusted() {
        // Google マップが入っている端末（Google の入ったエミュレータなど）でだけ確かめる
        val installed = runCatching { context.packageManager.getPackageInfo(GOOGLE_MAPS, 0) }.isSuccess
        assumeTrue("Google マップが入っていない端末", installed)
        assertTrue(isGenuineGoogleMaps(context))
    }

    @Test fun other_signature_or_missing_app_is_not_trusted() {
        // このアプリ自身は Google の署名ではないので、Google マップの証明書とは一致しない
        assertFalse(isSignedBy(context, context.packageName, "f0fd6c5b410f25cb25c3b53346c8972fae30f8ee7411df910480ad6b2d60db83"))
        // 入っていないアプリは信用しない
        assertFalse(isSignedBy(context, "com.example.not.installed", "00".repeat(32)))
    }
}
