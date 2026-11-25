package com.example.fishingnote.domain

import com.example.fishingnote.data.BasicItem
import com.example.fishingnote.data.CatchEntry
import com.example.fishingnote.data.Gear
import com.example.fishingnote.data.GearCategory
import com.example.fishingnote.data.TackleSet
import com.example.fishingnote.data.Trip
import com.example.fishingnote.data.TripWithCatches
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RulesTest {
    private val today = LocalDate.of(2026, 11, 20)
    private fun gear(name: String, category: GearCategory = GearCategory.LURE, id: Long = 0, owned: Boolean = true) =
        Gear(id = id, name = name, category = category, owned = owned)

    // --- ラインの状態 ---
    @Test fun line_status_is_null_for_category_without_line() {
        assertNull(lineStatus(gear("竿", GearCategory.ROD).copy(lineChangedOn = today), today))
    }

    @Test fun line_status_is_null_without_change_date() {
        assertNull(lineStatus(gear("リール", GearCategory.REEL), today))
    }

    @Test fun line_is_due_only_after_reminder_days() {
        val g = gear("リール", GearCategory.REEL).copy(lineReminderDays = 30)
        assertEquals(LineStatus(30, false), lineStatus(g.copy(lineChangedOn = today.minusDays(30)), today))
        assertEquals(LineStatus(31, true), lineStatus(g.copy(lineChangedOn = today.minusDays(31)), today))
    }

    @Test fun line_is_never_due_without_reminder() {
        val g = gear("ライン", GearCategory.LINE).copy(lineChangedOn = today.minusDays(1000))
        assertEquals(LineStatus(1000, false), lineStatus(g, today))
    }

    @Test fun future_change_date_counts_as_zero_days() {
        val g = gear("ライン", GearCategory.LINE).copy(lineChangedOn = today.plusDays(3), lineReminderDays = 0)
        assertEquals(LineStatus(0, false), lineStatus(g, today))
    }

    // --- 残りの少なさ ---
    @Test fun low_stock_when_quantity_at_or_below_min() {
        val g = gear("スナップ", GearCategory.TERMINAL).copy(minQuantity = 3)
        assertTrue(isLowStock(g.copy(quantity = 3)))
        assertFalse(isLowStock(g.copy(quantity = 4)))
    }

    @Test fun no_low_stock_without_min_or_when_wanted() {
        assertFalse(isLowStock(gear("ジグ").copy(quantity = 0)))
        assertFalse(isLowStock(gear("ジグ", owned = false).copy(quantity = 0, minQuantity = 1)))
    }

    // --- 検索と絞り込み ---
    @Test fun search_matches_all_words_across_fields_ignoring_width_and_case() {
        val items = listOf(
            gear("メタルジグ", id = 1).copy(spec = "30g ピンク", brand = "ABC"),
            gear("メタルジグ", id = 2).copy(spec = "40g"),
            gear("ミノー", id = 3).copy(spec = "30g"),
        )
        assertEquals(listOf(1L), filterGear(items, "ジグ　３０Ｇ", GearFilter.ALL, today).map { it.id })
        assertEquals(listOf(1L), filterGear(items, "abc", GearFilter.ALL, today).map { it.id })
        assertEquals(3, filterGear(items, "  ", GearFilter.ALL, today).size)
    }

    @Test fun search_treats_hiragana_and_halfwidth_kana_as_katakana() {
        val items = listOf(gear("メタルジグ", id = 1), gear("ミノー", id = 2))
        assertEquals(listOf(1L), filterGear(items, "じぐ", GearFilter.ALL, today).map { it.id })
        assertEquals(listOf(1L), filterGear(items, "ｼﾞｸﾞ", GearFilter.ALL, today).map { it.id })
    }

    @Test fun shared_text_from_maps_gives_name_and_map_url() {
        val (name, query) = parseSharedPlace("見本漁港 北堤防\n見本県見本市1-2\nhttps://maps.app.goo.gl/abc123")
        assertEquals("見本漁港 北堤防", name)
        assertEquals("https://maps.app.goo.gl/abc123", query)
        assertEquals("https://maps.app.goo.gl/abc123", mapQuery(name, query))
    }

    @Test fun shared_single_line_keeps_name_before_url() {
        assertEquals("Sample Pier" to "https://maps.app.goo.gl/abc123", parseSharedPlace("Sample Pier https://maps.app.goo.gl/abc123"))
    }

    @Test fun non_map_urls_are_not_treated_as_map() {
        val (name, query) = parseSharedPlace("おすすめ\nhttps://example.com/maps/evil")
        assertEquals("おすすめ", name)
        assertEquals("おすすめ", query)
        assertFalse(isMapUrl("https://example.com/maps/evil"))
        assertFalse(isMapUrl("https://maps.app.goo.gl.example.com/x"))
        assertTrue(isMapUrl("https://www.google.com/maps/place/x"))
    }

    @Test fun map_url_check_rejects_lookalike_hosts_and_tricks() {
        for (bad in listOf(
            "https://google.evil/maps/abc",
            "https://maps.google.evil/place",
            "https://www.google.com.evil.example/maps",
            "https://evil.example/?u=https://maps.app.goo.gl/x",
            "http://maps.app.goo.gl/abc",
            "https://user@maps.app.goo.gl/abc",
            "https://maps.app.goo.gl:8443/abc",
            "https://www.google.com/search?q=maps",
            "https://goo.gl/abc",
            // 開く側でたどり直すと別の場所になるパス・決まったパスの続きに文字が付いた物
            "https://www.google.com/maps/../url?q=https://evil.example",
            "https://www.google.com/maps/%2e%2e/url",
            "https://www.google.com/maps%2F..%2Furl",
            "https://www.google.com/mapsX",
            "https://goo.gl/mapsevil",
        )) assertFalse(bad, isMapUrl(bad))
        for (ok in listOf("https://maps.app.goo.gl/abc", "https://www.google.com/maps/place/x", "https://maps.google.co.jp/?q=1", "https://goo.gl/maps/abc", "https://www.google.com/maps?q=1")) {
            assertTrue(ok, isMapUrl(ok))
        }
    }

    @Test fun shared_text_uses_only_the_map_url_among_several() {
        val (_, query) = parseSharedPlace("北堤防 https://google.evil/maps/x https://maps.app.goo.gl/real")
        assertEquals("https://maps.app.goo.gl/real", query)
    }

    @Test fun split_spec_separates_trailing_parentheses() {
        assertEquals("ジグヘッド" to "1.0g #8", splitSpec("ジグヘッド（1.0g #8）"))
        assertEquals("タオル" to null, splitSpec("タオル"))
    }

    @Test fun monthly_fish_counts_only_the_year() {
        val trips = listOf(
            TripWithCatches(Trip(id = 1, date = LocalDate.of(2026, 3, 1)), listOf(CatchEntry(tripId = 1, species = "アジ", count = 4))),
            TripWithCatches(Trip(id = 2, date = LocalDate.of(2025, 3, 1)), listOf(CatchEntry(tripId = 2, species = "アジ", count = 9))),
        )
        val m = monthlyFish(trips, 2026)
        assertEquals(4, m[2])
        assertEquals(4, m.sum())
    }

    @Test fun quick_species_fills_with_common_fish_without_duplicates() {
        assertEquals(listOf("タチウオ", "アジ", "サバ", "イワシ", "メバル", "カサゴ"), quickSpecies(listOf("タチウオ", "タチウオ", "アジ")))
    }

    @Test fun checklist_shows_spec_of_gear() {
        val names = buildChecklistNames(emptyList(), listOf(gear("ジグヘッド").copy(spec = "1.0g #8")))
        assertEquals(listOf("ジグヘッド（1.0g #8）"), names)
    }

    @Test fun filters_select_expected_gear() {
        val items = listOf(
            gear("スナップ", GearCategory.TERMINAL, id = 1).copy(quantity = 1, minQuantity = 2),
            gear("新しいリール", GearCategory.REEL, id = 2, owned = false),
            gear("リール", GearCategory.REEL, id = 3).copy(lineChangedOn = today.minusDays(100), lineReminderDays = 90),
            gear("欲しいリール巻き替え", GearCategory.REEL, id = 4, owned = false).copy(lineChangedOn = today.minusDays(100), lineReminderDays = 90),
        )
        assertEquals(listOf(1L), filterGear(items, "", GearFilter.LOW_STOCK, today).map { it.id })
        assertEquals(listOf(2L, 4L), filterGear(items, "", GearFilter.WANTED, today).map { it.id })
        assertEquals(listOf(3L), filterGear(items, "", GearFilter.LINE_DUE, today).map { it.id })
        assertEquals(mapOf(GearFilter.ALL to 4, GearFilter.LOW_STOCK to 1, GearFilter.WANTED to 2, GearFilter.LINE_DUE to 1), countByFilter(items, today))
    }

    // --- チェックリスト ---
    @Test fun checklist_puts_set_gear_first_by_category_then_basic_without_duplicates() {
        val names = buildChecklistNames(
            basic = listOf(BasicItem(name = "タオル"), BasicItem(name = "プライヤー"), BasicItem(name = " ")),
            setGear = listOf(
                gear("ジグ", GearCategory.LURE),
                gear("ロッド", GearCategory.ROD),
                gear("プライヤー", GearCategory.TERMINAL),
                gear("買う予定の竿", GearCategory.ROD, owned = false),
            ),
        )
        assertEquals(listOf("ロッド", "ジグ", "プライヤー", "タオル"), names)
    }

    // --- 集計 ---
    private fun trip(id: Long, date: LocalDate, setId: Long?, vararg catches: CatchEntry) =
        TripWithCatches(Trip(id = id, date = date, setId = setId), catches.toList())

    @Test fun summary_counts_only_the_year_and_ranks_species() {
        val sets = listOf(TackleSet(id = 1, name = "アジング"))
        val trips = listOf(
            trip(1, LocalDate.of(2026, 5, 1), 1, CatchEntry(tripId = 1, species = "アジ", sizeCm = 20.0, count = 10)),
            trip(2, LocalDate.of(2026, 6, 1), 1, CatchEntry(tripId = 2, species = "アジ ", sizeCm = 22.5, count = 5), CatchEntry(tripId = 2, species = "サバ", count = 2)),
            trip(3, LocalDate.of(2026, 7, 1), null),
            trip(4, LocalDate.of(2025, 7, 1), 1, CatchEntry(tripId = 4, species = "タイ", count = 1)),
        )
        val s = summarize(trips, sets, 2026)
        assertEquals(3, s.tripCount)
        assertEquals(17, s.fishCount)
        assertEquals(listOf(SpeciesTotal("アジ", 15, 22.5), SpeciesTotal("サバ", 2, null)), s.topSpecies)
        assertEquals(listOf(SetTotal("アジング", 2, 17), SetTotal("セットなし", 1, 0)), s.bySet)
    }

    @Test fun summary_reports_biggest_fish_of_the_year() {
        val trips = listOf(
            trip(1, LocalDate.of(2026, 5, 1), null, CatchEntry(tripId = 1, species = "アジ", sizeCm = 22.5, count = 3), CatchEntry(tripId = 1, species = "サバ", sizeCm = 32.0, count = 1)),
            trip(2, LocalDate.of(2025, 5, 1), null, CatchEntry(tripId = 2, species = "タイ", sizeCm = 50.0, count = 1)),
        )
        assertEquals(SpeciesTotal("サバ", 1, 32.0), summarize(trips, emptyList(), 2026).biggest)
        assertEquals(null, summarize(listOf(trip(3, today, null)), emptyList(), 2026).biggest)
    }

    @Test fun summary_keeps_sets_with_same_name_apart() {
        val sets = listOf(TackleSet(id = 1, name = "アジング"), TackleSet(id = 2, name = "アジング"))
        val s = summarize(listOf(trip(1, today, 1), trip(2, today, 2)), sets, 2026)
        assertEquals(2, s.bySet.size)
    }

    @Test fun summary_for_deleted_set_falls_back_to_no_set() {
        val s = summarize(listOf(trip(1, today, 99)), emptyList(), 2026)
        assertEquals(listOf(SetTotal("セットなし", 1, 0)), s.bySet)
    }

    @Test fun catch_line_merges_same_species() {
        val t = trip(1, today, null,
            CatchEntry(tripId = 1, species = "アジ", sizeCm = 18.0, count = 3),
            CatchEntry(tripId = 1, species = "アジ", sizeCm = 21.5, count = 2),
            CatchEntry(tripId = 1, species = "サバ", count = 1))
        assertEquals("アジ 5匹（最大 21.5cm）、サバ 1匹", catchLine(t))
        assertEquals("釣果なし", catchLine(trip(2, today, null)))
    }

    // --- 入力の候補と地図 ---
    @Test fun suggestions_rank_by_frequency_and_drop_blank() {
        assertEquals(listOf("A港", "B浜"), suggestions(listOf("B浜", "A港", " A港 ", "", "  ")))
    }

    @Test fun map_query_labels_lat_lng_and_falls_back_to_name() {
        assertEquals("35.3,139.5(北堤防)", mapQuery("北堤防", " 35.3, 139.5 "))
        assertEquals("○○漁港", mapQuery("北堤防", "○○漁港"))
        assertEquals("北堤防", mapQuery("北堤防", ""))
    }

    @Test fun nearbyGeoUri_passesRoundedCoordsAndEncodedQuery() {
        assertEquals("geo:35.6180,139.8360?z=13&q=%E9%87%A3%E3%82%8A%E5%85%AC%E5%9C%92", nearbyGeoUri(35.618, 139.836, "釣り公園"))
        // 南半球・西経（負の値）と、空白を含む検索語
        assertEquals("geo:-33.8688,-151.2093?z=13&q=fishing%20pier", nearbyGeoUri(-33.86882, -151.20929, "fishing pier"))
    }

    @Test fun nearbyGeoUri_ignoresDeviceDecimalComma() {
        val saved = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.GERMANY)
            assertEquals("geo:35.6180,139.8360?z=13&q=a", nearbyGeoUri(35.618, 139.836, "a"))
        } finally { java.util.Locale.setDefault(saved) }
    }

    @Test fun shared_place_name_drops_invisible_characters() {
        // 文字の向きを変える制御文字やゼロ幅の文字で、表示を偽装されないようにする
        val (name, _) = parseSharedPlace("見本\u202E堤防\u200B https://maps.app.goo.gl/abc")
        assertEquals("見本堤防", name)
    }

    @Test fun brand_suggestions_match_partial_input_past_first() {
        val past = listOf("シマノ ステラ 2500", "メーカーA")
        // 途中まで・ひらがなでも当たる。前に登録した値が先、メーカー名が後
        assertEquals(listOf("シマノ ステラ 2500", "シマノ"), brandSuggestions("しま", past))
        // 大文字と小文字・全角と半角を区別しない
        assertEquals(listOf("メーカーA"), brandSuggestions("ﾒｰｶ", past, makers = emptyList()))
        // 言葉で始まる物を先に
        assertEquals(listOf("ダイワ", "ダイコー"), brandSuggestions("ダイ", emptyList()))
        // 前に登録した値は、途中に含むだけでもメーカー名より先
        assertEquals(listOf("釣具 シマノ改", "シマノ"), brandSuggestions("しま", listOf("釣具 シマノ改")))
        // 空・入れた物と同じ値は出さない。件数は上限まで
        assertEquals(emptyList<String>(), brandSuggestions(" ", past))
        assertEquals(emptyList<String>(), brandSuggestions("メーカーA", past, makers = emptyList()))
        assertTrue(brandSuggestions("ー", emptyList()).size <= 6)
    }

    @Test fun gear_search_query_joins_brand_and_name_without_repeats() {
        assertEquals("シマノ ステラ アジング用リール", gearSearchQuery("シマノ ステラ", "アジング用リール"))
        assertEquals("シマノ ステラ", gearSearchQuery("シマノ ステラ", "ステラ"))
        assertEquals("ダイワ 月下美人", gearSearchQuery("ダイワ", "ダイワ 月下美人"))
        assertEquals("アジングロッド", gearSearchQuery("", " アジングロッド "))
        assertEquals("", gearSearchQuery(" ", ""))
    }
}
