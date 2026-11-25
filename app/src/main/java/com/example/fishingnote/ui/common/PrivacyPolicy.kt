package com.example.fishingnote.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * プライバシーポリシーの本文（res/raw/privacy_policy.md）を画面に出すための部品。
 * 本文はビルドのときに docs/プライバシーポリシー.md から作る（app/build.gradle.kts の generatePrivacyPolicy）。
 * Markdown のうち、この本文で使う書き方（見出し・箇条書き・表・段落）だけを読む。
 * それ以外の書き方は記号のまま出てしまうため、policyProblems で見つけて単体テストで止める。
 */
sealed interface PolicyBlock {
    data class Title(val text: String) : PolicyBlock
    data class Heading(val text: String) : PolicyBlock
    data class Paragraph(val text: String) : PolicyBlock
    data class Bullet(val text: String) : PolicyBlock
    /** 表の1行。狭い画面で横に並べると読めないため、「見出し：値」の組にして縦に並べる */
    data class Row(val cells: List<Pair<String, String>>) : PolicyBlock
}

fun parsePolicy(text: String): List<PolicyBlock> {
    val blocks = mutableListOf<PolicyBlock>()
    var header: List<String>? = null
    for (raw in text.lines()) {
        val line = raw.trimEnd()
        if (!line.startsWith("|")) header = null
        when {
            line.isBlank() -> Unit
            line.startsWith("## ") -> blocks += PolicyBlock.Heading(line.removePrefix("## ").trim())
            line.startsWith("# ") -> blocks += PolicyBlock.Title(line.removePrefix("# ").trim())
            line.startsWith("- ") -> blocks += PolicyBlock.Bullet(line.removePrefix("- ").trim())
            line.startsWith("|") -> {
                val cells = line.trim().removePrefix("|").removeSuffix("|").split("|").map { it.trim() }
                val h = header
                when {
                    h == null -> header = cells
                    cells.all { TABLE_RULE.matches(it) } -> Unit // 見出しと本体の区切りの行
                    // セルの数が見出しと合わなくても欠けないよう、足りない側を空で埋める（数の違いは policyProblems で止める）
                    else -> blocks += PolicyBlock.Row((0 until maxOf(h.size, cells.size)).map { h.getOrElse(it) { "" } to cells.getOrElse(it) { "" } })
                }
            }
            else -> blocks += PolicyBlock.Paragraph(line.trim())
        }
    }
    return blocks
}

private val TABLE_RULE = Regex("^:?-+:?$")
private val UNSUPPORTED_LINE = Regex("""^(#{3,}\s|\*\s|\d+\.\s|>|\s+[-*]\s)""")
private val UNSUPPORTED_INLINE = listOf("**", "`", "](", "\\|")

/** parsePolicy が読めない書き方（記号のまま画面に出る物）と、セルの数が見出しと合わない表の行を返す */
fun policyProblems(text: String): List<String> {
    val problems = mutableListOf<String>()
    var headerSize: Int? = null
    text.lines().forEachIndexed { i, raw ->
        val line = raw.trimEnd()
        val no = i + 1
        if (UNSUPPORTED_LINE.containsMatchIn(line)) problems += "${no}行目：読めない書き方「$line」"
        UNSUPPORTED_INLINE.filter { line.contains(it) }.forEach { problems += "${no}行目：記号「$it」が画面にそのまま出る" }
        if (line.startsWith("|")) {
            val cells = line.trim().removePrefix("|").removeSuffix("|").split("|").size
            val h = headerSize
            if (h == null) headerSize = cells else if (cells != h) problems += "${no}行目：表のセルが${cells}個（見出しは${h}個）"
        } else headerSize = null
    }
    return problems
}

@Composable
fun PolicyContent(blocks: List<PolicyBlock>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (b in blocks) when (b) {
            is PolicyBlock.Title -> Text(b.text, style = MaterialTheme.typography.titleMedium)
            is PolicyBlock.Heading -> Text(b.text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 10.dp))
            is PolicyBlock.Paragraph -> Text(b.text, style = MaterialTheme.typography.bodyMedium)
            is PolicyBlock.Bullet -> Row {
                Text("・", style = MaterialTheme.typography.bodyMedium)
                Text(b.text, style = MaterialTheme.typography.bodyMedium)
            }
            is PolicyBlock.Row -> Column(Modifier.padding(vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                HorizontalDivider()
                for ((name, value) in b.cells) {
                    Text(name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Text(value, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
