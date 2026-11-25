package com.example.fishingnote.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Relation
import java.time.LocalDate

/** 道具の種類。label は画面に出す名前、hasLine はラインの巻き替え日を記録する種類か。 */
enum class GearCategory(val label: String, val hasLine: Boolean) {
    ROD("ロッド", false),
    REEL("リール", true),
    LINE("ライン", true),
    LURE("ルアー・エサ", false),
    TERMINAL("小物・仕掛け", false),
    OTHER("その他", false),
}

@Entity(tableName = "gear")
data class Gear(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: GearCategory,
    val brand: String = "",
    /** 号数・長さ・重さなど、忘れやすい仕様（例：PE0.8号 150m） */
    val spec: String = "",
    val quantity: Int = 1,
    /** 残りがこの数以下になったら「残りが少ない」に出す。null なら知らせない */
    val minQuantity: Int? = null,
    /** false のときは「欲しい物」（まだ持っていない） */
    val owned: Boolean = true,
    /** ラインを巻き替えた日（リール・ラインだけ使う） */
    val lineChangedOn: LocalDate? = null,
    /** 巻き替えてからこの日数を超えたら知らせる。null なら知らせない */
    val lineReminderDays: Int? = null,
    val memo: String = "",
)

@Entity(tableName = "tackle_set")
data class TackleSet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val memo: String = "",
)

@Entity(
    tableName = "set_gear",
    primaryKeys = ["setId", "gearId"],
    foreignKeys = [
        ForeignKey(TackleSet::class, ["id"], ["setId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(Gear::class, ["id"], ["gearId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("gearId")],
)
data class SetGear(val setId: Long, val gearId: Long)

data class SetWithGear(
    @Embedded val set: TackleSet,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(SetGear::class, parentColumn = "setId", entityColumn = "gearId"),
    )
    val gear: List<Gear>,
)

/** いつもの持ち物（どのセットでも持っていく物） */
@Entity(tableName = "basic_item")
data class BasicItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

/** 今回の持ち物チェックリストの1行 */
@Entity(tableName = "checklist_item")
data class ChecklistItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val checked: Boolean = false,
    val sortOrder: Int = 0,
)

@Entity(
    tableName = "trip",
    foreignKeys = [
        ForeignKey(TackleSet::class, ["id"], ["setId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("setId")],
)
data class Trip(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val place: String = "",
    val setId: Long? = null,
    val memo: String = "",
)

@Entity(
    tableName = "catch_entry",
    foreignKeys = [
        ForeignKey(Trip::class, ["id"], ["tripId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("tripId")],
)
data class CatchEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val species: String,
    val sizeCm: Double? = null,
    val count: Int = 1,
)

data class TripWithCatches(
    @Embedded val trip: Trip,
    @Relation(parentColumn = "id", entityColumn = "tripId")
    val catches: List<CatchEntry>,
)

/** ワンタップ記録の結果。「元に戻す」で、作った物だけを消すために使う */
/** ワンタップ記録の「元に戻す」に使う控え。generation はバックアップを読み込むたびに変わり、読み込み前の控えを無効にする */
data class QuickCatch(val tripId: Long, val catchId: Long, val createdTrip: Boolean, val createdCatch: Boolean, val generation: Long = 0)

/** マイ釣り場。query は地図で探すときの住所・施設名・緯度経度（例：35.6,139.7） */
@Entity(tableName = "spot")
data class Spot(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val query: String = "",
    val memo: String = "",
)
