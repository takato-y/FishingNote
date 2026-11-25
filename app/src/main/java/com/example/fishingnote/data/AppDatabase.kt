package com.example.fishingnote.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import java.time.LocalDate

/** 日付は epoch day、種類は名前の文字列で保存する（並び順・読みやすさのため） */
class Converters {
    @TypeConverter fun dateToLong(date: LocalDate?): Long? = date?.toEpochDay()
    @TypeConverter fun longToDate(value: Long?): LocalDate? = value?.let(LocalDate::ofEpochDay)
    @TypeConverter fun categoryToString(category: GearCategory): String = category.name
    @TypeConverter fun stringToCategory(value: String): GearCategory = GearCategory.valueOf(value)
}

@Database(
    entities = [
        Gear::class, TackleSet::class, SetGear::class, BasicItem::class,
        ChecklistItem::class, Trip::class, CatchEntry::class, Spot::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun gearDao(): GearDao
    abstract fun setDao(): SetDao
    abstract fun checklistDao(): ChecklistDao
    abstract fun tripDao(): TripDao
    abstract fun spotDao(): SpotDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "fishing_note.db").build()
    }
}
