package com.example.fishingnote.data

import androidx.room.withTransaction
import com.example.fishingnote.domain.DEFAULT_BASIC_ITEMS
import com.example.fishingnote.domain.buildChecklistNames
import kotlinx.coroutines.flow.Flow

/** 画面から DB を使うときの窓口。複数の表をまたぐ書き込みは1つのトランザクションで行う。 */
class Repository(private val db: AppDatabase) {
    private val gearDao = db.gearDao()
    private val setDao = db.setDao()
    private val checklistDao = db.checklistDao()
    private val tripDao = db.tripDao()
    private val spotDao = db.spotDao()

    val gear: Flow<List<Gear>> = gearDao.observeAll()
    val sets: Flow<List<SetWithGear>> = setDao.observeWithGear()
    val checklist: Flow<List<ChecklistItem>> = checklistDao.observeItems()
    val basicItems: Flow<List<BasicItem>> = checklistDao.observeBasic()
    val trips: Flow<List<TripWithCatches>> = tripDao.observeWithCatches()
    val spots: Flow<List<Spot>> = spotDao.observeAll()

    /** 初回だけ、いつもの持ち物の例を入れる */
    suspend fun seedIfEmpty() = db.withTransaction {
        if (checklistDao.allBasic().isEmpty() && gearDao.all().isEmpty()) {
            checklistDao.insertBasic(DEFAULT_BASIC_ITEMS.map { BasicItem(name = it) })
        }
    }

    suspend fun getGear(id: Long) = gearDao.get(id)
    suspend fun saveGear(gear: Gear) = gearDao.upsert(gear)
    suspend fun deleteGear(gear: Gear) = gearDao.delete(gear)
    suspend fun addQuantity(id: Long, delta: Int) = gearDao.addQuantity(id, delta)

    /** 魚の数を増減する（0〜99999 に収まる） */
    suspend fun addCatchCount(catchId: Long, delta: Int) = tripDao.addCatchCount(catchId, delta)

    suspend fun getSet(id: Long) = setDao.getWithGear(id)

    suspend fun saveSet(set: TackleSet, gearIds: Set<Long>): Long = db.withTransaction {
        val upserted = setDao.upsert(set)
        // @Upsert は更新のとき -1 を返すため、既存の id を使う
        val id = if (set.id != 0L) set.id else upserted
        setDao.clearGear(id)
        setDao.insertLinks(gearIds.map { SetGear(id, it) })
        id
    }

    suspend fun deleteSet(set: TackleSet) = setDao.delete(set)

    /** チェックリストを、いつもの持ち物とセットの道具で作り直す */
    suspend fun prepareChecklist(setId: Long?) = db.withTransaction {
        val setGear = setId?.let { setDao.getWithGear(it)?.gear }.orEmpty()
        val names = buildChecklistNames(checklistDao.allBasic(), setGear)
        checklistDao.deleteAllItems()
        checklistDao.insertItems(names.mapIndexed { i, n -> ChecklistItem(name = n, sortOrder = i) })
    }

    suspend fun setChecked(id: Long, checked: Boolean) = checklistDao.setChecked(id, checked)
    suspend fun uncheckAll() = checklistDao.uncheckAll()
    suspend fun deleteChecklistItem(id: Long) = checklistDao.deleteItem(id)

    suspend fun addChecklistItem(name: String) = db.withTransaction {
        checklistDao.insertItems(listOf(ChecklistItem(name = name.trim(), sortOrder = checklistDao.maxSortOrder() + 1)))
    }

    suspend fun addBasicItem(name: String) = checklistDao.insertBasic(listOf(BasicItem(name = name.trim())))
    suspend fun deleteBasicItem(id: Long) = checklistDao.deleteBasic(id)

    suspend fun getTrip(id: Long) = tripDao.getWithCatches(id)

    suspend fun saveTrip(trip: Trip, catches: List<CatchEntry>): Long = db.withTransaction {
        val upserted = tripDao.upsert(trip)
        val id = if (trip.id != 0L) trip.id else upserted
        tripDao.clearCatches(id)
        tripDao.insertCatches(catches.map { it.copy(id = 0, tripId = id) })
        id
    }

    suspend fun deleteTrip(trip: Trip) = tripDao.delete(trip)

    /**
     * ワンタップ記録。今日の記録（無ければ作る）に、その魚を1匹足す。
     * 戻り値は「元に戻す」に使う。作った物だけを消し、足した数だけを戻すため。
     */
    suspend fun addQuickCatch(species: String, today: java.time.LocalDate): QuickCatch = db.withTransaction {
        val name = species.trim()
        val existingTrip = tripDao.latestOn(today)
        // 新しく作る記録には、前回使ったセットを入れておく（「セットごとの釣果」がすべて「セットなし」に集まらないように）
        val autoSetId = if (existingTrip == null) tripDao.lastUsedSetId() else null
        val tripId = existingTrip?.id ?: tripDao.upsert(Trip(date = today, setId = autoSetId))
        val existingCatch = tripDao.findCatch(tripId, name)
        val catchId = if (existingCatch != null) {
            tripDao.addCatchCount(existingCatch.id, 1); existingCatch.id
        } else {
            tripDao.insertCatch(CatchEntry(tripId = tripId, species = name, count = 1))
        }
        if (existingTrip == null) quickMadeTrips[tripId] = autoSetId ?: NO_SET
        QuickCatch(tripId, catchId, createdTrip = quickMadeTrips.containsKey(tripId), createdCatch = existingCatch == null, generation = generation)
    }

    /**
     * ワンタップ記録が作った釣行の ID と、そのとき自動で入れたセット。取り消しで消してよいのは、ここにある釣行だけにする
     * （利用者が先に作っておいた釣行は、空でも消さないため）。
     * アプリが終了すると忘れるが、そのときは釣行を残す側に倒れるので安全。
     */
    private val quickMadeTrips = java.util.concurrent.ConcurrentHashMap<Long, Long>()   // 値はセットの ID。セットなしは NO_SET

    /** バックアップを読み込んだ回数。読み込む前に作った「元に戻す」の控えで、読み込んだ記録を変えないために使う */
    @Volatile private var generation = 0L

    /**
     * ワンタップ記録を1匹分だけ戻す。行ごと消すのは、今の数が1のときだけにする
     * （取り消しの前に同じ魚がまた足されていても、その分は消さないため）。
     */
    suspend fun undoQuickCatch(q: QuickCatch) = db.withTransaction {
        if (q.generation != generation) return@withTransaction   // 読み込み前の控えは使わない
        val current = tripDao.getCatch(q.catchId) ?: return@withTransaction
        if (current.count > 1) tripDao.addCatchCount(q.catchId, -1) else tripDao.deleteCatch(q.catchId)
        // 釣果が無くなった記録は、ワンタップで作った物で、場所・メモが空・セットが自動で入れたままの物だけ消す
        // （利用者が先に作った記録や、後から書き足した記録は残す）
        val autoSet = quickMadeTrips[q.tripId]
        if (q.createdTrip && autoSet != null && tripDao.catchCount(q.tripId) == 0) {
            val trip = tripDao.getWithCatches(q.tripId)?.trip
            if (trip != null && trip.place.isBlank() && trip.memo.isBlank() && (trip.setId ?: NO_SET) == autoSet) {
                tripDao.deleteTripById(q.tripId)
                quickMadeTrips.remove(q.tripId)
            }
        }
    }

    /** チェックリストの項目を元に戻す（消した直後の「元に戻す」用） */
    suspend fun restoreChecklistItems(items: List<ChecklistItem>) = checklistDao.insertItems(items)

    /** いつもの持ち物を元に戻す（消した直後の「元に戻す」用） */
    suspend fun restoreBasicItem(item: BasicItem) = checklistDao.insertBasic(listOf(item))

    suspend fun getSpot(id: Long) = spotDao.get(id)
    suspend fun saveSpot(spot: Spot) = spotDao.upsert(spot)
    suspend fun deleteSpot(spot: Spot) = spotDao.delete(spot)

    suspend fun snapshot(): BackupData = db.withTransaction {
        BackupData(
            gear = gearDao.all(),
            sets = setDao.all(),
            setGear = setDao.allLinks(),
            basicItems = checklistDao.allBasic(),
            checklist = checklistDao.allItems(),
            trips = tripDao.allTrips(),
            catches = tripDao.allCatches(),
            spots = spotDao.all(),
        )
    }

    /** 全件を置き換える。途中で失敗したら元のまま残す（トランザクション） */
    suspend fun replaceAll(data: BackupData) {
        // 読み込みの前に、それまでの「元に戻す」の控えをすべて無効にする。
        // 読み込みが失敗して元のデータに戻った場合も無効のままにする（取り消せなくなるだけで、記録は変わらない側に倒す）
        generation++
        quickMadeTrips.clear()
        replaceAllInTransaction(data)
    }

    private suspend fun replaceAllInTransaction(data: BackupData) = db.withTransaction {
        tripDao.deleteAll()          // catch_entry は CASCADE で消える
        setDao.deleteAll()           // set_gear は CASCADE で消える
        gearDao.deleteAll()
        checklistDao.deleteAllItems()
        checklistDao.deleteAllBasic()
        spotDao.deleteAll()
        gearDao.insertAll(data.gear)
        setDao.insertAll(data.sets)
        setDao.insertLinks(data.setGear)
        checklistDao.insertBasic(data.basicItems)
        checklistDao.insertItems(data.checklist)
        tripDao.insertTrips(data.trips)
        tripDao.insertCatches(data.catches)
        spotDao.insertAll(data.spots)
    }
}

/** ワンタップで作った釣行にセットを入れなかった印（ConcurrentHashMap は null を値にできないため） */
private const val NO_SET = -1L
