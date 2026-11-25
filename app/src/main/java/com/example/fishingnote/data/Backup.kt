package com.example.fishingnote.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

data class BackupData(
    val gear: List<Gear>,
    val sets: List<TackleSet>,
    val setGear: List<SetGear>,
    val basicItems: List<BasicItem>,
    val checklist: List<ChecklistItem>,
    val trips: List<Trip>,
    val catches: List<CatchEntry>,
    val spots: List<Spot>,
) {
    /** 置き換える前に利用者へ見せる件数 */
    fun summary(): String =
        "道具 ${gear.size}件・セット ${sets.size}件（中の道具 ${setGear.size}件）・釣行 ${trips.size}件（釣果 ${catches.size}件）・" +
            "釣り場 ${spots.size}件・いつもの持ち物 ${basicItems.size}件・チェックリスト ${checklist.size}件"
}

open class BackupFormatException(message: String) : Exception(message)

/**
 * バックアップの JSON。format と version で、別のファイルや新しすぎる版を読み込まないようにする。
 * 読み込みは全件を検査してから返す（途中まで読んで DB を壊さないため）。
 */
object Backup {
    private const val FORMAT = "fishing-note-backup"
    private const val VERSION = 1

    fun toJson(d: BackupData): String = JSONObject().apply {
        put("format", FORMAT)
        put("version", VERSION)
        put("gear", JSONArray(d.gear.map {
            JSONObject().put("id", it.id).put("name", it.name).put("category", it.category.name)
                .put("brand", it.brand).put("spec", it.spec).put("quantity", it.quantity)
                .putOpt("minQuantity", it.minQuantity).put("owned", it.owned)
                .putOpt("lineChangedOn", it.lineChangedOn?.toString())
                .putOpt("lineReminderDays", it.lineReminderDays).put("memo", it.memo)
        }))
        put("sets", JSONArray(d.sets.map { JSONObject().put("id", it.id).put("name", it.name).put("memo", it.memo) }))
        put("setGear", JSONArray(d.setGear.map { JSONObject().put("setId", it.setId).put("gearId", it.gearId) }))
        put("basicItems", JSONArray(d.basicItems.map { JSONObject().put("id", it.id).put("name", it.name) }))
        put("checklist", JSONArray(d.checklist.map {
            JSONObject().put("id", it.id).put("name", it.name).put("checked", it.checked).put("sortOrder", it.sortOrder)
        }))
        put("trips", JSONArray(d.trips.map {
            JSONObject().put("id", it.id).put("date", it.date.toString()).put("place", it.place)
                .putOpt("setId", it.setId).put("memo", it.memo)
        }))
        put("catches", JSONArray(d.catches.map {
            JSONObject().put("id", it.id).put("tripId", it.tripId).put("species", it.species)
                .putOpt("sizeCm", it.sizeCm).put("count", it.count)
        }))
        put("spots", JSONArray(d.spots.map {
            JSONObject().put("id", it.id).put("name", it.name).put("query", it.query).put("memo", it.memo)
        }))
    }.toString(2)

    fun fromJson(text: String): BackupData {
        // 深く入れ子にした JSON は読み取りの途中でスタックを使い切ることがあるため、それも「読めない」として止める
        val root = try { JSONObject(text) } catch (e: Exception) {
            throw BackupFormatException("バックアップのファイルとして読めません")
        } catch (e: StackOverflowError) {
            throw BackupFormatException("バックアップのファイルとして読めません")
        }
        if (root.optString("format") != FORMAT) throw BackupFormatException("このアプリのバックアップではありません")
        val version = root.optInt("version", -1)
        if (version < 1 || version > VERSION) throw BackupFormatException("対応していない版のバックアップです（版 $version）")
        try {
            val data = BackupData(
                gear = root.objects("gear").map {
                    Gear(
                        id = it.strictLong("id"), name = it.getString("name"),
                        category = GearCategory.valueOf(it.getString("category")),
                        brand = it.optString("brand"), spec = it.optString("spec"),
                        quantity = it.strictInt("quantity"), minQuantity = it.optIntOrNull("minQuantity"),
                        owned = it.getBoolean("owned"),
                        lineChangedOn = it.optStringOrNull("lineChangedOn")?.let(LocalDate::parse),
                        lineReminderDays = it.optIntOrNull("lineReminderDays"), memo = it.optString("memo"),
                    )
                },
                sets = root.objects("sets").map { TackleSet(it.strictLong("id"), it.getString("name"), it.optString("memo")) },
                setGear = root.objects("setGear").map { SetGear(it.strictLong("setId"), it.strictLong("gearId")) },
                basicItems = root.objects("basicItems").map { BasicItem(it.strictLong("id"), it.getString("name")) },
                checklist = root.objects("checklist").map {
                    ChecklistItem(it.strictLong("id"), it.getString("name"), it.getBoolean("checked"), it.strictInt("sortOrder"))
                },
                trips = root.objects("trips").map {
                    Trip(it.strictLong("id"), LocalDate.parse(it.getString("date")), it.optString("place"),
                        it.optLongOrNull("setId"), it.optString("memo"))
                },
                catches = root.objects("catches").map {
                    CatchEntry(it.strictLong("id"), it.strictLong("tripId"), it.getString("species"),
                        it.optDoubleOrNull("sizeCm"), it.strictInt("count"))
                },
                spots = root.objects("spots").map {
                    Spot(it.strictLong("id"), it.getString("name"), it.optString("query"), it.optString("memo"))
                },
            )
            checkValues(data)
            checkReferences(data)
            return data
        } catch (e: BackupFormatException) {
            throw e
        } catch (e: Exception) {
            throw BackupFormatException("バックアップの中身に読めない項目があります")
        }
    }

    /**
     * 読み込むファイルの大きさの上限。これより大きい物は読まずに止める（端末のメモリを使い切らないため）。
     * 書き出すときも同じ上限と検査を通してから書く（書き出せたのに読み戻せないバックアップを作らないため）。
     */
    const val MAX_BYTES = 5 * 1024 * 1024

    /** 最大 limit バイトまで読む（InputStream.readNBytes は Android 13 以降のため使わない） */
    fun readAtMost(input: java.io.InputStream, limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        while (out.size() < limit) {
            val n = input.read(buf, 0, minOf(buf.size, limit - out.size()))
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    /**
     * 値の範囲を確かめる。手で書き換えたファイルや壊れたファイルで、画面では入れられない値
     * （負の数・極端に長い文字・同じ ID の重複など）が入り込まないようにする。
     * 上限は画面の入力の上限より広めにとる（このアプリが書き出した物は必ず通るように）。
     */
    private fun checkValues(d: BackupData) {
        fun fail(what: String): Nothing = throw BackupFormatException("バックアップの「$what」に、範囲外の値があります")
        fun text(s: String, max: Int, what: String) { if (s.length > max) fail(what) }
        fun ids(list: List<Long>, what: String) { if (list.any { it <= 0 } || list.toSet().size != list.size) fail(what) }
        val tables = listOf(d.gear, d.sets, d.setGear, d.basicItems, d.checklist, d.trips, d.catches, d.spots)
        if (tables.any { it.size > MAX_ROWS }) throw BackupFormatException("バックアップの件数が多すぎます")
        ids(d.gear.map { it.id }, "道具"); ids(d.sets.map { it.id }, "セット"); ids(d.basicItems.map { it.id }, "いつもの持ち物")
        ids(d.checklist.map { it.id }, "チェックリスト"); ids(d.trips.map { it.id }, "釣行"); ids(d.catches.map { it.id }, "釣果"); ids(d.spots.map { it.id }, "釣り場")
        if (d.setGear.toSet().size != d.setGear.size) fail("セットの中の道具")
        d.gear.forEach {
            text(it.name, LINE_MAX, "道具"); text(it.brand, LINE_MAX, "道具"); text(it.spec, LINE_MAX, "道具"); text(it.memo, MEMO_MAX, "道具")
            if (it.name.isBlank() || it.quantity !in 0..COUNT_MAX || (it.minQuantity ?: 0) !in 0..COUNT_MAX || (it.lineReminderDays ?: 0) !in 0..DAYS_MAX) fail("道具")
        }
        d.sets.forEach { if (it.name.isBlank()) fail("セット"); text(it.name, LINE_MAX, "セット"); text(it.memo, MEMO_MAX, "セット") }
        d.basicItems.forEach { text(it.name, LINE_MAX, "いつもの持ち物") }
        d.checklist.forEach { text(it.name, LINE_MAX, "チェックリスト"); if (it.sortOrder !in 0..SORT_MAX) fail("チェックリスト") }
        d.trips.forEach { if (it.date.year !in YEAR_RANGE) fail("釣行"); text(it.place, LINE_MAX, "釣行"); text(it.memo, MEMO_MAX, "釣行") }
        d.gear.forEach { g -> g.lineChangedOn?.let { if (it.year !in YEAR_RANGE) fail("道具") } }
        d.catches.forEach {
            text(it.species, LINE_MAX, "釣果")
            if (it.count !in 0..CATCH_MAX || (it.sizeCm ?: 0.0).let { s -> s.isNaN() || s < 0.0 || s > SIZE_MAX_CM }) fail("釣果")
        }
        d.spots.forEach { if (it.name.isBlank()) fail("釣り場"); text(it.name, LINE_MAX, "釣り場"); text(it.query, QUERY_MAX, "釣り場"); text(it.memo, MEMO_MAX, "釣り場") }
    }

    private const val MAX_ROWS = 20_000
    private const val LINE_MAX = 300          // 1行の欄（画面は 40〜100文字。チェックリストは「名前（仕様）」で長くなる）
    private const val MEMO_MAX = 1_000
    private const val QUERY_MAX = 2_000       // 地図の URL を含むため長めにする
    private const val COUNT_MAX = 9_999
    private const val CATCH_MAX = 99_999      // ワンタップで足し続けた数も通す
    private const val DAYS_MAX = 3_650
    private const val SIZE_MAX_CM = 500.0
    private const val SORT_MAX = 1_000_000    // 項目を足すたびに1つずつ増える並び順
    private val YEAR_RANGE = 1900..2100       // 極端な日付で、日付の計算やカレンダーが壊れないように

    /** 参照先の無いつながりがあると、読み込みの途中で外部キーの検査に落ちるため、先に確かめる */
    private fun checkReferences(d: BackupData) {
        val gearIds = d.gear.map { it.id }.toSet()
        val setIds = d.sets.map { it.id }.toSet()
        val tripIds = d.trips.map { it.id }.toSet()
        if (d.setGear.any { it.setId !in setIds || it.gearId !in gearIds } ||
            d.trips.any { it.setId != null && it.setId !in setIds } ||
            d.catches.any { it.tripId !in tripIds }
        ) throw BackupFormatException("バックアップの中のつながりが壊れています")
    }

    /**
     * 表は必ず配列として入っていなければならない。欠けた表を空として受け入れると、
     * 読み込みが「正常に」終わったうえで全データが消えるため、欠けていたら読み込みを止める。
     */
    private fun JSONObject.objects(key: String): List<JSONObject> {
        val arr = optJSONArray(key) ?: throw BackupFormatException("バックアップに「$key」の表がありません")
        return (0 until arr.length()).map { arr.getJSONObject(it) }
    }

    private fun JSONObject.optIntOrNull(key: String) = if (isNull(key)) null else strictInt(key)
    private fun JSONObject.optLongOrNull(key: String) = if (isNull(key)) null else strictLong(key)

    /**
     * 整数として読む。getInt / getLong は範囲外の数や小数を黙って丸める（4294967297 が 1 になる等）ため、
     * 丸めると値が変わる物は読み込みを止める。文字で書かれた数も受け付けない（このアプリは数で書き出す）。
     */
    private fun JSONObject.strictLong(key: String): Long = when (val v = get(key)) {
        is Int -> v.toLong()
        is Long -> v
        is Double -> if (v % 1.0 == 0.0 && v >= -9.0e15 && v <= 9.0e15) v.toLong() else throw BackupFormatException("バックアップの「$key」が整数ではありません")
        else -> throw BackupFormatException("バックアップの「$key」が数ではありません")
    }

    private fun JSONObject.strictInt(key: String): Int {
        val v = strictLong(key)
        if (v < Int.MIN_VALUE || v > Int.MAX_VALUE) throw BackupFormatException("バックアップの「$key」が大きすぎます")
        return v.toInt()
    }
    private fun JSONObject.optDoubleOrNull(key: String) = if (isNull(key)) null else getDouble(key)
    private fun JSONObject.optStringOrNull(key: String) = if (isNull(key)) null else getString(key)
}
