package com.example.fishingnote.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** DB とバックアップを実際の Room（メモリ上）で確かめる */
@RunWith(AndroidJUnit4::class)
class RepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: Repository

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        repo = Repository(db)
    }

    @After fun tearDown() = db.close()

    private suspend fun sample(): Triple<Long, Long, Long> {
        val rod = repo.saveGear(Gear(name = "アジングロッド", category = GearCategory.ROD))
        val reel = repo.saveGear(Gear(name = "2000番リール", category = GearCategory.REEL, lineChangedOn = LocalDate.of(2026, 10, 1), lineReminderDays = 60))
        val setId = repo.saveSet(TackleSet(name = "アジング"), setOf(rod, reel))
        return Triple(rod, reel, setId)
    }

    @Test fun update_keeps_id_for_set_and_trip() = runBlocking {
        val (rod, _, setId) = sample()
        val again = repo.saveSet(TackleSet(id = setId, name = "アジング改"), setOf(rod))
        assertEquals(setId, again)
        val set = repo.getSet(setId)!!
        assertEquals("アジング改", set.set.name)
        assertEquals(listOf(rod), set.gear.map { it.id })

        val tripId = repo.saveTrip(Trip(date = LocalDate.of(2026, 11, 1), place = "A港", setId = setId),
            listOf(CatchEntry(tripId = 0, species = "アジ", count = 3)))
        val sameId = repo.saveTrip(Trip(id = tripId, date = LocalDate.of(2026, 11, 1), place = "A港"),
            listOf(CatchEntry(tripId = 0, species = "サバ", count = 1)))
        assertEquals(tripId, sameId)
        assertEquals(listOf("サバ"), repo.getTrip(tripId)!!.catches.map { it.species })
    }

    @Test fun deleting_gear_removes_it_from_sets_and_deleting_set_keeps_trip() = runBlocking {
        val (rod, _, setId) = sample()
        repo.deleteGear(repo.getGear(rod)!!)
        assertEquals(1, repo.getSet(setId)!!.gear.size)
        val tripId = repo.saveTrip(Trip(date = LocalDate.of(2026, 11, 1), setId = setId), emptyList())
        repo.deleteSet(repo.getSet(setId)!!.set)
        assertNull(repo.getTrip(tripId)!!.trip.setId)
    }

    @Test fun prepare_checklist_uses_set_and_basic_items() = runBlocking {
        val (_, _, setId) = sample()
        repo.addBasicItem("タオル")
        repo.prepareChecklist(setId)
        assertEquals(listOf("アジングロッド", "2000番リール", "タオル"), repo.checklist.first().map { it.name })
        val first = repo.checklist.first().first()
        repo.setChecked(first.id, true)
        repo.addChecklistItem("虫よけ")
        assertEquals("虫よけ", repo.checklist.first().last().name)
        repo.uncheckAll()
        assertTrue(repo.checklist.first().none { it.checked })
    }

    @Test fun quick_catch_adds_to_todays_trip_and_undo_removes_only_what_it_created() = runBlocking {
        val today = LocalDate.of(2026, 11, 20)
        val first = repo.addQuickCatch("アジ", today)
        assertTrue(first.createdTrip && first.createdCatch)
        val second = repo.addQuickCatch(" アジ ", today)
        assertEquals(first.tripId, second.tripId)
        assertEquals(first.catchId, second.catchId)
        assertEquals(2, repo.getTrip(first.tripId)!!.catches.single().count)

        // 取り消す順が前後しても、1匹分ずつ戻す（後から足した分まで消さない）
        repo.undoQuickCatch(first)
        assertEquals(1, repo.getTrip(first.tripId)!!.catches.single().count)
        repo.undoQuickCatch(second)
        assertNull(repo.getTrip(first.tripId))

        // 既にある今日の記録（場所入り）には足すだけで、元に戻しても記録そのものは残す
        val tripId = repo.saveTrip(Trip(date = today, place = "A港"), emptyList())
        val q = repo.addQuickCatch("サバ", today)
        assertEquals(tripId, q.tripId)
        repo.undoQuickCatch(q)
        assertEquals("A港", repo.getTrip(tripId)!!.trip.place)
        assertTrue(repo.getTrip(tripId)!!.catches.isEmpty())
    }

    @Test fun undo_keeps_empty_trip_that_user_made_beforehand() = runBlocking {
        val today = LocalDate.of(2026, 11, 21)
        // 利用者が先に作った、場所もメモもセットも空の今日の記録
        val tripId = repo.saveTrip(Trip(date = today), emptyList())
        val q = repo.addQuickCatch("アジ", today)
        assertEquals(tripId, q.tripId)
        assertTrue(!q.createdTrip)
        repo.undoQuickCatch(q)
        // 釣果は戻るが、記録そのものは残す
        assertTrue(repo.getTrip(tripId)!!.catches.isEmpty())
    }

    @Test fun quick_catch_uses_last_used_set_and_undo_still_removes_its_trip() = runBlocking {
        val (_, _, setId) = sample()
        repo.saveTrip(Trip(date = LocalDate.of(2026, 11, 1), setId = setId), emptyList())
        val today = LocalDate.of(2026, 11, 23)
        val q = repo.addQuickCatch("アジ", today)
        // 前回使ったセットが入る（集計で「セットなし」に集まらない）
        assertEquals(setId, repo.getTrip(q.tripId)!!.trip.setId)
        // セットを自動で入れただけの記録は、取り消すと消える
        repo.undoQuickCatch(q)
        assertNull(repo.getTrip(q.tripId))
    }

    @Test fun undo_after_backup_restore_does_not_delete_restored_trip() = runBlocking {
        val today = LocalDate.of(2026, 11, 22)
        val q = repo.addQuickCatch("サバ", today)
        val data = repo.snapshot()
        repo.replaceAll(data)
        repo.undoQuickCatch(q)
        // 読み込んだ記録は利用者の物として扱い、読み込む前の「元に戻す」では釣行も釣果も変えない
        val restored = repo.getTrip(q.tripId)!!
        assertEquals(1, restored.catches.size)
        assertEquals("サバ", restored.catches.single().species)
        assertEquals(1, restored.catches.single().count)
    }

    @Test fun deleted_checklist_item_can_be_restored_in_place() = runBlocking {
        repo.addChecklistItem("タオル")
        repo.addChecklistItem("飲み物")
        val item = repo.checklist.first().first()
        repo.deleteChecklistItem(item.id)
        repo.restoreChecklistItems(listOf(item))
        assertEquals(listOf("タオル", "飲み物"), repo.checklist.first().map { it.name })
    }

    @Test fun catch_count_stays_within_backup_range() = runBlocking {
        val q = repo.addQuickCatch("アジ", LocalDate.of(2026, 11, 24))
        repo.addCatchCount(q.catchId, -2)
        assertEquals(0, repo.getTrip(q.tripId)!!.catches.single().count)
        repo.addCatchCount(q.catchId, 200_000)
        assertEquals(99_999, repo.getTrip(q.tripId)!!.catches.single().count)
        // どちらの端でも、書き出した物をそのまま読み戻せる
        repo.replaceAll(Backup.fromJson(Backup.toJson(repo.snapshot())))
        assertEquals(99_999, repo.getTrip(q.tripId)!!.catches.single().count)
    }

    @Test fun quantity_never_goes_below_zero() = runBlocking {
        val id = repo.saveGear(Gear(name = "スナップ", category = GearCategory.TERMINAL, quantity = 1))
        repo.addQuantity(id, -1)
        repo.addQuantity(id, -1)
        assertEquals(0, repo.getGear(id)!!.quantity)
        // 上限の 9999 も超えない（超えると、書き出したバックアップを読み戻せなくなるため）
        repo.addQuantity(id, 20_000)
        assertEquals(9999, repo.getGear(id)!!.quantity)
        // 上限まで増やした状態でも、書き出した物をそのまま読み戻せる
        val json = Backup.toJson(repo.snapshot())
        repo.replaceAll(Backup.fromJson(json))
        assertEquals(9999, repo.getGear(id)!!.quantity)
    }

    @Test fun backup_round_trip_restores_everything() = runBlocking {
        val (_, _, setId) = sample()
        repo.addBasicItem("タオル")
        repo.prepareChecklist(setId)
        repo.saveTrip(Trip(date = LocalDate.of(2026, 11, 1), place = "A港", setId = setId),
            listOf(CatchEntry(tripId = 0, species = "アジ", sizeCm = 21.5, count = 3)))
        repo.saveSpot(Spot(name = "北堤防", query = "35.3,139.5", memo = "トイレあり"))
        val before = repo.snapshot()
        val json = Backup.toJson(before)

        repo.replaceAll(BackupData(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList()))
        assertTrue(repo.snapshot().gear.isEmpty())

        repo.replaceAll(Backup.fromJson(json))
        assertEquals(before, repo.snapshot())
    }

    @Test fun encrypted_backup_round_trip_on_device() = runBlocking {
        val (_, _, setId) = sample()
        repo.saveSpot(Spot(name = "北堤防", query = "35.3,139.5", memo = "トイレあり"))
        repo.saveTrip(Trip(date = LocalDate.of(2026, 11, 1), place = "A港", setId = setId), listOf(CatchEntry(tripId = 0, species = "アジ", count = 3)))
        val before = repo.snapshot()
        // 本番と同じ繰り返し回数で、端末での所要時間も測る
        val started = android.os.SystemClock.elapsedRealtime()
        val sealed = BackupCrypto.encrypt(Backup.toJson(before).toByteArray(), "Mebaru!Night-07".toCharArray())
        val encryptMs = android.os.SystemClock.elapsedRealtime() - started
        assertTrue(!sealed.contains("北堤防"))
        repo.replaceAll(BackupData(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList()))
        val t2 = android.os.SystemClock.elapsedRealtime()
        val plain = BackupCrypto.decrypt(sealed, "Mebaru!Night-07".toCharArray())
        val decryptMs = android.os.SystemClock.elapsedRealtime() - t2
        repo.replaceAll(Backup.fromJson(String(plain)))
        assertEquals(before, repo.snapshot())
        android.util.Log.i("BackupCryptoTiming", "encrypt=${encryptMs}ms decrypt=${decryptMs}ms")
        // 違うパスワードでは開けない
        try { BackupCrypto.decrypt(sealed, "wrong-password".toCharArray()); fail("開けてはいけない") } catch (e: WrongPasswordException) { }
    }

    @Test fun encryption_matches_vector_made_by_another_implementation_on_device() {
        // 単体テスト（パソコン）と同じ、Python で作った見本。端末の暗号の部品でも同じ結果になることを確かめる
        val text = "fishing-note-backup-encrypted\n1\n100000\nAAECAwQFBgcICQoLDA0ODw==\nAAECAwQFBgcICQoL\nVEic0vZ6XcOKE/OAPk0WdU3pwSTjn9jV3/3VfEDq6u8ekLszyVknMgQ=\n"
        val plain = "{\"kat\":\"釣りノート\"}".toByteArray(Charsets.UTF_8)
        assertEquals(text, BackupCrypto.encryptWith(plain, "Kat-Test-Pass-2026".toCharArray(), 100_000, ByteArray(16) { it.toByte() }, ByteArray(12) { it.toByte() }))
        assertTrue(plain.contentEquals(BackupCrypto.decrypt(text, "Kat-Test-Pass-2026".toCharArray())))
    }

    @Test fun broken_backup_is_rejected_before_touching_db() = runBlocking {
        sample()
        val before = repo.snapshot()
        for (bad in listOf("not json", """{"format":"other","version":1}""", """{"format":"fishing-note-backup","version":99}""",
            // 表が欠けたバックアップは、空として受け入れずに止める（受け入れると全データが消える）
            """{"format":"fishing-note-backup","version":1}""",
            Backup.toJson(before).replace("\"spots\"", "\"spotsX\""),
            """{"format":"fishing-note-backup","version":1,"setGear":[{"setId":5,"gearId":6}]}""",
            // 画面では入れられない値（負の数・極端に大きい数・極端な日付・長すぎる文字・大きすぎる魚）
            Backup.toJson(before).replaceFirst("\"quantity\": 1", "\"quantity\": -5"),
            Backup.toJson(before).replaceFirst("\"quantity\": 1", "\"quantity\": 2147483647"),
            // 整数に丸めると範囲内に見える数（4294967297 は int にすると 1 になる）・小数・文字で書いた数
            Backup.toJson(before).replaceFirst("\"quantity\": 1", "\"quantity\": 4294967297"),
            Backup.toJson(before).replaceFirst("\"quantity\": 1", "\"quantity\": 1.5"),
            Backup.toJson(before).replaceFirst("\"quantity\": 1", "\"quantity\": \"1\""),
            // 範囲外の並び順
            Backup.toJson(before.copy(checklist = listOf(ChecklistItem(id = 1, name = "ロッド", sortOrder = -1)))),
            Backup.toJson(before.copy(trips = listOf(Trip(id = 1, date = LocalDate.of(9999, 1, 1))))),
            Backup.toJson(before.copy(spots = listOf(Spot(id = 1, name = "あ".repeat(5000))))),
            Backup.toJson(before.copy(trips = listOf(Trip(id = 1, date = LocalDate.of(2026, 1, 1))), catches = listOf(CatchEntry(id = 1, tripId = 1, species = "アジ", sizeCm = 9999.0, count = 1)))),
            // 深く入れ子にした JSON
            """{"format":"fishing-note-backup","version":1,"x":""" + "[".repeat(200_000) + "}")) {
            try {
                repo.replaceAll(Backup.fromJson(bad))
                fail("読み込めてはいけない: $bad")
            } catch (e: BackupFormatException) {
                // 想定どおり
            }
        }
        assertEquals(before, repo.snapshot())
    }
}
