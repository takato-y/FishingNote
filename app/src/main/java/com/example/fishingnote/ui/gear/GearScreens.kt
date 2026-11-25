@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.example.fishingnote.ui.gear

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.fishingnote.FishingNoteApp
import com.example.fishingnote.data.Backup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Switch
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.flow.map
import com.example.fishingnote.ui.common.openWebSearch
import com.example.fishingnote.domain.gearSearchQuery
import com.example.fishingnote.domain.brandSuggestions
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalClipboard
import kotlinx.coroutines.delay
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.Key
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuKeys
import androidx.compose.foundation.text.contextmenu.modifier.filterTextContextMenuComponents
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Lifecycle
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import com.example.fishingnote.data.BackupCrypto
import com.example.fishingnote.data.WrongPasswordException
import com.example.fishingnote.data.BackupData
import com.example.fishingnote.data.BackupFormatException
import com.example.fishingnote.data.Gear
import com.example.fishingnote.data.GearCategory
import com.example.fishingnote.data.Repository
import com.example.fishingnote.domain.GearFilter
import com.example.fishingnote.domain.countByFilter
import com.example.fishingnote.domain.filterGear
import com.example.fishingnote.domain.isLowStock
import com.example.fishingnote.domain.lineStatus
import com.example.fishingnote.ui.common.MEMO_MAX
import com.example.fishingnote.ui.common.editKey
import com.example.fishingnote.ui.common.TEXT_MAX
import com.example.fishingnote.ui.common.NAME_MAX
import com.example.fishingnote.ui.common.Badge
import com.example.fishingnote.ui.common.IconTile
import com.example.fishingnote.ui.common.categoryIcon
import androidx.compose.material.icons.filled.Inventory2
import com.example.fishingnote.ui.common.BigButtonHeight
import com.example.fishingnote.ui.common.ChoiceChips
import com.example.fishingnote.ui.common.ConfirmDialog
import com.example.fishingnote.ui.common.AddAction
import com.example.fishingnote.ui.common.DateField
import com.example.fishingnote.ui.common.EmptyState
import com.example.fishingnote.ui.common.NumberInput
import com.example.fishingnote.ui.common.SaveBar
import com.example.fishingnote.ui.common.ScreenScaffold
import com.example.fishingnote.ui.common.Stepper
import com.example.fishingnote.ui.common.TouchTarget
import com.example.fishingnote.ui.common.appViewModel
import com.example.fishingnote.ui.common.label
import com.example.fishingnote.ui.common.parseNumber
import com.example.fishingnote.ui.common.rememberGuardedBack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.time.LocalDate

data class GearListState(
    val all: List<Gear> = emptyList(),
    val shown: List<Gear> = emptyList(),
    val counts: Map<GearFilter, Int> = emptyMap(),
    val query: String = "",
    val filter: GearFilter = GearFilter.ALL,
    val today: LocalDate = LocalDate.now(),
)

class GearViewModel(private val repo: Repository) : ViewModel() {
    private val query = MutableStateFlow("")

    /** 前に登録した「メーカー・商品名」（入力の候補に使う。新しく登録した物を先に） */
    val pastBrands: StateFlow<List<String>> = repo.gear
        .map { all -> all.sortedByDescending { it.id }.map { it.brand.trim() }.filter { it.isNotEmpty() }.distinct() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val filter = MutableStateFlow(GearFilter.ALL)

    val state = combine(repo.gear, query, filter) { all, q, f ->
        val today = LocalDate.now()
        GearListState(all, filterGear(all, q, f, today), countByFilter(all, today), q, f, today)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GearListState())

    /** 検索するときは絞り込みを外す（絞り込みが残っていて「持っていない」と誤解させないため） */
    fun setQuery(q: String) {
        if (q.isNotBlank() && query.value.isBlank()) filter.value = GearFilter.ALL
        query.value = q
    }

    fun setFilter(f: GearFilter) { filter.value = f }
    fun addQuantity(id: Long, delta: Int) = viewModelScope.launch { repo.addQuantity(id, delta) }

    suspend fun load(id: Long): Gear? = repo.getGear(id)
    fun save(gear: Gear, done: () -> Unit) = viewModelScope.launch { repo.saveGear(gear); done() }
    fun delete(gear: Gear, done: () -> Unit) = viewModelScope.launch { repo.deleteGear(gear); done() }

    /**
     * 書き出しに使うパスワード。保存先を選ぶ画面から戻るまでの間だけ持つ。
     * 画面の状態（回転で保存される領域）には置かない。読み書きは画面のスレッドだけで行う。
     */
    private var exportPassword: CharArray? = null

    fun holdExportPassword(password: CharArray) { clearExportPassword(); exportPassword = password }
    fun clearExportPassword() { exportPassword?.fill('\u0000'); exportPassword = null }
    override fun onCleared() = clearExportPassword()

    /** 保存の結果（画面へ知らせる文と、成功したか） */
    data class ExportResult(val message: String, val saved: Boolean)

    /** 保存の結果を画面へ届ける。画面を回している最中に終わっても、作り直した画面で受け取れるよう溜めておく */
    private val _exportResults = Channel<ExportResult>(Channel.BUFFERED)
    val exportResults: ReceiveChannel<ExportResult> = _exportResults

    /** 保存している最中か（その間は保存のメニューを押せなくし、保存中と表示する） */
    private val _exporting = MutableStateFlow(false)
    val exporting: StateFlow<Boolean> = _exporting

    /**
     * 保存を始める。画面ではなくここで動かすので、保存の途中で画面を回しても止まらない。
     * パスワードは呼び出したその場（画面のスレッド）で預かり先から受け取る（続けて保存したとき、別の保存の物と混ざらないため）。
     */
    fun startExport(context: Context, uri: Uri, encrypt: Boolean) {
        val app = context.applicationContext
        val password = if (encrypt) exportPassword.also { exportPassword = null } else null
        if (encrypt && password == null) {
            // 保存先を選んでいる間にアプリが終了させられ、預かったパスワードが失われた場合
            viewModelScope.launch {
                deleteIfEmpty(app, uri)
                _exportResults.send(ExportResult("保存できませんでした。お手数ですが、もう一度保存してください", saved = false))
            }
            return
        }
        _exporting.value = true
        viewModelScope.launch {
            // 書き始めたら最後まで終える（途中で止めると、保存先に空のファイルだけが残るため）
            val result = withContext(NonCancellable) {
                var opened = false
                try {
                    writeBackup(app, uri, password) { opened = true }
                    (app as FishingNoteApp).lastBackupDay = LocalDate.now().toEpochDay()
                    ExportResult(if (encrypt) "パスワードで守ったバックアップを保存しました" else "バックアップを保存しました", saved = true)
                } catch (e: Exception) {
                    // 書けなかったときの後始末。書き込みを始めた後なら中身が壊れているので消す。
                    // 始める前なら、保存先を選んだ時点でできた空のファイルだけを消す
                    // （書き込みを始める前の失敗では、中身のあるファイルは消さない）
                    if (opened) deleteQuietly(app, uri) else deleteIfEmpty(app, uri)
                    ExportResult("保存できませんでした：${e.message ?: "原因が分かりませんでした"}", saved = false)
                } finally {
                    password?.fill('\u0000')
                    _exporting.value = false
                }
            }
            _exportResults.send(result)
        }
    }

    // 保存先がクラウドのこともあり通信を待つため、画面のスレッドでは動かさない
    private suspend fun deleteQuietly(context: Context, uri: Uri) = withContext(Dispatchers.IO) {
        runCatching { android.provider.DocumentsContract.deleteDocument(context.contentResolver, uri) }
    }

    /** 大きさが 0 のファイルだけを消す（大きさが分からないときは消さない） */
    private suspend fun deleteIfEmpty(context: Context, uri: Uri) = withContext(Dispatchers.IO) {
        val size = runCatching {
            context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null
            }
        }.getOrNull()
        if (size == 0L) deleteQuietly(context, uri)
    }

    private suspend fun writeBackup(context: Context, uri: Uri, password: CharArray?, onOpened: () -> Unit) = withContext(Dispatchers.IO) {
        val json = Backup.toJson(repo.snapshot())
        val bytes = json.toByteArray(Charsets.UTF_8)
        // 読み込みと同じ上限と検査を、書く前に通す（通らない物は、書いても読み戻せないため書かない）
        if (bytes.size > Backup.MAX_BYTES) error("データが多すぎて、読み込める大きさのバックアップを作れません")
        Backup.fromJson(json)
        val out = if (password != null) withContext(Dispatchers.Default) { BackupCrypto.encrypt(bytes, password) }.toByteArray(Charsets.UTF_8) else bytes
        context.contentResolver.openOutputStream(uri, "wt")?.use { onOpened(); it.write(out) }
            ?: error("保存先を開けませんでした")
    }

    /**
     * ファイルを文字として読む（暗号化していれば暗号文のまま返す）。
     * 上限より1バイト多く読み、超えていたら中身を調べずに止める（巨大なファイルでメモリを使い切らないため）。
     * 暗号化した物は Base64 で約4/3倍になるため、その分と見出しの分だけ上限を広げる（中身の上限は復号の前にも確かめる）。
     */
    suspend fun readBackupText(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val limit = Backup.MAX_BYTES / 3 * 4 + 4_096
        val bytes = context.contentResolver.openInputStream(uri)?.use { Backup.readAtMost(it, limit + 1) }
            ?: throw BackupFormatException("ファイルを開けませんでした")
        val text = bytes.toString(Charsets.UTF_8)
        if (bytes.size > limit || (!BackupCrypto.isEncrypted(text) && bytes.size > Backup.MAX_BYTES)) {
            throw BackupFormatException("ファイルが大きすぎます（このアプリのバックアップではないかもしれません）")
        }
        text
    }

    /** 暗号化していないバックアップを検査して返す（DB には触れない） */
    suspend fun parsePlain(text: String): BackupData = withContext(Dispatchers.Default) { Backup.fromJson(text) }

    /** 暗号化したバックアップを復号し、検査して返す（DB には触れない）。パスワードは使い終わったら消す */
    suspend fun decryptBackup(text: String, password: CharArray): BackupData = withContext(Dispatchers.Default) {
        try {
            val plain = BackupCrypto.decrypt(text, password)
            if (plain.size > Backup.MAX_BYTES) throw BackupFormatException("ファイルが大きすぎます")
            Backup.fromJson(plain.toString(Charsets.UTF_8))
        } finally {
            password.fill('\u0000')
        }
    }

    suspend fun restore(data: BackupData) = repo.replaceAll(data)
}

/** この日数を超えてバックアップしていなければ、一覧の上で知らせる */
private const val BACKUP_REMIND_DAYS = 30

@Composable
fun GearListScreen(onEdit: (Long) -> Unit) {
    val vm = appViewModel { GearViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val app = context.applicationContext as FishingNoteApp
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    var menuOpen by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<BackupData?>(null) }
    var lastBackup by remember { mutableStateOf(app.lastBackupDay?.let(LocalDate::ofEpochDay)) }

    var askExport by remember { mutableStateOf(false) }
    var pendingEncrypted by remember { mutableStateOf<String?>(null) }
    // 読み込もうとしているファイルが、パスワードで守られていたか（守られていない物は、置き換えの確認で明示する）
    var pendingProtected by remember { mutableStateOf(false) }

    val exporting by vm.exporting.collectAsStateWithLifecycle()
    // 保存している間は「保存しています」と出し続ける（パスワードから鍵を作るのに数秒かかるため）
    LaunchedEffect(exporting) {
        if (exporting) { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar("保存しています…", duration = SnackbarDuration.Indefinite) }
    }
    // 保存の結果は、画面を回した後でも必ず知らせる
    LaunchedEffect(Unit) {
        for (r in vm.exportResults) {
            if (r.saved) lastBackup = LocalDate.now()
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(r.message)
        }
    }

    fun export(uri: Uri?, encrypt: Boolean) {
        if (uri == null) { vm.clearExportPassword(); return }
        vm.startExport(context, uri, encrypt)
    }
    // 暗号化しない物は JSON、暗号化した物は中を読めないファイルとして、別の種類で保存する
    val plainLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { export(it, encrypt = false) }
    val encryptedLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { export(it, encrypt = true) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                val text = vm.readBackupText(context, uri)
                if (BackupCrypto.isEncrypted(text)) pendingEncrypted = text
                else { pendingProtected = false; pendingImport = vm.parsePlain(text) }
            }.onFailure { e -> snackbar.showSnackbar(if (e is BackupFormatException) e.message ?: "読み込めませんでした" else "読み込めませんでした") }
        }
    }
    fun exportNow() { if (!exporting) askExport = true }

    if (askExport) ExportDialog(
        onPlain = { askExport = false; plainLauncher.launch("釣り道具ノート_${LocalDate.now()}.json") },
        onEncrypted = { password ->
            askExport = false
            vm.holdExportPassword(password)
            encryptedLauncher.launch("釣り道具ノート_${LocalDate.now()}.fnbk")
        },
        onDismiss = { askExport = false },
    )
    pendingEncrypted?.let { text ->
        UnlockDialog(
            unlock = { password -> vm.decryptBackup(text, password) },
            onUnlocked = { data -> pendingEncrypted = null; pendingProtected = true; pendingImport = data },
            onFailed = { message -> pendingEncrypted = null; scope.launch { snackbar.showSnackbar(message) } },
            onDismiss = { pendingEncrypted = null },
        )
    }

    fun changeQuantity(g: Gear, delta: Int) {
        vm.addQuantity(g.id, delta)
        if (delta < 0) scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val r = snackbar.showSnackbar("${g.name.take(20)}を1つ減らしました", actionLabel = "元に戻す", duration = SnackbarDuration.Short)
            if (r == SnackbarResult.ActionPerformed) vm.addQuantity(g.id, -delta)
        }
    }

    pendingImport?.let { data ->
        ConfirmDialog(
            title = "バックアップから戻す",
            // パスワードで守られていないファイルは、中身を差し替えられていても気づけないため、そのことを先に示す
            message = (if (pendingProtected) "" else "このファイルはパスワードで守られていません。自分で保存したファイルか確かめてください。\n\n") +
                "バックアップの中身：${data.summary()}\n\n今のデータはすべて消え、この内容に置き換わります。よろしいですか。",
            confirmLabel = "置き換える",
            onConfirm = {
                scope.launch {
                    val msg = runCatching { vm.restore(data) }.fold({ "バックアップから戻しました" }, { "戻せませんでした。今のデータはそのまま残っています" })
                    snackbar.showSnackbar(msg)
                }
            },
            onDismiss = { pendingImport = null },
        )
    }

    var showAbout by remember { mutableStateOf(false) }
    if (showAbout) com.example.fishingnote.ui.common.AboutDialog(onDismiss = { showAbout = false })

    ScreenScaffold(
        title = "道具",
        topLevel = true,
        snackbar = snackbar,
        actions = {
            IconButton(onClick = { menuOpen = true }, modifier = Modifier.heightIn(min = TouchTarget)) { Icon(Icons.Filled.MoreVert, contentDescription = "メニュー") }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = {
                        Column {
                            Text("バックアップを保存（機種変更・紛失に備える）")
                            Text("釣り場の情報も含まれます。パスワードで守って保存できます", style = MaterialTheme.typography.bodySmall)
                        }
                    },
                    onClick = { menuOpen = false; exportNow() },
                    enabled = !exporting,
                )
                DropdownMenuItem(text = { Text("バックアップから戻す") }, onClick = {
                    menuOpen = false
                    importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                })
                DropdownMenuItem(text = { Text("このアプリについて（プライバシー・ライセンス）") }, onClick = { menuOpen = false; showAbout = true })
            }
        },
        add = AddAction("道具を追加", onClick = { onEdit(0) }),
    ) { padding ->
        // 検索欄と絞り込みも一覧と一緒に送る（上に固定すると、横向きでは道具が1行しか見えないため）。
        // 結果が空でも同じ一覧の中に出し、入力中に検索欄が作り直されてキーボードが閉じないようにする。
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "search") {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = vm::setQuery,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
                    placeholder = { Text("持っている道具を検索") },
                    shape = MaterialTheme.shapes.large,
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (state.query.isNotEmpty()) IconButton(onClick = { vm.setQuery("") }, modifier = Modifier.heightIn(min = TouchTarget)) {
                            Icon(Icons.Filled.Clear, contentDescription = "検索を消す")
                        }
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge,
                )
            }
            item(key = "filters") {
                ChoiceChips(
                    options = GearFilter.entries,
                    selected = { it == state.filter },
                    label = { f -> if (f == GearFilter.ALL) f.label else "${f.label} ${state.counts[f] ?: 0}" },
                    onClick = vm::setFilter,
                )
            }
            when {
                state.all.isEmpty() -> item(key = "empty") {
                    EmptyState("まだ道具がありません。\n「道具を追加」から、竿・リール・ラインなどを登録しましょう。", icon = Icons.Filled.Inventory2)
                }
                state.shown.isEmpty() -> item(key = "none") {
                    EmptyState(
                        if (state.query.isNotBlank() && state.filter != GearFilter.ALL) "「${state.filter.label}」の中には「${state.query}」が見つかりません。"
                        else if (state.query.isNotBlank()) "「${state.query}」は登録されていません。" else "当てはまる道具はありません。"
                    ) {
                        if (state.filter != GearFilter.ALL) FilledTonalButton(onClick = { vm.setFilter(GearFilter.ALL) }, modifier = Modifier.height(BigButtonHeight)) {
                            Text("すべての道具から探す")
                        }
                    }
                }
                else -> {
                    val stale = lastBackup?.let { LocalDate.now().toEpochDay() - it.toEpochDay() > BACKUP_REMIND_DAYS } ?: true
                    if (stale) item(key = "backup") {
                        BackupReminder(lastBackup, onSave = ::exportNow)
                    }
                    state.shown.groupBy { it.category }.toSortedMap().forEach { (category, list) ->
                        item(key = "h-${category.name}") {
                            Text(
                                "${category.label}  ${list.size}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        items(list, key = { it.id }) { g ->
                            GearCard(g, state.today, onClick = { onEdit(g.id) }, onDelta = { changeQuantity(g, it) })
                        }
                    }
                }
            }
        }
    }
}

/** 控えめな1行の案内。目立たせすぎず、押せば保存できる */
@Composable
private fun BackupReminder(last: LocalDate?, onSave: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(if (last == null) "バックアップはまだありません" else "前回のバックアップ：${last.label()}", style = MaterialTheme.typography.bodyMedium)
            Text("機種変更・紛失に備えて保存できます", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FilledTonalButton(onClick = onSave, modifier = Modifier.heightIn(min = TouchTarget)) { Text("保存する") }
    }
}

private fun showsCount(g: Gear) = g.minQuantity != null || g.category == GearCategory.LURE || g.category == GearCategory.TERMINAL

@Composable
private fun GearCard(g: Gear, today: LocalDate, onClick: () -> Unit, onDelta: (Int) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    // 欲しい物（未所持）は、持っている物と見間違えないよう背景と枠を変える
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = if (g.owned) scheme.surfaceContainerLowest else scheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = if (g.owned) 1.dp else 0.dp),
        border = if (g.owned) null else BorderStroke(2.dp, scheme.tertiary),
    ) {
        Row(Modifier.padding(start = 14.dp, end = 8.dp, top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTile(
                categoryIcon(g.category),
                container = if (g.owned) scheme.primaryContainer else scheme.tertiaryContainer,
                content = if (g.owned) scheme.primary else scheme.tertiary,
            )
            Column(Modifier.weight(1f).padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    if (g.owned) g.name else "【未所持】${g.name}",
                    style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                val sub = listOf(g.brand, g.spec).filter { it.isNotBlank() }.joinToString(" ・ ")
                if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (!g.owned) Badge("欲しい物", scheme.tertiaryContainer, scheme.onTertiaryContainer)
                    if (isLowStock(g)) Badge("残り少ない", scheme.errorContainer, scheme.onErrorContainer)
                    lineStatus(g, today)?.let { s ->
                        if (s.due) Badge("巻き替え時期（${s.days}日）", scheme.errorContainer, scheme.onErrorContainer)
                        else Badge("巻き替えから${s.days}日", scheme.secondaryContainer, scheme.onSecondaryContainer)
                    }
                }
                // 数の増減は名前の下の行に置く（名前の横に置くと幅が足りず、名前が語の途中で折り返すため）
                if (g.owned && showsCount(g)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Stepper(g.quantity, onChange = { onDelta(it - g.quantity) }, name = g.name)
                    }
                }
            }
        }
    }
}

private fun specHint(c: GearCategory) = when (c) {
    GearCategory.ROD -> "例：8.6ft ML / ルアー 5〜28g"
    GearCategory.REEL -> "例：2500番 / PE0.8号 150m"
    GearCategory.LINE -> "例：PE0.8号 150m / リーダー フロロ3号"
    GearCategory.LURE -> "例：メタルジグ 30g ピンク"
    GearCategory.TERMINAL -> "例：スナップ #1 / オモリ 5号"
    GearCategory.OTHER -> "例：20L"
}

@Composable
fun GearEditScreen(id: Long, onDone: () -> Unit) {
    val vm = appViewModel { GearViewModel(it) }
    // 読み込み済みの印も保存する（画面の回転などで作り直されたとき、入力中の内容を DB の値で上書きしないため）
    var loaded by rememberSaveable { mutableStateOf(id == 0L) }
    var original by remember { mutableStateOf<Gear?>(null) }
    LaunchedEffect(id) { if (id != 0L && original == null) original = vm.load(id) }
    var name by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf(GearCategory.ROD) }
    var owned by rememberSaveable { mutableStateOf(true) }
    var brand by rememberSaveable { mutableStateOf("") }
    var spec by rememberSaveable { mutableStateOf("") }
    var quantity by rememberSaveable { mutableStateOf(1) }
    var minQty by rememberSaveable { mutableStateOf("") }
    var lineDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var reminder by rememberSaveable { mutableStateOf("") }
    var memo by rememberSaveable { mutableStateOf("") }
    var showErrors by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var initial by rememberSaveable { mutableStateOf<String?>(null) }
    val pastBrands by vm.pastBrands.collectAsStateWithLifecycle()

    val snapshot = editKey(name, category, owned, brand, spec, quantity, minQty, lineDay, reminder, memo)
    LaunchedEffect(id) {
        if (id != 0L && !loaded) {
            vm.load(id)?.let { g ->
                original = g; name = g.name; category = g.category; owned = g.owned; brand = g.brand; spec = g.spec
                quantity = g.quantity; minQty = g.minQuantity?.toString().orEmpty()
                lineDay = g.lineChangedOn?.toEpochDay(); reminder = g.lineReminderDays?.toString().orEmpty(); memo = g.memo
            }
            loaded = true
        }
    }
    LaunchedEffect(loaded) { if (loaded && initial == null) initial = editKey(name, category, owned, brand, spec, quantity, minQty, lineDay, reminder, memo) }
    val back = rememberGuardedBack(dirty = initial != null && snapshot != initial, onLeave = onDone)

    val minParsed = parseNumber(minQty)
    val remParsed = parseNumber(reminder)
    val nameError = name.isBlank()
    // 整数で、ありえない大きさでないこと（Int に収まらない値を黙って丸めないため）
    fun intError(v: NumberInput, max: Int) = v is NumberInput.Invalid || (v is NumberInput.Value && (v.number % 1.0 != 0.0 || v.number > max))
    val minError = intError(minParsed, 9999)
    val remError = intError(remParsed, 3650)

    fun save() {
        if (nameError || minError || remError) { showErrors = true; return }
        val gear = Gear(
            id = id, name = name.trim(), category = category, brand = brand.trim(), spec = spec.trim(),
            quantity = quantity, minQuantity = (minParsed as? NumberInput.Value)?.number?.toInt(), owned = owned,
            lineChangedOn = if (category.hasLine) lineDay?.let(LocalDate::ofEpochDay) else null,
            lineReminderDays = if (category.hasLine) (remParsed as? NumberInput.Value)?.number?.toInt() else null,
            memo = memo.trim(),
        )
        vm.save(gear, onDone)
    }

    if (confirmDelete) original?.let { g ->
        ConfirmDialog("道具を消す", "「${g.name}」を消します。セットからも外れます。", "消す", onConfirm = { vm.delete(g, onDone) }, onDismiss = { confirmDelete = false })
    }

    ScreenScaffold(
        title = if (id == 0L) "道具を追加" else "道具を編集",
        onBack = back,
        actions = {
            if (original != null) IconButton(onClick = { confirmDelete = true }, modifier = Modifier.heightIn(min = TouchTarget)) { Icon(Icons.Filled.Delete, contentDescription = "この道具を消す") }
        },
        bottomBar = { if (loaded) SaveBar(onSave = ::save) },
    ) { padding ->
        if (!loaded) return@ScreenScaffold
        Column(
            Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = name, onValueChange = { name = it.take(NAME_MAX) }, label = { Text("名前（必須）") },
                isError = showErrors && nameError,
                supportingText = { if (showErrors && nameError) Text("名前を入れてください") },
                modifier = Modifier.fillMaxWidth(), singleLine = true, textStyle = MaterialTheme.typography.bodyLarge,
            )
            Text("種類", style = MaterialTheme.typography.labelLarge)
            ChoiceChips(GearCategory.entries, selected = { it == category }, label = { it.label }, onClick = { category = it })
            Text("持っているか", style = MaterialTheme.typography.labelLarge)
            ChoiceChips(listOf(true, false), selected = { it == owned }, label = { if (it) "持っている" else "欲しい物（未所持）" }, onClick = { owned = it })
            BrandField(
                value = brand, onValueChange = { brand = it.take(TEXT_MAX) },
                past = pastBrands, searchQuery = gearSearchQuery(brand, name),
            )
            OutlinedTextField(
                value = spec, onValueChange = { spec = it.take(TEXT_MAX) }, label = { Text("号数・長さ・重さなど") },
                placeholder = { Text(specHint(category)) }, modifier = Modifier.fillMaxWidth(), singleLine = true, textStyle = MaterialTheme.typography.bodyLarge,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("数", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Stepper(quantity, onChange = { quantity = it }, name = name.ifBlank { "数" })
            }
            OutlinedTextField(
                value = minQty, onValueChange = { minQty = it.take(6) },
                label = { Text("この数以下で「残り少ない」") },
                supportingText = { Text(if (showErrors && minError) "0〜9999の整数を入れてください" else "空欄なら知らせません（スナップ・オモリなどの消耗品向け）") },
                isError = showErrors && minError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(), singleLine = true, textStyle = MaterialTheme.typography.bodyLarge,
            )
            if (category.hasLine) {
                DateField(
                    label = "ラインを巻き替えた日",
                    date = lineDay?.let(LocalDate::ofEpochDay),
                    onChange = { lineDay = it?.toEpochDay() },
                    allowClear = true,
                )
                OutlinedTextField(
                    value = reminder, onValueChange = { reminder = it.take(6) },
                    label = { Text("巻き替えから何日で知らせるか") },
                    supportingText = { Text(if (showErrors && remError) "0〜3650の整数を入れてください" else "空欄なら知らせません。交換の時期は釣り方や保管で変わるため、ご自身で決めてください") },
                    isError = showErrors && remError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(), singleLine = true, textStyle = MaterialTheme.typography.bodyLarge,
                )
            }
            OutlinedTextField(value = memo, onValueChange = { memo = it.take(MEMO_MAX) }, label = { Text("メモ") }, modifier = Modifier.fillMaxWidth(), minLines = 2, textStyle = MaterialTheme.typography.bodyLarge)
            if (showErrors && (nameError || minError || remError)) Text("赤い欄を直してから保存してください", color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
        }
    }
}

/**
 * 「メーカー・商品名」の入力欄。途中まで入れると、前に登録した値と主なメーカー名から候補を出し、押すとその値が入る。
 * 下の「ネットで調べる」で、入れた言葉（と道具の名前）でネット検索を開く（全部を手で打たなくて済むように）
 */
@Composable
private fun BrandField(value: String, onValueChange: (String) -> Unit, past: List<String>, searchQuery: String) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    // 検索を開けなかった知らせ。検索語が変わったら消す
    var searchFailed by remember(searchQuery) { mutableStateOf(false) }
    val suggestions = remember(value, past) { brandSuggestions(value, past) }
    // 候補を選んだとき（や読み込んだ値が入ったとき）は、カーソルを末尾に置く（続けて打てるように）
    var field by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    if (field.text != value) field = TextFieldValue(value, TextRange(value.length))
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ExposedDropdownMenuBox(expanded = menu && suggestions.isNotEmpty(), onExpandedChange = { menu = it }) {
            OutlinedTextField(
                value = field,
                onValueChange = { field = it; if (it.text != value) { onValueChange(it.text); menu = true } },
                label = { Text("メーカー・商品名") },
                supportingText = { Text("途中まで入れると候補が出ます") },
                modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
                singleLine = true, textStyle = MaterialTheme.typography.bodyLarge,
            )
            ExposedDropdownMenu(expanded = menu && suggestions.isNotEmpty(), onDismissRequest = { menu = false }) {
                for (s in suggestions) {
                    DropdownMenuItem(
                        text = { Text(s, style = MaterialTheme.typography.bodyLarge) },
                        onClick = { onValueChange(s); menu = false },
                        modifier = Modifier.heightIn(min = TouchTarget),
                    )
                }
            }
        }
        OutlinedButton(
            onClick = { searchFailed = !openWebSearch(context, searchQuery) },
            enabled = searchQuery.isNotBlank(),
            modifier = Modifier.heightIn(min = TouchTarget),
        ) { Text("ネットで調べる") }
        Text(
            if (searchFailed) "検索を開けるアプリが見つかりませんでした"
            else "入れた言葉で、ブラウザなどの検索を開きます（言葉は検索サイトに送られます）",
            style = MaterialTheme.typography.bodySmall,
            color = if (searchFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * バックアップを保存する前の確認。最初は「パスワードで守る」をオンにしておく。
 * 人が考えたパスワードは、強さの見積もりを通っても推測されることがあるため、「作ってもらう」をおすすめとして先に出す。
 * パスワードは画面の回転で保存される領域には置かない（回すとこの確認は閉じ、保存をやり直すことになる）。
 * パスワードは64文字で切る（保存と読み込みで同じように切るので、往復は崩れない）。
 */
@Composable
private fun ExportDialog(onPlain: () -> Unit, onEncrypted: (CharArray) -> Unit, onDismiss: () -> Unit) {
    var protect by remember { mutableStateOf(true) }
    var password by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    var showErrors by remember { mutableStateOf(false) }
    // 作った物も最初は伏せ、利用者が「表示する」を押したときだけ見せる。アプリを離れたら伏せ直す
    var visible by remember { mutableStateOf(false) }
    MaskWhenLeaving { visible = false }
    // アプリが作ったパスワード。パスワードの欄がこの値のままなら「作った物」として扱う
    // （確認の欄を打ち直しても作った物のまま。パスワードの欄を書き換えたら自分で決めた物になる）。
    // 入力欄の値と同じ文字列を指すだけなので、写しは増えない
    var madeValue by remember { mutableStateOf<String?>(null) }
    val generated = madeValue != null && password == madeValue
    // パスワードの判定（強さの見積もりを含む）は画面のスレッドの外で行う。見積もりは時間がかかることがあり、
    // 1文字ごとに画面のスレッドで行うと入力が引っかかるため。判定が済むまでは保存に進めない
    var problem by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(true) }
    // どのパスワードを判定した結果か（押した時点の値と照らし、古い判定のまま進まないように）
    // 値と、作った物として判定したか（規則だけ）の組で持つ
    var checkedFor by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    LaunchedEffect(Unit) {
        // 見積もりの辞書を、入力を始める前に読み込んでおく
        withContext(Dispatchers.Default) { BackupCrypto.warmUp() }
    }
    LaunchedEffect(Unit) {
        snapshotFlow { password to (madeValue != null && password == madeValue) }.collectLatest { (p, made) ->
            checking = true
            delay(150) // 打ち続けている間は判定しない
            // アプリが作った物は規則だけで確かめる（ランダムな物なので、見積もりが使えない端末でも保存できるように）
            problem = withContext(Dispatchers.Default) { if (made) BackupCrypto.ruleProblem(p) else BackupCrypto.passwordProblem(p) }
            checkedFor = p to made
            checking = false
        }
    }
    val scope = rememberCoroutineScope()
    var generateFailed by remember { mutableStateOf(false) }
    val mismatch = password != again
    val mask = if (visible) VisualTransformation.None else PasswordVisualTransformation()
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = SecretDialogProperties,
        title = { Text("バックアップを保存") },
        text = {
            MaskWhenUnfocused(visible) { visible = false }
            NoCopyTextFields {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = TouchTarget).toggleable(value = protect, onValueChange = { protect = it; showErrors = false }, role = Role.Switch),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("パスワードで守る", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(checked = protect, onCheckedChange = null)
                }
                if (protect) {
                    Text(
                        "中身はパスワードで暗号化されます。忘れると、このバックアップは戻せません。" +
                            "パスワードは、推測されにくい物をアプリに作ってもらうのがおすすめです。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(
                        onClick = {
                            scope.launch {
                                val made = withContext(Dispatchers.Default) {
                                    try { BackupCrypto.generatePassword() } catch (e: IllegalStateException) { null }
                                }
                                if (made == null) { generateFailed = true; return@launch }
                                password = String(made); again = password; madeValue = password
                                made.fill('\u0000')
                                showErrors = false; generateFailed = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = TouchTarget),
                    ) { Text(if (generated) "別のパスワードを作ってもらう" else "パスワードを作ってもらう（おすすめ）") }
                    if (generateFailed) {
                        Text("パスワードを作れませんでした。もう一度押すか、自分で決めてください", color = MaterialTheme.colorScheme.error)
                    }
                    if (generated) {
                        Text(
                            "「パスワードを表示する」を押して、紙などに書き写してから保存してください。このパスワードは、あとから見ることはできません。" +
                                "書いた紙は、バックアップのファイルと別の場所にしまってください。",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        Text(
                            "自分で決める場合は、下に入れてください（半角英数字・記号で${BackupCrypto.PASSWORD_MIN}文字以上）。" +
                                "推測されやすい物は、強さを見積もって断ります（見積もりなので、強さを保証するものではありません）。",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it.take(64); generateFailed = false
                            // 作った物から書き換えたら、作った物の控えを手放す（使わない写しを持ち続けないため）
                            if (madeValue != null && password != madeValue) madeValue = null
                        },
                        label = { Text("パスワード") }, singleLine = true,
                        visualTransformation = mask,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        isError = showErrors && !checking && problem != null, modifier = Modifier.fillMaxWidth(),
                        // 断る理由は欄のすぐ下に出す（確認の窓の下の方に出すと、スクロールの外に隠れて気づけないため）
                        supportingText = if (showErrors && !checking && problem != null) ({ Text(problem!!) }) else null,
                    )
                    OutlinedTextField(
                        value = again, onValueChange = { again = it.take(64) },
                        label = { Text("もう一度入れる") }, singleLine = true,
                        visualTransformation = mask,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        isError = showErrors && mismatch, modifier = Modifier.fillMaxWidth(),
                        supportingText = if (showErrors && mismatch) ({ Text("2つのパスワードが違います") }) else null,
                    )
                    ShowPasswordToggle(visible) { visible = it }
                } else {
                    Text("釣り場の情報が、誰でも読める形で保存されます。保存先は人に見られない場所を選んでください。", style = MaterialTheme.typography.bodyMedium)
                }
            }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (!protect) onPlain()
                    else if (checking || checkedFor != (password to generated)) Unit // 判定の途中。済めば押せる
                    else if (problem != null || password != again) showErrors = true
                    else onEncrypted(password.toCharArray())
                },
                modifier = Modifier.heightIn(min = TouchTarget),
            ) { Text("保存先を選ぶ") }
        },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = TouchTarget)) { Text("やめる") } },
    )
}

/**
 * パスワードを扱う確認の窓では、画面の撮影と「最近使ったアプリ」の一覧への写り込みを止める（FLAG_SECURE）。
 * 表示に切り替えたパスワードが、画面の写真やその同期で端末の外へ出ないようにするため
 */
private val SecretDialogProperties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn)

/** アプリを離れたとき（ホームに戻る・別のアプリに切り替える）に呼ぶ。見せていたパスワードを伏せ直すのに使う */
@Composable
private fun MaskWhenLeaving(onLeave: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    val latest by rememberUpdatedState(onLeave)
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) latest() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}

/**
 * 中の入力欄の文字のメニューを、貼り付け・全選択・自動入力だけにし（コピー・切り取り・読み上げ・ほかのアプリへ渡す項目を外す）、クリップボードへの書き込みも止める。
 * 表示に切り替えたパスワードが、クリップボードやほかのアプリに渡り、そこから読まれるのを防ぐため。
 * Compose の新しい文字のメニューが前提（版を上げたら、表示中の長押しで「コピー」が出ないことを確かめ直す）
 */
@Composable
private fun NoCopyTextFields(content: @Composable () -> Unit) {
    val allowed = remember { setOf(TextContextMenuKeys.PasteKey, TextContextMenuKeys.SelectAllKey, TextContextMenuKeys.AutofillKey) }
    // コピーの経路（メニュー・キーボード・入力方式からの要求）は最後に必ずクリップボードへ書くので、
    // 書き込みを受け付けないクリップボードに差し替えて、どの経路でも写らないようにする（貼り付けは元のクリップボードから読む）
    val clipboard = LocalClipboard.current
    val readOnlyClipboard = remember(clipboard) {
        object : Clipboard {
            override suspend fun getClipEntry(): ClipEntry? = clipboard.getClipEntry()
            override suspend fun setClipEntry(clipEntry: ClipEntry?) = Unit
            override val nativeClipboard get() = clipboard.nativeClipboard
        }
    }
    val manager = LocalClipboardManager.current
    val readOnlyManager = remember(manager) {
        object : ClipboardManager {
            override fun getText(): AnnotatedString? = manager.getText()
            override fun setText(annotatedString: AnnotatedString) = Unit
            override fun setClip(clipEntry: ClipEntry?) = Unit
            override fun getClip(): ClipEntry? = manager.getClip()
            override fun hasText(): Boolean = manager.hasText()
            override val nativeClipboard get() = manager.nativeClipboard
        }
    }
    CompositionLocalProvider(LocalClipboard provides readOnlyClipboard, LocalClipboardManager provides readOnlyManager) {
        Box(
            Modifier
                .filterTextContextMenuComponents { it.key in allowed }
                // 切り取りは、写らなくても入力欄から文字が消えるので、キーボードの操作でも止める
                // （Ctrl+X・Shift+Delete・専用の切り取りキー。入力方式からの切り取りの要求もこのキーとして届く）
                .onPreviewKeyEvent { e ->
                    val ctrl = e.isCtrlPressed || e.isMetaPressed
                    (ctrl && e.key == Key.X) || (e.isShiftPressed && e.key == Key.Delete) || e.key == Key.Cut
                },
        ) { content() }
    }
}

/**
 * 見せていたパスワードを伏せ直す時機：この窓が操作の対象でなくなったとき（画面分割でほかのアプリに触れた・通知を引き出した等）と、
 * 見せてから1分たったとき。アプリを離れたときは MaskWhenLeaving で伏せる。確認の窓の中（text）で呼ぶ
 */
@Composable
private fun MaskWhenUnfocused(visible: Boolean, onMask: () -> Unit) {
    val windowInfo = LocalWindowInfo.current
    val latest by rememberUpdatedState(onMask)
    LaunchedEffect(windowInfo) {
        snapshotFlow { windowInfo.isWindowFocused }.collect { focused -> if (!focused) latest() }
    }
    LaunchedEffect(visible) {
        if (visible) {
            delay(REVEAL_MILLIS)
            latest()
        }
    }
}

/** パスワードを見せておく長さ（書き写すのに足り、置きっぱなしにしない長さ） */
private const val REVEAL_MILLIS = 60_000L

/** パスワードを見せるかどうかの切り替え（最初は伏せる） */
@Composable
private fun ShowPasswordToggle(visible: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = TouchTarget).toggleable(value = visible, enabled = enabled, onValueChange = onChange, role = Role.Checkbox),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = visible, onCheckedChange = null, enabled = enabled)
        Text("パスワードを表示する", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp))
    }
}

/** パスワードで守ったバックアップを開く。違うパスワードなら、その場でもう一度入れてもらう */
@Composable
private fun UnlockDialog(
    unlock: suspend (CharArray) -> BackupData,
    onUnlocked: (BackupData) -> Unit,
    onFailed: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var wrong by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }
    MaskWhenLeaving { visible = false }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = SecretDialogProperties,
        title = { Text("パスワードを入れてください") },
        text = {
            MaskWhenUnfocused(visible) { visible = false }
            NoCopyTextFields {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("このバックアップはパスワードで守られています。保存したときのパスワードを入れてください。", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = password, onValueChange = { password = it.take(64); wrong = false },
                    label = { Text("パスワード") }, singleLine = true, enabled = !busy,
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = wrong, modifier = Modifier.fillMaxWidth(),
                )
                ShowPasswordToggle(visible, enabled = !busy) { visible = it }
                if (busy) Text("確かめています…", style = MaterialTheme.typography.bodyMedium)
                // 復号できない原因は、パスワード違いとファイルの破損・書き換えを区別できないため、両方を示す
                if (wrong) Text("パスワードが違うか、ファイルが壊れています。正しいパスワードで開けないときは、別の控えをお試しください", color = MaterialTheme.colorScheme.error)
            }
            }
        },
        confirmButton = {
            Button(
                enabled = !busy && password.isNotEmpty(),
                onClick = {
                    busy = true
                    scope.launch {
                        try {
                            onUnlocked(unlock(password.toCharArray()))
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: WrongPasswordException) {
                            wrong = true
                        } catch (e: BackupFormatException) {
                            onFailed(e.message ?: "読み込めませんでした")
                        } catch (e: Exception) {
                            onFailed("読み込めませんでした")
                        } finally {
                            busy = false
                        }
                    }
                },
                modifier = Modifier.heightIn(min = TouchTarget),
            ) { Text("開く") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy, modifier = Modifier.heightIn(min = TouchTarget)) { Text("やめる") } },
    )
}
