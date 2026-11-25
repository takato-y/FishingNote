@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.fishingnote.ui.sets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.fishingnote.data.Repository
import com.example.fishingnote.data.SetWithGear
import com.example.fishingnote.data.TackleSet
import androidx.compose.ui.draw.scale
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.filled.Search
import com.example.fishingnote.domain.GearFilter
import com.example.fishingnote.domain.filterGear
import com.example.fishingnote.ui.common.IconTile
import androidx.compose.material.icons.filled.Backpack
import com.example.fishingnote.ui.common.EmptyState
import com.example.fishingnote.ui.common.ScreenScaffold
import com.example.fishingnote.ui.common.appViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SetViewModel(private val repo: Repository) : ViewModel() {
    val sets = repo.sets.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val ownedGear = repo.gear.map { list -> list.filter { it.owned } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun load(id: Long) = repo.getSet(id)
    fun save(set: TackleSet, gearIds: Set<Long>, done: () -> Unit) = viewModelScope.launch { repo.saveSet(set, gearIds); done() }
    fun delete(set: TackleSet, done: () -> Unit) = viewModelScope.launch { repo.deleteSet(set); done() }
    fun prepare(setId: Long, done: () -> Unit) = viewModelScope.launch { repo.prepareChecklist(setId); done() }
}

@Composable
fun SetListScreen(onEdit: (Long) -> Unit, onPrepared: () -> Unit) {
    val vm = appViewModel { SetViewModel(it) }
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as com.example.fishingnote.FishingNoteApp
    val sets by vm.sets.collectAsStateWithLifecycle()
    var confirmPrepare by remember { mutableStateOf<SetWithGear?>(null) }
    val listState = rememberLazyListState()
    // 前に見たセットの id。編集画面から戻っても残るよう rememberSaveable に置く
    var knownIds by rememberSaveable { mutableStateOf<List<Long>?>(null) }

    confirmPrepare?.let { s ->
        ConfirmDialog(
            "セットで準備",
            "「${s.set.name}」の道具と、いつもの持ち物でチェックリストを作り直します。今のチェックは消えます。",
            "作り直す",
            onConfirm = { app.checklistTitle = s.set.name; vm.prepare(s.set.id, onPrepared) },
            onDismiss = { confirmPrepare = null },
        )
    }

    ScreenScaffold(
        title = "セット",
        topLevel = true,
        add = AddAction("セットを作る", onClick = { onEdit(0) }),
    ) { padding ->
        val list = sets ?: return@ScreenScaffold
        if (list.isEmpty()) {
            EmptyState(
                "釣りの種類ごとに道具をまとめておくと、出かける前のチェックリストがすぐ作れます。\n例：「アジング」「堤防サビキ」「ショアジギング」",
                Modifier.padding(padding), icon = Icons.Filled.Backpack,
            )
            return@ScreenScaffold
        }
        // 一覧は名前順のため、新しいセットが今見ている位置より上に入ると画面の外に隠れる。増えたセットまで送る
        LaunchedEffect(list) {
            val ids = list.map { it.set.id }
            val prev = knownIds
            knownIds = ids
            if (prev != null) {
                val added = list.indexOfFirst { it.set.id !in prev }
                if (added >= 0) listState.animateScrollToItem(added)
            }
        }
        LazyColumn(
            Modifier.padding(padding),
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(list, key = { it.set.id }) { s ->
                AppCard(onClick = { onEdit(s.set.id) }) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconTile(Icons.Filled.Backpack)
                            Column(Modifier.padding(start = 14.dp)) {
                                Text(s.set.name, style = MaterialTheme.typography.titleMedium)
                                Text("道具 ${s.gear.size}点", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Text(
                            if (s.gear.isEmpty()) "道具が入っていません"
                            else s.gear.sortedBy { it.category.ordinal }.joinToString("・") { it.name },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                        if (s.set.memo.isNotBlank()) Text(s.set.memo, style = MaterialTheme.typography.bodySmall)
                        FilledTonalButton(onClick = { confirmPrepare = s }, modifier = Modifier.fillMaxWidth().height(BigButtonHeight)) {
                            Text("このセットで準備する")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SetEditScreen(id: Long, onDone: () -> Unit) {
    val vm = appViewModel { SetViewModel(it) }
    val gear by vm.ownedGear.collectAsStateWithLifecycle()
    // 読み込み済みの印も保存する（画面の回転などで作り直されたとき、入力中の内容を DB の値で上書きしないため）
    var loaded by rememberSaveable { mutableStateOf(id == 0L) }
    var original by remember { mutableStateOf<TackleSet?>(null) }
    LaunchedEffect(id) { if (id != 0L && original == null) original = vm.load(id)?.set }
    var name by rememberSaveable { mutableStateOf("") }
    var memo by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf(listOf<Long>()) }
    var search by rememberSaveable { mutableStateOf("") }
    var showErrors by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var initial by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(id) {
        if (id != 0L && !loaded) {
            vm.load(id)?.let { s -> original = s.set; name = s.set.name; memo = s.set.memo; selected = s.gear.map { it.id } }
            loaded = true
        }
    }
    val snapshot = editKey(name, memo, selected.sorted())
    LaunchedEffect(loaded) { if (loaded && initial == null) initial = snapshot }
    val back = rememberGuardedBack(dirty = initial != null && snapshot != initial, onLeave = onDone)

    fun save() {
        if (name.isBlank()) { showErrors = true; return }
        vm.save(TackleSet(id = id, name = name.trim(), memo = memo.trim()), selected.toSet(), onDone)
    }

    if (confirmDelete) original?.let { s ->
        ConfirmDialog("セットを消す", "「${s.name}」を消します。道具そのものは消えません。", "消す", onConfirm = { vm.delete(s, onDone) }, onDismiss = { confirmDelete = false })
    }

    ScreenScaffold(
        title = if (id == 0L) "セットを作る" else "セットを編集",
        onBack = back,
        actions = { if (original != null) IconButton(onClick = { confirmDelete = true }, modifier = Modifier.heightIn(min = TouchTarget)) { Icon(Icons.Filled.Delete, contentDescription = "このセットを消す") } },
        bottomBar = { if (loaded) SaveBar(onSave = ::save) },
    ) { padding ->
        if (!loaded) return@ScreenScaffold
        LazyColumn(
            Modifier.padding(padding).fillMaxSize().imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(NAME_MAX) }, label = { Text("セットの名前（必須）") },
                    placeholder = { Text("例：アジング") },
                    isError = showErrors && name.isBlank(),
                    supportingText = { if (showErrors && name.isBlank()) Text("名前を入れてください") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                )
            }
            item { OutlinedTextField(value = memo, onValueChange = { memo = it.take(MEMO_MAX) }, label = { Text("メモ") }, modifier = Modifier.fillMaxWidth()) }
            item { Text("入れる道具（${selected.size}点）", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp)) }
            if (gear.isEmpty()) item { Text("持っている道具がありません。先に「道具」タブで登録してください。") }
            if (gear.size > 6) item {
                OutlinedTextField(
                    value = search, onValueChange = { search = it }, placeholder = { Text("道具を絞り込む") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth(),
                )
            }
            filterGear(gear, search, GearFilter.ALL, java.time.LocalDate.now()).groupBy { it.category }.toSortedMap().forEach { (category, list) ->
                item(key = "h-${category.name}") {
                    Text(category.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                items(list, key = { it.id }) { g ->
                    val checked = g.id in selected
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 64.dp)
                            .clickable { selected = if (checked) selected - g.id else selected + g.id },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(horizontal = 8.dp).scale(1.3f))
                        Column(Modifier.padding(start = 8.dp)) {
                            Text(g.name, style = MaterialTheme.typography.bodyLarge)
                            if (g.spec.isNotBlank()) Text(g.spec, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
