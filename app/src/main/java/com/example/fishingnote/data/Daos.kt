package com.example.fishingnote.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface GearDao {
    @Query("SELECT * FROM gear ORDER BY category, name")
    fun observeAll(): Flow<List<Gear>>

    @Query("SELECT * FROM gear ORDER BY id")
    suspend fun all(): List<Gear>

    @Query("SELECT * FROM gear WHERE id = :id")
    suspend fun get(id: Long): Gear?

    @Upsert
    suspend fun upsert(gear: Gear): Long

    @Insert
    suspend fun insertAll(items: List<Gear>)

    @Delete
    suspend fun delete(gear: Gear)

    // 0〜9999 に収める（バックアップの読み込みの上限と同じ。超えると書き出した物を読み戻せなくなるため）
    @Query("UPDATE gear SET quantity = MIN(9999, MAX(0, quantity + :delta)) WHERE id = :id")
    suspend fun addQuantity(id: Long, delta: Int)

    @Query("DELETE FROM gear")
    suspend fun deleteAll()
}

@Dao
interface SetDao {
    @Transaction
    @Query("SELECT * FROM tackle_set ORDER BY name")
    fun observeWithGear(): Flow<List<SetWithGear>>

    @Transaction
    @Query("SELECT * FROM tackle_set WHERE id = :id")
    suspend fun getWithGear(id: Long): SetWithGear?

    @Query("SELECT * FROM tackle_set ORDER BY id")
    suspend fun all(): List<TackleSet>

    @Query("SELECT * FROM set_gear")
    suspend fun allLinks(): List<SetGear>

    @Upsert
    suspend fun upsert(set: TackleSet): Long

    @Insert
    suspend fun insertAll(items: List<TackleSet>)

    @Delete
    suspend fun delete(set: TackleSet)

    @Query("DELETE FROM set_gear WHERE setId = :setId")
    suspend fun clearGear(setId: Long)

    @Insert
    suspend fun insertLinks(links: List<SetGear>)

    @Query("DELETE FROM tackle_set")
    suspend fun deleteAll()
}

@Dao
interface ChecklistDao {
    @Query("SELECT * FROM checklist_item ORDER BY sortOrder, id")
    fun observeItems(): Flow<List<ChecklistItem>>

    @Query("SELECT * FROM checklist_item ORDER BY sortOrder, id")
    suspend fun allItems(): List<ChecklistItem>

    @Insert
    suspend fun insertItems(items: List<ChecklistItem>)

    @Query("UPDATE checklist_item SET checked = :checked WHERE id = :id")
    suspend fun setChecked(id: Long, checked: Boolean)

    @Query("UPDATE checklist_item SET checked = 0")
    suspend fun uncheckAll()

    @Query("DELETE FROM checklist_item WHERE id = :id")
    suspend fun deleteItem(id: Long)

    @Query("DELETE FROM checklist_item")
    suspend fun deleteAllItems()

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM checklist_item")
    suspend fun maxSortOrder(): Int

    @Query("SELECT * FROM basic_item ORDER BY id")
    fun observeBasic(): Flow<List<BasicItem>>

    @Query("SELECT * FROM basic_item ORDER BY id")
    suspend fun allBasic(): List<BasicItem>

    @Insert
    suspend fun insertBasic(items: List<BasicItem>)

    @Query("DELETE FROM basic_item WHERE id = :id")
    suspend fun deleteBasic(id: Long)

    @Query("DELETE FROM basic_item")
    suspend fun deleteAllBasic()
}

@Dao
interface TripDao {
    @Transaction
    @Query("SELECT * FROM trip ORDER BY date DESC, id DESC")
    fun observeWithCatches(): Flow<List<TripWithCatches>>

    @Transaction
    @Query("SELECT * FROM trip WHERE id = :id")
    suspend fun getWithCatches(id: Long): TripWithCatches?

    @Query("SELECT * FROM trip ORDER BY id")
    suspend fun allTrips(): List<Trip>

    @Query("SELECT * FROM catch_entry ORDER BY id")
    suspend fun allCatches(): List<CatchEntry>

    @Upsert
    suspend fun upsert(trip: Trip): Long

    @Insert
    suspend fun insertTrips(items: List<Trip>)

    @Delete
    suspend fun delete(trip: Trip)

    @Query("DELETE FROM catch_entry WHERE tripId = :tripId")
    suspend fun clearCatches(tripId: Long)

    @Insert
    suspend fun insertCatches(items: List<CatchEntry>)

    @Query("DELETE FROM trip")
    suspend fun deleteAll()

    @Query("SELECT * FROM trip WHERE date = :date ORDER BY id DESC LIMIT 1")
    suspend fun latestOn(date: java.time.LocalDate): Trip?

    /** 最後にセットを入れた釣行のセット（一覧と同じ、新しい順で探す） */
    @Query("SELECT setId FROM trip WHERE setId IS NOT NULL ORDER BY date DESC, id DESC LIMIT 1")
    suspend fun lastUsedSetId(): Long?

    @Query("SELECT * FROM catch_entry WHERE tripId = :tripId AND species = :species ORDER BY id LIMIT 1")
    suspend fun findCatch(tripId: Long, species: String): CatchEntry?

    @Insert
    suspend fun insertCatch(item: CatchEntry): Long

    @Query("SELECT * FROM catch_entry WHERE id = :id")
    suspend fun getCatch(id: Long): CatchEntry?

    // 魚の数も 0〜99999 に収める（バックアップの読み込みの範囲と同じ）
    @Query("UPDATE catch_entry SET count = MIN(99999, MAX(0, count + :delta)) WHERE id = :id")
    suspend fun addCatchCount(id: Long, delta: Int)

    @Query("DELETE FROM catch_entry WHERE id = :id")
    suspend fun deleteCatch(id: Long)

    @Query("SELECT COUNT(*) FROM catch_entry WHERE tripId = :tripId")
    suspend fun catchCount(tripId: Long): Int

    @Query("DELETE FROM trip WHERE id = :id")
    suspend fun deleteTripById(id: Long)
}

@Dao
interface SpotDao {
    @Query("SELECT * FROM spot ORDER BY name")
    fun observeAll(): Flow<List<Spot>>

    @Query("SELECT * FROM spot ORDER BY id")
    suspend fun all(): List<Spot>

    @Query("SELECT * FROM spot WHERE id = :id")
    suspend fun get(id: Long): Spot?

    @Upsert
    suspend fun upsert(spot: Spot): Long

    @Insert
    suspend fun insertAll(items: List<Spot>)

    @Delete
    suspend fun delete(spot: Spot)

    @Query("DELETE FROM spot")
    suspend fun deleteAll()
}
