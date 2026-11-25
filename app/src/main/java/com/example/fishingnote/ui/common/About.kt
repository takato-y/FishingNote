package com.example.fishingnote.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.fishingnote.R

/** アプリに含めている外部のライブラリと、そのライセンス（全文は res/raw に置く） */
private data class LibraryNotice(val name: String, val owner: String, val license: String, val text: Int)

private val NOTICES = listOf(
    LibraryNotice("AndroidX（Jetpack）のライブラリ一式（Compose・Room・Lifecycle・Navigation など）", "The Android Open Source Project / Google LLC", "Apache License 2.0", R.raw.license_apache_2_0),
    LibraryNotice("Kotlin 標準ライブラリ・kotlinx.coroutines・kotlinx.serialization・JetBrains Java Annotations", "JetBrains s.r.o.", "Apache License 2.0", R.raw.license_apache_2_0),
    LibraryNotice("Guava ListenableFuture", "Google LLC", "Apache License 2.0", R.raw.license_apache_2_0),
    LibraryNotice("JSpecify", "JSpecify プロジェクト", "Apache License 2.0", R.raw.license_apache_2_0),
    LibraryNotice("zxcvbn4j（パスワードの強さの見積もり）", "Nulab Inc", "MIT License", R.raw.license_zxcvbn4j),
    // zxcvbn4j は元の zxcvbn を Java に移した物で、辞書などを含むため、元の著作権表示も載せる
    LibraryNotice("zxcvbn（zxcvbn4j の元になったもの。見積もりの辞書を含む）", "Dan Wheeler and Dropbox, Inc.", "MIT License", R.raw.license_zxcvbn_dropbox),
)

/** このアプリについて：版、プライバシーポリシー、使っているライブラリのライセンス。各項目を押すと全文を出す */
@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }
    var open by remember { mutableStateOf<LibraryNotice?>(null) }
    var policyOpen by remember { mutableStateOf(false) }
    val anyOpen = open != null || policyOpen
    AlertDialog(
        // 全文を読んでいるときの戻る操作は、「一覧に戻る」ボタンと同じく一覧に戻す（窓の中では BackHandler が効かないため、ここで分ける）
        onDismissRequest = { if (anyOpen) { open = null; policyOpen = false } else onDismiss() },
        // ポリシーの題名は本文の先頭に出す（窓の題名に入れると、大きな文字で「ー」だけが次の行に折り返したため）
        title = { Text(if (open != null) open!!.license else "このアプリについて") },
        text = {
            // 一覧と全文を切り替えたら、先頭から表示する
            Column(Modifier.verticalScroll(remember(open, policyOpen) { ScrollState(0) }), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val shown = open
                if (policyOpen) {
                    val blocks = remember {
                        parsePolicy(context.resources.openRawResource(R.raw.privacy_policy).bufferedReader(Charsets.UTF_8).use { it.readText() })
                    }
                    PolicyContent(blocks)
                } else if (shown == null) {
                    Text("釣り道具ノート　版 $version", style = MaterialTheme.typography.bodyLarge)
                    Column(
                        Modifier.fillMaxWidth().heightIn(min = TouchTarget)
                            .clickable(role = Role.Button, onClickLabel = "プライバシーポリシーを表示") { policyOpen = true }
                            .padding(vertical = 8.dp),
                    ) {
                        Text("プライバシーポリシー", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                        Text("このアプリが扱う情報と、その扱い方", style = MaterialTheme.typography.bodySmall)
                    }
                    Text("このアプリは、次のライブラリを使っています。押すとライセンスの全文を表示します。", style = MaterialTheme.typography.bodyMedium)
                    for (n in NOTICES) {
                        // 左寄せの行にする（ボタンの中に入れると中央に寄り、長い名前の左端が切れたため）
                        Column(
                            Modifier.fillMaxWidth().heightIn(min = TouchTarget)
                                .clickable(role = Role.Button, onClickLabel = "ライセンスの全文を表示") { open = n }
                                .padding(vertical = 8.dp),
                        ) {
                            Text(n.name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                            Text("${n.owner}　${n.license}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                } else {
                    val text = remember(shown) {
                        context.resources.openRawResource(shown.text).bufferedReader(Charsets.UTF_8).use { it.readText() }
                    }
                    Text(shown.name, style = MaterialTheme.typography.bodyMedium)
                    Text(text, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (anyOpen) { open = null; policyOpen = false } else onDismiss() }, modifier = Modifier.heightIn(min = TouchTarget)) {
                Text(if (anyOpen) "一覧に戻る" else "閉じる")
            }
        },
    )
}
