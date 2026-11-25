package com.example.fishingnote.domain

import com.example.fishingnote.data.BasicItem
import com.example.fishingnote.data.Gear
import com.example.fishingnote.data.TackleSet
import com.example.fishingnote.data.TripWithCatches
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** ラインの状態。days は巻き替えてからの日数、due は知らせる日数を超えたか。 */
data class LineStatus(val days: Long, val due: Boolean)

/** 巻き替え日が無い・種類がラインを持たない場合は null。未来の日付は 0 日として扱う。 */
fun lineStatus(gear: Gear, today: LocalDate): LineStatus? {
    if (!gear.category.hasLine) return null
    val changed = gear.lineChangedOn ?: return null
    val days = ChronoUnit.DAYS.between(changed, today).coerceAtLeast(0)
    val limit = gear.lineReminderDays
    return LineStatus(days, limit != null && days > limit)
}

fun isLowStock(gear: Gear): Boolean =
    gear.owned && gear.minQuantity != null && gear.quantity <= gear.minQuantity

enum class GearFilter(val label: String) {
    ALL("すべて"),
    LOW_STOCK("残り少ない"),
    WANTED("欲しい物"),
    LINE_DUE("巻き替え"),
}

/**
 * 検索語は空白で区切り、すべての語が名前・メーカー・仕様・メモのどれかに含まれる道具を残す。
 * 大文字と小文字、全角と半角の英数字は区別しない（釣具店で素早く探すため）。
 */
fun filterGear(items: List<Gear>, query: String, filter: GearFilter, today: LocalDate): List<Gear> {
    val words = normalize(query).split(' ').filter { it.isNotEmpty() }
    return items.filter { g ->
        val text = normalize(listOf(g.name, g.brand, g.spec, g.memo, g.category.label).joinToString(" "))
        val matches = words.all { text.contains(it) }
        val inFilter = when (filter) {
            GearFilter.ALL -> true
            GearFilter.LOW_STOCK -> isLowStock(g)
            GearFilter.WANTED -> !g.owned
            GearFilter.LINE_DUE -> g.owned && lineStatus(g, today)?.due == true
        }
        matches && inFilter
    }
}

fun countByFilter(items: List<Gear>, today: LocalDate): Map<GearFilter, Int> =
    GearFilter.entries.associateWith { f -> filterGear(items, "", f, today).size }

/**
 * 検索用に表記をそろえる。NFKC で全角英数字・半角カナ・全角スペースをそろえ、
 * ひらがなをカタカナに、英字を小文字にする（「じぐ」「ｼﾞｸﾞ」「ジグ」を同じに扱う）。
 */
fun normalize(s: String): String {
    val nfkc = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFKC)
    return buildString(nfkc.length) {
        for (c in nfkc) {
            append(
                when (c) {
                    in 'ぁ'..'ゖ' -> c + 0x60   // ひらがな → カタカナ
                    '　' -> ' '
                    else -> c.lowercaseChar()
                }
            )
        }
    }
}

/**
 * いつもの持ち物とセットの道具から、チェックリストの項目名を作る。
 * 道具は号数・重さも付ける（例：ジグヘッド（1.0g #8））。同じ名前は1つにまとめる。
 */
fun buildChecklistNames(basic: List<BasicItem>, setGear: List<Gear>): List<String> {
    val seen = LinkedHashSet<String>()
    setGear.filter { it.owned }.sortedBy { it.category.ordinal }.forEach { g ->
        val name = g.name.trim()
        seen += if (g.spec.isBlank()) name else "$name（${g.spec.trim()}）"
    }
    basic.forEach { seen += it.name.trim() }
    return seen.filter { it.isNotEmpty() }
}

/** ワンタップ記録のボタンに出す魚。前に記録した魚を多い順に、足りなければよく釣れる魚で埋める */
fun quickSpecies(history: List<String>, limit: Int = 6): List<String> =
    (suggestions(history, limit) + COMMON_SPECIES).distinct().take(limit)

val COMMON_SPECIES = listOf("アジ", "サバ", "イワシ", "メバル", "カサゴ", "シーバス")

data class SpeciesTotal(val species: String, val count: Int, val maxSizeCm: Double?)
data class SetTotal(val setName: String, val trips: Int, val fish: Int)

data class YearSummary(
    val year: Int,
    val tripCount: Int,
    val fishCount: Int,
    val topSpecies: List<SpeciesTotal>,
    val bySet: List<SetTotal>,
    /** 今年いちばん大きかった魚（大きさの記録が無ければ null） */
    val biggest: SpeciesTotal? = null,
)

/** 指定した年の集計。魚の種類は前後の空白を除いた名前でまとめる。 */
fun summarize(trips: List<TripWithCatches>, sets: List<TackleSet>, year: Int): YearSummary {
    val inYear = trips.filter { it.trip.date.year == year }
    val catches = inYear.flatMap { it.catches }
    val species = catches
        .groupBy { it.species.trim() }
        .filterKeys { it.isNotEmpty() }
        .map { (name, list) -> SpeciesTotal(name, list.sumOf { it.count }, list.mapNotNull { it.sizeCm }.maxOrNull()) }
        .sortedWith(compareByDescending<SpeciesTotal> { it.count }.thenBy { it.species })
    // セットは ID でまとめる（名前でまとめると、同じ名前の別のセットが合算されるため）。消したセットは「セットなし」に入る
    val setNames = sets.associate { it.id to it.name }
    val bySet = inYear
        .groupBy { it.trip.setId?.takeIf { id -> id in setNames } }
        .map { (id, list) -> SetTotal(id?.let(setNames::getValue) ?: "セットなし", list.size, list.sumOf { t -> t.catches.sumOf { it.count } }) }
        .sortedWith(compareByDescending<SetTotal> { it.fish }.thenBy { it.setName })
    val biggest = catches.filter { it.sizeCm != null && it.species.isNotBlank() }.maxByOrNull { it.sizeCm!! }
        ?.let { SpeciesTotal(it.species.trim(), it.count, it.sizeCm) }
    return YearSummary(year, inYear.size, catches.sumOf { it.count }, species.take(3), bySet, biggest)
}

/** 1回の釣行の釣果を短い文にする（例：アジ 12匹（最大 22cm）、サバ 3匹） */
fun catchLine(trip: TripWithCatches): String {
    if (trip.catches.isEmpty()) return "釣果なし"
    return trip.catches
        .groupBy { it.species.trim() }
        .map { (name, list) ->
            val max = list.mapNotNull { it.sizeCm }.maxOrNull()
            "$name ${list.sumOf { it.count }}匹" + (max?.let { "（最大 ${formatCm(it)}cm）" } ?: "")
        }
        .joinToString("、")
}

fun formatCm(v: Double): String = if (v % 1.0 == 0.0) v.toLong().toString() else "%.1f".format(v)

/** 前に入力した値を、多く使った順に返す（入力の手間を減らす候補） */
fun suggestions(values: List<String>, limit: Int = 8): List<String> =
    values.map { it.trim() }.filter { it.isNotEmpty() }
        .groupingBy { it }.eachCount()
        .entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .take(limit).map { it.key }

/** 地図アプリで探すときの検索語。緯度経度なら名前をラベルに付ける。 */
fun mapQuery(name: String, query: String): String {
    val q = query.trim().ifEmpty { name.trim() }
    if (isMapUrl(q)) return q
    val latLng = Regex("""^\s*(-?\d{1,2}(?:\.\d+)?)\s*,\s*(-?\d{1,3}(?:\.\d+)?)\s*$""").matchEntire(q)
    return if (latLng != null) {
        val (lat, lng) = latLng.destructured
        "$lat,$lng(${name.trim().ifEmpty { "釣り場" }})"
    } else q
}

/**
 * 現在地の近くを地図アプリで探すための geo: URI。
 * 座標は小数4桁（約10m）に丸める。小数点がカンマになる地域設定でも崩れないよう、書式は Locale.US で作る。
 */
fun nearbyGeoUri(lat: Double, lng: Double, query: String): String =
    String.format(java.util.Locale.US, "geo:%.4f,%.4f?z=13&q=", lat, lng) +
        java.net.URLEncoder.encode(query, "UTF-8").replace("+", "%20")

val NEARBY_SEARCHES = listOf("釣り公園", "海釣り施設", "漁港", "釣り桟橋", "管理釣り場", "釣具店", "釣り船")

/** 初めて起動したときに入れる「いつもの持ち物」 */
val DEFAULT_BASIC_ITEMS = listOf(
    "ライフジャケット", "フィッシュグリップ", "プライヤー", "ハサミ", "タオル", "クーラーボックス", "飲み物", "スマホの充電",
)

/** 「名前（号数）」を名前と号数に分ける（チェックリストで号数を2行目に小さく出すため） */
fun splitSpec(label: String): Pair<String, String?> {
    val t = label.trim()
    val open = t.lastIndexOf('（')
    return if (t.endsWith('）') && open > 0) t.substring(0, open).trim() to t.substring(open + 1, t.length - 1).trim() else t to null
}

/** 月ごとの釣れた数（1月〜12月）。記録の画面の棒グラフに使う */
fun monthlyFish(trips: List<TripWithCatches>, year: Int): List<Int> {
    val counts = IntArray(12)
    trips.filter { it.trip.date.year == year }.forEach { t -> counts[t.trip.date.monthValue - 1] += t.catches.sumOf { it.count } }
    return counts.toList()
}

/** 時間帯のあいさつ */
fun greeting(hour: Int): String = when (hour) {
    in 4..10 -> "おはようございます"
    in 11..16 -> "こんにちは"
    else -> "こんばんは"
}

/**
 * 地図の URL として受け付けるもの。共有で届いた文には別のサイトの URL が混ざることがあるため、
 * URL を分解して、https で、ホスト名が下の一覧と完全に一致し、決まったパスで始まる物だけを地図として開く。
 * （正規表現でホスト名を「らしく」照合すると、google.evil のような別のサイトを通してしまうため）
 */
private val MAP_HOSTS = mapOf(
    "maps.app.goo.gl" to "/",
    "goo.gl" to "/maps",
    "maps.google.com" to "/",
    "maps.google.co.jp" to "/",
    "google.com" to "/maps",
    "www.google.com" to "/maps",
    "google.co.jp" to "/maps",
    "www.google.co.jp" to "/maps",
)

private val URL_IN_TEXT = Regex("""https://[^\s]+""")
private val INVISIBLE = Regex("[\u200B-\u200F\u202A-\u202E\u2060-\u2069\uFEFF]")

fun isMapUrl(text: String): Boolean {
    val t = text.trim()
    if (t.isEmpty() || t.any { it.isWhitespace() }) return false
    val uri = try { java.net.URI(t) } catch (e: Exception) { return false }
    if (uri.scheme != "https" || uri.userInfo != null || (uri.port != -1 && uri.port != 443)) return false
    val prefix = MAP_HOSTS[uri.host?.lowercase() ?: return false] ?: return false
    val path = uri.rawPath ?: ""
    // 「/maps/../url」のように、開く側でたどり直すと別の場所になるパスは受け付けない（エンコードした . と / も同じ）
    val lower = path.lowercase()
    if (".." in path || "%2e" in lower || "%2f" in lower || "%5c" in lower || '\\' in path) return false
    // 「/mapsX」のように、決まったパスの続きに文字が付いた物も受け付けない
    return prefix == "/" || path == prefix || path.startsWith("$prefix/")
}

/**
 * 地図アプリの「共有」で届いた文から、釣り場の名前と地図で開く値を取り出す。
 * 名前は URL を含まない最初の行。地図で開く値は Google マップの URL、無ければ住所などの文。
 */
fun parseSharedPlace(text: String): Pair<String, String> {
    val url = URL_IN_TEXT.findAll(text).map { it.value }.firstOrNull(::isMapUrl)
    // URL の部分を除いた残りを行ごとに見る（「名前 URL」が1行に並んでいても名前を取り出す）
    // 見えない文字（ゼロ幅の文字・文字の向きを変える制御文字）は取り除く（表示を偽装されないように）
    val clean = text.replace(INVISIBLE, "")
    val lines = clean.lines().map { it.replace(Regex("""https?://\S+"""), "").trim() }.filter { it.isNotEmpty() }
    val name = lines.firstOrNull()?.take(40) ?: "共有された場所"
    return name to (url ?: lines.joinToString(" ").take(100))
}

/**
 * 「メーカー・商品名」の候補に出す、主な釣り具のメーカー名。
 * 名前だけを持ち、商品の情報（型番・仕様）は持たない（古くなるため。商品は「ネットで調べる」で探す）
 */
val KNOWN_MAKERS = listOf(
    "シマノ", "ダイワ", "がまかつ", "アブ・ガルシア", "メジャークラフト", "ヤマガブランクス", "テイルウォーク", "オリムピック",
    "天龍", "ジャッカル", "メガバス", "エバーグリーン", "デュオ", "ジャクソン", "ダイコー", "プロックス", "宇崎日新",
    "サンライン", "よつあみ", "バリバス", "クレハ", "東レ", "ゴーセン", "デュエル", "ヤマシタ", "ハヤブサ", "ささめ針",
    "オーナーばり", "第一精工", "メイホウ", "プロマリン", "ルミカ", "富士工業",
)

/**
 * 「メーカー・商品名」に途中まで入れた言葉から、候補を返す。
 * 前に登録した値を先に、主なメーカー名を後に並べ、それぞれの中で「言葉で始まる物」を先にする（同じ扱いの中は元の順）。
 * 文字の比べ方は一覧の検索と同じ（大文字と小文字・全角と半角・ひらがなとカタカナを区別しない）。ただし語に分けず、入れた文字列のまま含むかで見る。入れた物と同じ値は出さない
 */
fun brandSuggestions(input: String, past: List<String>, makers: List<String> = KNOWN_MAKERS, limit: Int = 6): List<String> {
    val q = normalize(input).trim()
    if (q.isEmpty()) return emptyList()
    val seen = HashSet<String>()
    fun hits(list: List<String>): List<String> {
        val found = list.map { it.trim() }.filter { it.isNotEmpty() && seen.add(normalize(it)) }
            .filter { val n = normalize(it); n.contains(q) && n != q }
        return found.filter { normalize(it).startsWith(q) } + found.filterNot { normalize(it).startsWith(q) }
    }
    // 前に登録した値を全部並べてから、メーカー名を並べる（前に登録した値が上限まで当たれば、メーカー名は出ない）
    return (hits(past) + hits(makers)).take(limit)
}

/** ネットで調べるときの検索語（メーカー・商品名と名前をつなぐ。同じ言葉は重ねない） */
fun gearSearchQuery(brand: String, name: String): String {
    val b = brand.trim()
    val n = name.trim()
    return when {
        b.isEmpty() -> n
        n.isEmpty() || normalize(b).contains(normalize(n)) -> b
        normalize(n).contains(normalize(b)) -> n
        else -> "$b $n"
    }
}
