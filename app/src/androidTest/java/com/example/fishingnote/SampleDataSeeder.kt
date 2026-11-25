package com.example.fishingnote

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.fishingnote.data.BackupData
import com.example.fishingnote.data.CatchEntry
import com.example.fishingnote.data.Gear
import com.example.fishingnote.data.GearCategory
import com.example.fishingnote.data.Spot
import com.example.fishingnote.data.TackleSet
import com.example.fishingnote.data.Trip
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * 画面の確認用に、アプリの実際の DB へ見本のデータ（架空）を入れる。
 * 通常のテストでは動かさない。`-e seed true` を付けて手動で実行したときだけ動く。
 */
@RunWith(AndroidJUnit4::class)
class SampleDataSeeder {
    @Test fun seed(): Unit = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("seed") == "true")
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as FishingNoteApp
        val repo = app.repository
        app.seedOnce()
        repo.replaceAll(BackupData(emptyList(), emptyList(), emptyList(), repo.snapshot().basicItems, emptyList(), emptyList(), emptyList(), emptyList()))
        val today = LocalDate.now()

        val rod = repo.saveGear(Gear(name = "アジングロッド 6.4ft", category = GearCategory.ROD, brand = "メーカーA", spec = "6.4ft UL / ジグ単 0.4〜5g"))
        val shoreRod = repo.saveGear(Gear(name = "ショアジギングロッド", category = GearCategory.ROD, brand = "メーカーB", spec = "9.6ft MH / 〜60g"))
        val reel1 = repo.saveGear(Gear(name = "2000番スピニング", category = GearCategory.REEL, brand = "メーカーA", spec = "エステル0.3号 200m",
            lineChangedOn = today.minusDays(45), lineReminderDays = 30))
        val reel2 = repo.saveGear(Gear(name = "4000番スピニング", category = GearCategory.REEL, brand = "メーカーB", spec = "PE1.5号 200m",
            lineChangedOn = today.minusDays(20), lineReminderDays = 90))
        val leader = repo.saveGear(Gear(name = "フロロリーダー", category = GearCategory.LINE, spec = "1.5号 30m", quantity = 1, minQuantity = 1))
        val jighead = repo.saveGear(Gear(name = "ジグヘッド", category = GearCategory.LURE, spec = "1.0g #8", quantity = 6, minQuantity = 5))
        val worm = repo.saveGear(Gear(name = "アジ用ワーム", category = GearCategory.LURE, spec = "2インチ クリア", quantity = 2, minQuantity = 3))
        val jig = repo.saveGear(Gear(name = "メタルジグ", category = GearCategory.LURE, spec = "40g ピンク", quantity = 4))
        repo.saveGear(Gear(name = "メタルジグ", category = GearCategory.LURE, spec = "60g ブルー", quantity = 0, owned = false, memo = "店で見かけたら買う"))
        val snap = repo.saveGear(Gear(name = "スナップ", category = GearCategory.TERMINAL, spec = "#1", quantity = 3, minQuantity = 5))
        val headlight = repo.saveGear(Gear(name = "ヘッドライト", category = GearCategory.OTHER))

        val ajing = repo.saveSet(TackleSet(name = "アジング", memo = "夜の常夜灯まわり"), setOf(rod, reel1, leader, jighead, worm, headlight))
        val shore = repo.saveSet(TackleSet(name = "ショアジギング", memo = "朝まずめ"), setOf(shoreRod, reel2, leader, jig, snap))
        repo.prepareChecklist(ajing)

        repo.saveTrip(Trip(date = today.minusDays(3), place = "見本漁港 北堤防", setId = ajing, memo = "下げ潮。常夜灯の明暗で連発"),
            listOf(CatchEntry(tripId = 0, species = "アジ", sizeCm = 22.5, count = 14), CatchEntry(tripId = 0, species = "メバル", sizeCm = 18.0, count = 2)))
        repo.saveTrip(Trip(date = today.minusDays(10), place = "見本海岸", setId = shore, memo = "朝まずめ 1時間だけ反応"),
            listOf(CatchEntry(tripId = 0, species = "サバ", sizeCm = 32.0, count = 3)))
        repo.saveTrip(Trip(date = today.minusDays(17), place = "見本漁港 北堤防", setId = ajing),
            listOf(CatchEntry(tripId = 0, species = "アジ", sizeCm = 19.0, count = 6)))

        repo.saveSpot(Spot(name = "見本漁港 北堤防", query = "35.0,139.0", memo = "駐車場あり・トイレは港の入口"))
        repo.saveSpot(Spot(name = "見本海岸", memo = "朝まずめが良い。足場が高いので玉網必須"))
    }
}
