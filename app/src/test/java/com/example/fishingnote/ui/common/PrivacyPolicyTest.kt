package com.example.fishingnote.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PrivacyPolicyTest {
    // 単体テストは app フォルダを作業フォルダにして動く。アプリの中の本文は、ビルドのときにこの原稿から作られる
    private val body = File("../docs/プライバシーポリシー.md").readText(Charsets.UTF_8).substringAfter("-->").trim()

    @Test
    fun parses_headings_bullets_and_tables() {
        val blocks = parsePolicy(
            """
            # 題名

            ## 1. 見出し

            本文です。
            - 箇条書き
            | 操作 | 渡す先 |
            |---|---|
            | 地図 | 地図アプリ |
            """.trimIndent(),
        )
        assertEquals(
            listOf(
                PolicyBlock.Title("題名"),
                PolicyBlock.Heading("1. 見出し"),
                PolicyBlock.Paragraph("本文です。"),
                PolicyBlock.Bullet("箇条書き"),
                PolicyBlock.Row(listOf("操作" to "地図", "渡す先" to "地図アプリ")),
            ),
            blocks,
        )
    }

    /** セルの数が見出しと合わない行も、欠けずに出る */
    @Test
    fun uneven_table_row_is_not_cut() {
        val rows = parsePolicy("| a | b |\n|---|---|\n| 1 | 2 | 3 |").filterIsInstance<PolicyBlock.Row>()
        assertEquals(listOf("a" to "1", "b" to "2", "" to "3"), rows.single().cells)
    }

    /** 読めない書き方とセルの数の違いを見つける */
    @Test
    fun problems_are_found() {
        val text = "### 小見出し\n* 星の箇条書き\n1. 番号\n> 引用\n  - 入れ子\n**太字**\n`コード`\n[文](https://example.com)\n| a | b |\n|---|---|\n| 1 |"
        // 行頭の書き方5つ、文中の記号3つ、セルの数の違い1つ
        assertEquals(9, policyProblems(text).size)
        assertTrue(policyProblems("# 題名\n\n## 見出し\n\n- 箇条書き\n| a | b |\n|---|---|\n| 1 | 2 |").isEmpty())
    }

    /** 実際の原稿が、画面で記号の残らない書き方だけでできていること */
    @Test
    fun real_policy_uses_only_supported_markdown() {
        assertEquals(emptyList<String>(), policyProblems(body))
        val blocks = parsePolicy(body)
        assertTrue(blocks.first() is PolicyBlock.Title)
        assertTrue(blocks.any { it is PolicyBlock.Heading })
        assertTrue(blocks.filterIsInstance<PolicyBlock.Row>().all { row -> row.cells.none { (k, v) -> k.isBlank() || v.isBlank() } })
    }
}
