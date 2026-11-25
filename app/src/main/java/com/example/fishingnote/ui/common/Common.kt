@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.fishingnote.ui.common

import androidx.compose.material3.FilledTonalButton
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Backpack
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Hardware
import androidx.compose.material.icons.filled.Phishing
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import com.example.fishingnote.data.GearCategory
import com.example.fishingnote.ui.theme.OceanDeep
import com.example.fishingnote.ui.theme.Ocean
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.fishingnote.FishingNoteApp
import com.example.fishingnote.data.Repository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 画面ごとの ViewModel を、アプリの Repository を渡して作る */
@Composable
inline fun <reified VM : ViewModel> appViewModel(crossinline create: (Repository) -> VM): VM {
    val app = LocalContext.current.applicationContext as FishingNoteApp
    return viewModel(factory = viewModelFactory { initializer { create(app.repository) } })
}

private val dateFormat = DateTimeFormatter.ofPattern("yyyy/M/d（E）", Locale.JAPAN)
fun LocalDate.label(): String = format(dateFormat)

/** 一覧の画面の「追加」ボタン。上のバーに文字付きで置く */
data class AddAction(val label: String, val onClick: () -> Unit)

/**
 * 画面の枠。タブの画面（topLevel）では下のタブが下端の余白を持つため、ここでは余白を足さない。
 * 追加のボタンは、向きによらず上のバーに置く。右下に浮かせると、一覧の行にある「＋」（数を増やす）や
 * 「地図」のボタンに重なり、押し間違えるため。
 */
@Composable
fun ScreenScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    topLevel: Boolean = false,
    snackbar: SnackbarHostState? = null,
    actions: @Composable RowScope.() -> Unit = {},
    add: AddAction? = null,
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        bottomBar = bottomBar,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack, modifier = Modifier.size(TouchTarget)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                        }
                    }
                },
                actions = {
                    actions()
                    if (add != null) {
                        FilledTonalButton(onClick = add.onClick, modifier = Modifier.heightIn(min = TouchTarget).padding(end = 8.dp)) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Text(add.label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                },
            )
        },
        snackbarHost = { snackbar?.let { SnackbarHost(it) } },
        contentWindowInsets = if (topLevel) WindowInsets(0, 0, 0, 0) else ScaffoldDefaults.contentWindowInsets,
        content = content,
    )
}

/** 日付の入力。押すとカレンダーが開く */
@Composable
fun DateField(label: String, date: LocalDate?, onChange: (LocalDate?) -> Unit, allowClear: Boolean = false) {
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { open = true }, modifier = Modifier.widthIn(min = 180.dp).heightIn(min = TouchTarget)) {
                Text(date?.label() ?: "未設定", style = MaterialTheme.typography.titleSmall)
            }
            OutlinedButton(onClick = { onChange(LocalDate.now()) }, modifier = Modifier.heightIn(min = TouchTarget)) { Text("今日") }
        }
    }
    if (open) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (date ?: LocalDate.now()).toEpochDay() * 86_400_000L,
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    open = false
                }, modifier = Modifier.heightIn(min = TouchTarget)) { Text("決定") }
            },
            // 「日付を消す」は押し間違えないよう、一覧の横ではなくカレンダーの中に置く
            dismissButton = {
                Row {
                    if (allowClear && date != null) TextButton(onClick = { onChange(null); open = false }, modifier = Modifier.heightIn(min = TouchTarget)) { Text("日付を消す") }
                    TextButton(onClick = { open = false }, modifier = Modifier.heightIn(min = TouchTarget)) { Text("やめる") }
                }
            },
        ) { DatePicker(state = state) }
    }
}

/** 押す対象の大きさ。釣りの最中（濡れた手・手袋）でも押せるよう、標準の 48dp より大きくする */
val TouchTarget = 56.dp
val BigButtonHeight = 60.dp

/** 数の増減。ボタンは 56dp。name は読み上げに使う（例：「スナップを1つ減らす」） */
@Composable
fun Stepper(value: Int, onChange: (Int) -> Unit, min: Int = 0, label: String? = null, name: String = "") {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(
            onClick = { if (value > min) onChange(value - 1) },
            enabled = value > min,
            modifier = Modifier.size(TouchTarget).semantics { contentDescription = "${name}を1つ減らす" },
        ) { Text("−", style = MaterialTheme.typography.headlineSmall) }
        Text(
            label ?: value.toString(),
            modifier = Modifier.widthIn(min = 64.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleLarge,
        )
        FilledTonalIconButton(onClick = { onChange(value + 1) }, modifier = Modifier.size(TouchTarget)) {
            Icon(Icons.Filled.Add, contentDescription = "${name}を1つ増やす", modifier = Modifier.size(28.dp))
        }
    }
}

/** 選択肢のチップを折り返して並べる（横に流して見切れさせない）。手袋でも押し分けられるよう、高さは TouchTarget 以上・間隔は 12dp */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChoiceChips(options: List<T>, selected: (T) -> Boolean, label: (T) -> String, onClick: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        options.forEach { o ->
            val isSelected = selected(o)
            // 選んだ物は色だけでなく印でも分かるようにし、日差しの下でも見分けられる濃い色にする
            FilterChip(
                selected = isSelected,
                onClick = { onClick(o) },
                label = { Text(label(o), style = MaterialTheme.typography.labelLarge) },
                leadingIcon = if (isSelected) ({ Icon(Icons.Filled.Check, contentDescription = null) }) else null,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier.heightIn(min = TouchTarget),
            )
        }
    }
}

/** 前に入力した値を候補として並べる。押すとその値が入る */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SuggestionRow(values: List<String>, onPick: (String) -> Unit) {
    if (values.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        values.forEach { v ->
            AssistChip(onClick = { onPick(v) }, label = { Text(v, style = MaterialTheme.typography.labelLarge) }, modifier = Modifier.heightIn(min = TouchTarget))
        }
    }
}

@Composable
fun Badge(text: String, container: Color, content: Color) {
    Surface(color = container, contentColor = content, shape = MaterialTheme.shapes.small) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun EmptyState(message: String, modifier: Modifier = Modifier, icon: ImageVector = Icons.Filled.Phishing, action: @Composable () -> Unit = {}) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            IconTile(icon, size = 88.dp)
            Text(message, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            action()
        }
    }
}

@Composable
fun ConfirmDialog(title: String, message: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }, modifier = Modifier.heightIn(min = TouchTarget)) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = TouchTarget)) { Text("やめる") } },
    )
}

/**
 * 画面の主役になる情報（今年のまとめ・準備の進み具合など）を載せる、グラデーションのカード。
 * 中の文字は白。両端の色とも白い文字とのコントラストを 4.5:1 以上にしてある。
 */
@Composable
fun GradientCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    // ダークモードの primary は明るい水色で白い文字が読めないため、両方の配色で同じ濃い青に固定する
    val brush = Brush.linearGradient(listOf(Ocean, OceanDeep))
    Surface(
        shape = MaterialTheme.shapes.large,
        color = Color.Transparent,
        contentColor = Color.White,
        shadowElevation = 4.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.background(brush).padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

/** 種類ごとのアイコン。一覧の左に色付きの枠で出し、文字を読まなくても見分けられるようにする */
fun categoryIcon(c: GearCategory): ImageVector = when (c) {
    GearCategory.ROD -> Icons.Filled.Phishing
    GearCategory.REEL -> Icons.Filled.Album
    GearCategory.LINE -> Icons.Filled.Gesture
    GearCategory.LURE -> Icons.Filled.BugReport
    GearCategory.TERMINAL -> Icons.Filled.Hardware
    GearCategory.OTHER -> Icons.Filled.Backpack
}

/** 角の丸い色付きの枠に入れたアイコン（一覧の左端に置く） */
@Composable
fun IconTile(icon: ImageVector, size: Dp = 48.dp, container: Color = MaterialTheme.colorScheme.primaryContainer, content: Color = MaterialTheme.colorScheme.primary) {
    Box(
        Modifier.size(size).background(container, RoundedCornerShape(size * 0.32f)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(size * 0.52f)) }
}

/** 丸いチェック。チェックすると青く塗られ、白い印が出る（四角い小さなチェック欄より押しやすく、見分けやすい） */
@Composable
fun CheckCircle(checked: Boolean, size: Dp = 34.dp) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier.size(size)
            .background(if (checked) scheme.primary else Color.Transparent, CircleShape)
            .border(2.dp, if (checked) scheme.primary else scheme.outline, CircleShape),
        contentAlignment = Alignment.Center,
    ) { if (checked) Icon(Icons.Filled.Check, contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(size * 0.6f)) }
}

/** 白いカード。角を大きく丸め、薄い影で背景から浮かせる */
@Composable
fun AppCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
    val elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    if (onClick != null) Card(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, colors = colors, elevation = elevation, content = content)
    else Card(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, colors = colors, elevation = elevation, content = content)
}

/** 編集画面の下端に固定する保存ボタン。スクロールしなくても押せる */
@Composable
fun SaveBar(label: String = "保存する", onSave: () -> Unit) {
    Surface(tonalElevation = 3.dp, shadowElevation = 6.dp) {
        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 16.dp, vertical = 10.dp).height(BigButtonHeight),
        ) { Text(label, style = MaterialTheme.typography.titleMedium) }
    }
}

/**
 * 編集画面の「変更したか」を比べるための文字列を作る。
 * 値ごとに種類と長さを付けてつなぐので、区切りの文字（「, 」など）を入力しても別の入力と同じ文字列にならない。
 */
fun editKey(vararg parts: Any?): String = buildString { parts.forEach { appendEditKey(it) } }

private fun StringBuilder.appendEditKey(v: Any?) {
    when (v) {
        null -> append('n')
        is Collection<*> -> { append('l').append(v.size).append('['); v.forEach { appendEditKey(it) }; append(']') }
        else -> { val t = v.toString(); append('v').append(t.length).append(':').append(t) }
    }
}

/**
 * 変更したまま戻ろうとしたら確認する（濡れた手で戻るに触れて、入力を失わないため）。
 * 戻る操作に使う関数を返す。端末の戻る操作も同じ確認を通す。
 */
@Composable
fun rememberGuardedBack(dirty: Boolean, onLeave: () -> Unit): () -> Unit {
    // 画面を回しても確認の画面が消えないよう、保存できる状態に置く
    var asking by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = dirty) { asking = true }
    if (asking) {
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text("保存せずに戻りますか") },
            text = { Text("入力した内容は保存されていません。") },
            confirmButton = { TextButton(onClick = { asking = false; onLeave() }, modifier = Modifier.heightIn(min = TouchTarget)) { Text("保存せずに戻る") } },
            dismissButton = { TextButton(onClick = { asking = false }, modifier = Modifier.heightIn(min = TouchTarget)) { Text("編集を続ける") } },
        )
    }
    return { if (dirty) asking = true else onLeave() }
}

/** 現在地の近くを地図アプリで探す。開けるアプリが無ければ false を返す */
fun openNearby(context: Context, query: String, lat: Double, lng: Double): Boolean {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(com.example.fishingnote.domain.nearbyGeoUri(lat, lng, query)))
    // 現在地を含むため、受け取るアプリを絞る。Google マップがあればそれだけに渡し、
    // 無ければ既定のアプリに任せず、毎回利用者に選んでもらう（geo: を名乗る別のアプリに黙って渡さないため）
    val maps = Intent(intent).setPackage(GOOGLE_MAPS)
    if (isGenuineGoogleMaps(context) && maps.resolveActivity(context.packageManager) != null) {
        context.startActivity(maps)
        return true
    }
    if (intent.resolveActivity(context.packageManager) == null) return false
    context.startActivity(Intent.createChooser(intent, "現在地を渡す地図アプリを選んでください"))
    return true
}

const val GOOGLE_MAPS = "com.google.android.apps.maps"

/**
 * Google マップの署名（証明書の SHA-256）。端末に入っている Google マップから取り出した値。
 * パッケージ名だけで信用すると、Google マップが無い端末に同じ名前を名乗るアプリを入れられたとき、
 * 選択画面を出さずに現在地や釣り場を渡してしまうため、署名も確かめる。
 * 合わない場合（別の鍵で署名された版など）は、渡す先を毎回利用者に選んでもらう側に倒れる。
 */
private const val GOOGLE_MAPS_CERT_SHA256 = "f0fd6c5b410f25cb25c3b53346c8972fae30f8ee7411df910480ad6b2d60db83"

/** Google マップが入っていて、Google の署名であれば true */
fun isGenuineGoogleMaps(context: Context): Boolean = isSignedBy(context, GOOGLE_MAPS, GOOGLE_MAPS_CERT_SHA256)

/** 指定したアプリが、指定した証明書（SHA-256 の16進）で署名されていれば true。入っていなければ false */
fun isSignedBy(context: Context, packageName: String, certSha256Hex: String): Boolean {
    val cert = certSha256Hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    val pm = context.packageManager
    return try {
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            pm.hasSigningCertificate(packageName, cert, android.content.pm.PackageManager.CERT_INPUT_SHA256)
        } else {
            // Android 8〜8.1 は署名の一覧から確かめる。署名が複数ある物は信用しない
            @Suppress("DEPRECATION")
            val sigs = pm.getPackageInfo(packageName, android.content.pm.PackageManager.GET_SIGNATURES).signatures
            sigs != null && sigs.size == 1 &&
                java.security.MessageDigest.getInstance("SHA-256").digest(sigs[0].toByteArray()).contentEquals(cert)
        }
    } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
        false
    }
}

/** 地図アプリで検索する。開けるアプリが無ければ false を返す */
fun openMap(context: Context, query: String): Boolean {
    // Google マップの URL はそのまま開く。それ以外は地図アプリの検索に渡す
    val uri = if (com.example.fishingnote.domain.isMapUrl(query)) Uri.parse(query.trim()) else Uri.parse("geo:0,0?q=" + Uri.encode(query))
    val intent = Intent(Intent.ACTION_VIEW, uri)
    // 保存した釣り場（緯度経度を含むことがある）も、現在地と同じく渡す先を絞る。
    // Google マップがあればそれだけに渡し、無ければ毎回利用者に選んでもらう
    val maps = Intent(intent).setPackage(GOOGLE_MAPS)
    if (isGenuineGoogleMaps(context) && maps.resolveActivity(context.packageManager) != null) {
        context.startActivity(maps)
        return true
    }
    if (intent.resolveActivity(context.packageManager) == null) return false
    context.startActivity(Intent.createChooser(intent, "釣り場を開く地図アプリを選んでください"))
    return true
}

/**
 * ブラウザなどでネット検索を開く。アプリ自身は通信せず、検索語を検索のアプリに渡すだけ。
 * 検索のアプリが無ければ、ブラウザで検索サイトを開く。どちらも無ければ false を返す
 */
fun openWebSearch(context: Context, query: String): Boolean {
    val search = Intent(Intent.ACTION_WEB_SEARCH).putExtra(android.app.SearchManager.QUERY, query)
    val browser = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(query)))
        .addCategory(Intent.CATEGORY_BROWSABLE)
    // 検索のアプリは、検索語が http/https で始まるとそのサイトを開くことがあるため、その場合は検索サイトの検索として開く
    val looksLikeUrl = query.trim().let { it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) }
    for (intent in if (looksLikeUrl) listOf(browser) else listOf(search, browser)) {
        try {
            context.startActivity(intent)
            return true
        } catch (e: android.content.ActivityNotFoundException) {
            // 次の方法を試す
        } catch (e: SecurityException) {
            // 受け取る側が開かせてくれない。次の方法を試す
        }
    }
    return false
}

/** 入力の長さの上限。長すぎる名前で案内や一覧が画面を覆わないようにする */
const val NAME_MAX = 40
const val TEXT_MAX = 100
const val MEMO_MAX = 1000

/** 数字の入力欄の値を読む。空欄は null、読めない値は NaN 扱いにせず invalid を返す */
sealed interface NumberInput {
    data object Empty : NumberInput
    data class Value(val number: Double) : NumberInput
    data object Invalid : NumberInput
}

fun parseNumber(text: String): NumberInput {
    val t = com.example.fishingnote.domain.normalize(text).trim()
    if (t.isEmpty()) return NumberInput.Empty
    val v = t.toDoubleOrNull() ?: return NumberInput.Invalid
    return if (v < 0 || v.isNaN() || v.isInfinite()) NumberInput.Invalid else NumberInput.Value(v)
}

/** Compose の画面から、それを表示している Activity を探す（画面を回している最中かを調べるため） */
fun android.content.Context.findActivity(): android.app.Activity? {
    var c: android.content.Context = this
    while (c is android.content.ContextWrapper) {
        if (c is android.app.Activity) return c
        c = c.baseContext
    }
    return null
}
