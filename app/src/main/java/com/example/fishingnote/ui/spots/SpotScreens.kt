@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.fishingnote.ui.spots

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.fishingnote.data.Repository
import com.example.fishingnote.data.Spot
import com.example.fishingnote.domain.NEARBY_SEARCHES
import com.example.fishingnote.domain.mapQuery
import com.example.fishingnote.domain.parseSharedPlace
import com.example.fishingnote.domain.isMapUrl
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import com.example.fishingnote.ui.common.MEMO_MAX
import com.example.fishingnote.ui.common.editKey
import com.example.fishingnote.ui.common.TEXT_MAX
import com.example.fishingnote.ui.common.NAME_MAX
import com.example.fishingnote.ui.common.BigButtonHeight
import com.example.fishingnote.ui.common.SaveBar
import com.example.fishingnote.ui.common.TouchTarget
import com.example.fishingnote.ui.common.rememberGuardedBack
import com.example.fishingnote.ui.common.ConfirmDialog
import com.example.fishingnote.ui.common.AddAction
import com.example.fishingnote.ui.common.AppCard
import com.example.fishingnote.ui.theme.Ocean
import androidx.compose.foundation.lazy.rememberLazyListState
import com.example.fishingnote.ui.common.IconTile
import com.example.fishingnote.ui.common.GradientCard
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.graphics.Color
import com.example.fishingnote.ui.common.ScreenScaffold
import com.example.fishingnote.ui.common.appViewModel
import com.example.fishingnote.ui.common.openMap
import com.example.fishingnote.ui.common.openNearby
import com.example.fishingnote.ui.common.findApproxLocation
import com.example.fishingnote.ui.common.LocationFailure
import com.example.fishingnote.ui.common.LocationResult
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.os.SystemClock
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.DisposableEffect
import com.example.fishingnote.ui.common.hasApproxLocation
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SpotViewModel(private val repo: Repository) : ViewModel() {
    val spots = repo.spots.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    suspend fun load(id: Long) = repo.getSpot(id)
    fun save(spot: Spot, done: () -> Unit) = viewModelScope.launch { repo.saveSpot(spot); done() }
    fun delete(spot: Spot, done: () -> Unit) = viewModelScope.launch { repo.deleteSpot(spot); done() }
}

private const val NO_MAP_APP = "地図アプリが見つかりません。Google マップなどを入れてください"

@Composable
fun SpotListScreen(onEdit: (Long) -> Unit) {
    val vm = appViewModel { SpotViewModel(it) }
    val spots by vm.spots.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    fun open(query: String) {
        if (!openMap(context, query)) scope.launch { snackbar.showSnackbar(NO_MAP_APP) }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    fun openAppSettings() {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
    }
    // 探している検索語。画面を回してもプロセスが終わっても消えないよう rememberSaveable に置き、
    // 値がある間は下の LaunchedEffect が現在地を取って地図を開く（回した後は取り直す）
    var searching by rememberSaveable { mutableStateOf<String?>(null) }
    var searchingAt by rememberSaveable { mutableStateOf(0L) }
    var pendingTerm by rememberSaveable { mutableStateOf<String?>(null) }
    // 地図アプリの前面にいる間に出すと見えないため、この画面に戻ってきたときに出す案内
    var notice by rememberSaveable { mutableStateOf<String?>(null) }
    var noticeForSettings by rememberSaveable { mutableStateOf(false) }
    val locating = searching != null

    fun showNotice(message: String, settings: Boolean) = scope.launch {
        val action = if (settings) "設定" else null
        if (snackbar.showSnackbar(message, actionLabel = action, duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) openAppSettings()
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) notice?.let { m -> showNotice(m, noticeForSettings); notice = null }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(searching) {
        val term = searching ?: return@LaunchedEffect
        // 別のタブへ移って時間がたってから戻ったときに、前の検索を勝手に再開しない
        if (SystemClock.elapsedRealtime() - searchingAt > 20_000) { searching = null; return@LaunchedEffect }
        val result = findApproxLocation(context)
        searching = null
        // 待っている間にアプリを離れていたら、裏から地図を開けない（Android が止める）ため、取り消して戻ったときに知らせる
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            notice = "アプリを離れたため「$term」の検索を取り消しました。もう一度押してください"
            noticeForSettings = false
            return@LaunchedEffect
        }
        val opened = when (result) {
            is LocationResult.Found -> openNearby(context, term, result.location.latitude, result.location.longitude)
            is LocationResult.Failed -> openMap(context, term)
        }
        if (!opened) { showNotice(NO_MAP_APP, false); return@LaunchedEffect }
        if (result is LocationResult.Failed) {
            notice = when (result.reason) {
                LocationFailure.NO_PERMISSION -> "位置情報の権限がオフのため、現在地を使わずに探しました"
                LocationFailure.LOCATION_OFF -> "端末の位置情報がオフのため、現在地を使わずに探しました"
                LocationFailure.NOT_FOUND -> "現在地を取れなかったため、現在地を使わずに探しました"
            }
            noticeForSettings = result.reason == LocationFailure.NO_PERMISSION
        }
    }
    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
        // 断られても、現在地を使わない方法で探す
        searchingAt = SystemClock.elapsedRealtime()
        searching = pendingTerm
        pendingTerm = null
    }
    fun onSearch(term: String) {
        if (locating || pendingTerm != null) return
        if (hasApproxLocation(context)) { searchingAt = SystemClock.elapsedRealtime(); searching = term }
        else { pendingTerm = term; askLocation.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }
    }

    ScreenScaffold(
        title = "釣り場",
        topLevel = true,
        snackbar = snackbar,
        add = AddAction("釣り場を保存", onClick = { onEdit(0) }),
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding),
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val list = spots
            // 保存した釣り場があれば、それを先に出す（よく使う「保存した場所を開く」をスクロールなしで押せるように）
            val mineFirst = !list.isNullOrEmpty()
            fun LazyListScope.mySpots() {
                item(key = "mine-title") { Text("マイ釣り場", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = if (mineFirst) 0.dp else 8.dp)) }
                if (list != null && list.isEmpty()) item(key = "mine-empty") {
                    Text(
                        "気に入った場所を保存しておくと、次からワンタップで地図を開けます。\n" +
                            "今いる場所を保存するには、地図アプリで場所を開いて「共有」を押し、このアプリを選んでください。",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(list.orEmpty(), key = { it.id }) { s ->
                    AppCard(onClick = { onEdit(s.id) }) {
                        Row(Modifier.padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconTile(Icons.Filled.Place)
                            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                                Text(s.name, style = MaterialTheme.typography.titleMedium)
                                if (s.memo.isNotBlank()) Text(s.memo, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                            }
                            FilledTonalButton(onClick = { open(mapQuery(s.name, s.query)) }, modifier = Modifier.height(TouchTarget)) {
                                Icon(Icons.Filled.Place, contentDescription = null)
                                Text("地図")
                            }
                        }
                    }
                }
            }
            fun LazyListScope.searchCard() {
                item(key = "search") {
                    GradientCard {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            // 待っている間は見出しだけを差し替える（説明文の行数を変えず、ボタンの位置を動かさない）
                            Text(
                                if (locating) "現在地を確認しています…" else "近くの釣り場を地図で探す",
                                style = MaterialTheme.typography.titleMedium, maxLines = 1,
                            )
                            Text(
                                "押すと、今いるおおよその場所を Google マップに渡して近くを探します（入っていない端末では、選んだ地図アプリに渡します）。このアプリには保存しません。渡した後の扱いは、地図アプリの設定によります。",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            // 押す対象は 2列の大きなボタンにする（横に流さず、全部見えるようにする）
                            NEARBY_SEARCHES.chunked(2).forEach { pair ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    pair.forEach { term ->
                                        Button(
                                            onClick = { onSearch(term) },
                                            enabled = !locating,
                                            // ダークモードでも読めるよう、白い地に濃い青の文字で固定する
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color.White, contentColor = Ocean,
                                                disabledContainerColor = Color.White.copy(alpha = 0.6f), disabledContentColor = Ocean.copy(alpha = 0.6f),
                                            ),
                                            // 文字を大きくした端末で2行になっても切れないよう、高さは最小だけ決める
                                            modifier = Modifier.weight(1f).heightIn(min = BigButtonHeight),
                                            // 文字を大きくしても語の途中で折り返さないよう、左右の余白を標準より詰める
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                                        ) {
                                            Icon(Icons.Filled.Search, contentDescription = null)
                                            Text(term, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 6.dp))
                                        }
                                    }
                                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                            Text(
                                "釣り禁止・立入禁止の場所があります。現地の看板や管理者の案内に従ってください。",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
            // 読み終える前に並べると、読み終えて並びが入れ替わったとき、一覧が「探す」カードの位置を保って途中から表示されるため、
            // 保存した釣り場を読み終えてから並べる
            if (list != null) { if (mineFirst) { mySpots(); searchCard() } else { searchCard(); mySpots() } }
        }
    }
}

@Composable
fun SpotEditScreen(id: Long, onDone: () -> Unit) {
    val vm = appViewModel { SpotViewModel(it) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    // 読み込み済みの印も保存する（画面の回転などで作り直されたとき、入力中の内容を DB の値で上書きしないため）
    var loaded by rememberSaveable { mutableStateOf(id == 0L) }
    var original by remember { mutableStateOf<Spot?>(null) }
    LaunchedEffect(id) { if (id != 0L && original == null) original = vm.load(id) }
    var name by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var memo by rememberSaveable { mutableStateOf("") }
    var showErrors by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var initial by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(id) {
        if (id != 0L && !loaded) {
            vm.load(id)?.let { s -> original = s; name = s.name; query = s.query; memo = s.memo }
            loaded = true
        }
    }
    // 地図アプリの「共有」で届いた場所があれば、名前と地図の値を入れておく（1回だけ読む）
    val app = context.applicationContext as com.example.fishingnote.FishingNoteApp
    // 共有の値を入れてから「最初の値」を決める（共有で開いただけで「変更あり」と扱わないため）。
    // 回転などで作り直されたときは、どちらも済んでいるので何もしない
    LaunchedEffect(loaded) {
        if (!loaded || initial != null) return@LaunchedEffect
        if (id == 0L) app.pendingShare?.let { text ->
            app.pendingShare = null
            val (n, q) = parseSharedPlace(text)
            if (name.isBlank()) name = n
            query = q
        }
        initial = editKey(name, query, memo)
    }
    val snapshot = editKey(name, query, memo)
    val back = rememberGuardedBack(dirty = initial != null && snapshot != initial, onLeave = onDone)

    fun save() {
        if (name.isBlank()) { showErrors = true; return }
        vm.save(Spot(id = id, name = name.trim(), query = query.trim(), memo = memo.trim()), onDone)
    }

    if (confirmDelete) original?.let { s ->
        ConfirmDialog("釣り場を消す", "「${s.name}」を消します。", "消す", onConfirm = { vm.delete(s, onDone) }, onDismiss = { confirmDelete = false })
    }

    ScreenScaffold(
        title = if (id == 0L) "釣り場を保存" else "釣り場を編集",
        onBack = back,
        snackbar = snackbar,
        actions = { if (original != null) IconButton(onClick = { confirmDelete = true }, modifier = Modifier.heightIn(min = TouchTarget)) { Icon(Icons.Filled.Delete, contentDescription = "この釣り場を消す") } },
        bottomBar = { if (loaded && initial != null) SaveBar(onSave = ::save) },
    ) { padding ->
        if (!loaded || initial == null) return@ScreenScaffold
        Column(
            Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = name, onValueChange = { name = it.take(NAME_MAX) }, label = { Text("名前（必須）") },
                placeholder = { Text("例：北側の堤防の先端") },
                isError = showErrors && name.isBlank(),
                supportingText = { if (showErrors && name.isBlank()) Text("名前を入れてください") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
            )
            OutlinedTextField(
                value = query, onValueChange = { query = it.take(TEXT_MAX * 3) },
                label = { Text(if (isMapUrl(query)) "地図のリンク" else "地図で探す住所・施設名") },
                placeholder = { Text("例：○○漁港 / 35.3,139.5") },
                supportingText = {
                    Text(
                        if (isMapUrl(query)) "地図アプリから受け取った場所です。「地図で確かめる」で開けます"
                        else "空欄なら名前で探します。緯度経度は地図アプリで場所を長押しすると確かめられます"
                    )
                },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
            )
            OutlinedButton(
                onClick = { if (!openMap(context, mapQuery(name, query))) scope.launch { snackbar.showSnackbar(NO_MAP_APP) } },
                enabled = name.isNotBlank() || query.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(BigButtonHeight),
            ) { Text("地図で確かめる", style = MaterialTheme.typography.labelLarge) }
            OutlinedTextField(
                value = memo, onValueChange = { memo = it.take(MEMO_MAX) }, label = { Text("メモ（駐車場・トイレ・よく釣れる時間など）") },
                modifier = Modifier.fillMaxWidth(), minLines = 3,
            )
        }
    }
}
