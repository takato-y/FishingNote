@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.fishingnote.ui.prep

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.fishingnote.FishingNoteApp
import com.example.fishingnote.data.BasicItem
import com.example.fishingnote.data.ChecklistItem
import com.example.fishingnote.data.Repository
import com.example.fishingnote.data.SetWithGear
import com.example.fishingnote.domain.splitSpec
import com.example.fishingnote.ui.common.findActivity
import com.example.fishingnote.ui.common.NAME_MAX
import com.example.fishingnote.ui.common.BigButtonHeight
import com.example.fishingnote.ui.common.CheckCircle
import com.example.fishingnote.ui.common.ConfirmDialog
import com.example.fishingnote.ui.common.EmptyState
import com.example.fishingnote.ui.common.GradientCard
import com.example.fishingnote.ui.common.ScreenScaffold
import com.example.fishingnote.ui.common.TouchTarget
import com.example.fishingnote.ui.theme.Ocean
import com.example.fishingnote.ui.common.appViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PrepViewModel(private val repo: Repository) : ViewModel() {
    val items = repo.checklist.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * 持ち物リストの並び（ID の順）。画面を回しても変わらないよう、画面ではなくここに持つ。
     * タブを離れたときに空にし、次に開いたときに「まだの物が上」で並べ直す。
     */
    var fixedOrder: List<Long> = emptyList()
    val basic = repo.basicItems.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val sets = repo.sets.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun toggle(item: ChecklistItem) = viewModelScope.launch { repo.setChecked(item.id, !item.checked) }
    fun uncheckAll() = viewModelScope.launch { repo.uncheckAll() }
    fun remove(item: ChecklistItem) = viewModelScope.launch { repo.deleteChecklistItem(item.id) }
    fun restore(items: List<ChecklistItem>) = viewModelScope.launch { repo.restoreChecklistItems(items) }
    fun restoreBasic(item: BasicItem) = viewModelScope.launch { repo.restoreBasicItem(item) }
    fun add(name: String) = viewModelScope.launch { repo.addChecklistItem(name) }
    fun prepare(setId: Long?) = viewModelScope.launch { repo.prepareChecklist(setId) }
    fun addBasic(name: String) = viewModelScope.launch { repo.addBasicItem(name) }
    fun removeBasic(item: BasicItem) = viewModelScope.launch { repo.deleteBasicItem(item.id) }
}

@Composable
fun PrepScreen() {
    val vm = appViewModel { PrepViewModel(it) }
    val app = LocalContext.current.applicationContext as FishingNoteApp
    val items by vm.items.collectAsStateWithLifecycle()
    val basic by vm.basic.collectAsStateWithLifecycle()
    val sets by vm.sets.collectAsStateWithLifecycle()
    var choosing by remember { mutableStateOf(false) }
    var confirmUncheck by remember { mutableStateOf(false) }
    var newItem by rememberSaveable { mutableStateOf("") }
    var newBasic by rememberSaveable { mutableStateOf("") }
    var basicOpen by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var title by remember { mutableStateOf(app.checklistTitle) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // 続けて外した項目は、まとめて元に戻せるようにする（案内が出ている間に外した分を1つの「元に戻す」で戻す）
    val removedBatch = remember { mutableStateListOf<ChecklistItem>() }
    var undoGen by remember { mutableStateOf(0) }
    fun removeWithUndo(item: ChecklistItem) {
        vm.remove(item)
        removedBatch.add(item)
        val gen = ++undoGen
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val label = if (removedBatch.size == 1) "「${splitSpec(item.name).first.take(20)}」を外しました" else "${removedBatch.size}件を外しました"
            val r = snackbar.showSnackbar(label, actionLabel = "元に戻す", duration = SnackbarDuration.Short)
            // 後から出た案内に置き換えられた場合は、まとめた分をそちらに任せる
            if (gen != undoGen) return@launch
            if (r == SnackbarResult.ActionPerformed) vm.restore(removedBatch.toList())
            removedBatch.clear()
        }
    }
    // リストを作り直したら、前のリストの項目を戻せないよう案内を消す（戻すと項目が重なるため）
    fun forgetUndo() { undoGen++; removedBatch.clear(); snackbar.currentSnackbarData?.dismiss() }
    fun removeBasicWithUndo(item: BasicItem) {
        vm.removeBasic(item)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            if (snackbar.showSnackbar("「${item.name.take(20)}」をいつもの持ち物から消しました", actionLabel = "元に戻す", duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) {
                vm.restoreBasic(item)
            }
        }
    }

    if (choosing) ChooseSetDialog(
        sets, hasItems = !items.isNullOrEmpty(),
        onChoose = { id ->
            forgetUndo()
            vm.prepare(id)
            title = sets.firstOrNull { it.set.id == id }?.set?.name
            app.checklistTitle = title
            choosing = false
        },
        onDismiss = { choosing = false },
    )
    if (confirmUncheck) ConfirmDialog("チェックを全部外す", "次の釣行のために、すべてのチェックを外します。", "外す", onConfirm = vm::uncheckAll, onDismiss = { confirmUncheck = false })

    // 画面を回したときは並びを残し、タブを離れたときだけ捨てる（並びを固定する理由は、一覧を描くところに書く）
    val activity = LocalContext.current.findActivity()
    DisposableEffect(Unit) { onDispose { if (activity?.isChangingConfigurations != true) vm.fixedOrder = emptyList() } }

    ScreenScaffold(
        title = "出発前の準備",
        topLevel = true,
        snackbar = snackbar,
        actions = {
            if (!items.isNullOrEmpty()) TextButton(onClick = { editing = !editing }, modifier = Modifier.heightIn(min = TouchTarget)) {
                Text(if (editing) "完了" else "編集", style = MaterialTheme.typography.labelLarge)
            }
        },
    ) { padding ->
        val list = items ?: return@ScreenScaffold
        // 並び順は、画面を開いたときの「まだの物が上」で固定する。チェックした行がその場で下へ動くと、
        // 下の行が指の下へ繰り上がり、続けて押したときに別の物へチェックが付くため。
        // 済みの物が下へまとまるのは、次にこの画面を開いたとき。後から足した物は最後に並べる。
        val ids = list.map { it.id }.toSet()
        val kept = vm.fixedOrder.filter { it in ids }
        val added = list.filter { it.id !in kept.toSet() }.sortedBy { it.checked }.map { it.id }
        val order = kept + added
        SideEffect { vm.fixedOrder = order }
        val rank = order.withIndex().associate { (i, id) -> id to i }
        val ordered = list.sortedBy { rank[it.id] }
        if (list.isEmpty()) {
            EmptyState(
                "出かける前に、持ち物をチェックしましょう。\nセットを選ぶと、道具といつもの持ち物の一覧ができます。",
                Modifier.padding(padding), icon = Icons.Filled.Checklist,
            ) {
                Button(onClick = { choosing = true }, modifier = Modifier.height(BigButtonHeight)) { Text("持ち物リストを作る", style = MaterialTheme.typography.labelLarge) }
            }
            return@ScreenScaffold
        }
        LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { ProgressHero(title, list.count { it.checked }, list.size, onRebuild = { choosing = true }, onUncheck = { confirmUncheck = true }) }
            items(ordered, key = { it.id }) { item ->
                // 項目の追加・削除に動きを付け、どこが変わったか目で追えるようにする
                Box(Modifier.animateItem()) { CheckRow(item, editing, onToggle = { vm.toggle(item) }, onDelete = { removeWithUndo(item) }) }
            }
            item {
                AddRow(newItem, { newItem = it }, "今回だけ持っていく物を足す", onAdd = { vm.add(newItem); newItem = "" })
            }
            item {
                Surface(
                    onClick = { basicOpen = !basicOpen },
                    shape = MaterialTheme.shapes.medium,
                    color = Color.Transparent,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                ) {
                    Row(Modifier.heightIn(min = TouchTarget).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("いつもの持ち物（${basic.size}点）", style = MaterialTheme.typography.titleSmall)
                            Text("どのセットでも、リストに必ず入ります", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(if (basicOpen) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, contentDescription = if (basicOpen) "閉じる" else "開く")
                    }
                }
            }
            if (basicOpen) {
                items(basic, key = { "b-${it.id}" }) { b ->
                    Row(Modifier.fillMaxWidth().heightIn(min = TouchTarget), verticalAlignment = Alignment.CenterVertically) {
                        Text(b.name, modifier = Modifier.weight(1f).padding(start = 8.dp), style = MaterialTheme.typography.bodyLarge)
                        IconButton(onClick = { removeBasicWithUndo(b) }, modifier = Modifier.size(TouchTarget)) { Icon(Icons.Filled.Close, contentDescription = "「${b.name}」をいつもの持ち物から消す") }
                    }
                }
                item { AddRow(newBasic, { newBasic = it }, "いつもの持ち物を足す", onAdd = { vm.addBasic(newBasic); newBasic = "" }) }
            }
        }
    }
}

/** 進み具合。リングと大きな数字で、残りがひと目で分かるようにする */
@Composable
private fun ProgressHero(setName: String?, done: Int, total: Int, onRebuild: () -> Unit, onUncheck: () -> Unit) {
    GradientCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { if (total == 0) 0f else done.toFloat() / total },
                    modifier = Modifier.size(96.dp),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.25f),
                    strokeWidth = 9.dp,
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$done", style = MaterialTheme.typography.headlineSmall.copy(fontSize = 30.sp, lineHeight = 32.sp))
                    Text("/ $total", style = MaterialTheme.typography.labelMedium)
                }
            }
            Column(Modifier.weight(1f).padding(start = 18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(setName?.let { "${it}の準備" } ?: "今回の持ち物", style = MaterialTheme.typography.titleLarge)
                Text(if (done == total) "準備ができました。いってらっしゃい！" else "あと ${total - done}点", style = MaterialTheme.typography.bodyLarge)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            // グラデーションの上のボタンは、白い地に青い文字（屋外でも読めるコントラストにする）
            val soft = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Ocean)
            Button(onClick = onRebuild, colors = soft, modifier = Modifier.weight(1f).heightIn(min = TouchTarget)) { Text("作り直す") }
            Button(onClick = onUncheck, colors = soft, modifier = Modifier.weight(1f).heightIn(min = TouchTarget)) { Text("全部外す") }
        }
    }
}

/** チェックリストの1件。カード全体（高さ 64dp 以上）を押すとチェックが切り替わる */
@Composable
private fun CheckRow(item: ChecklistItem, editing: Boolean, onToggle: () -> Unit, onDelete: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val (name, spec) = splitSpec(item.name)
    Surface(
        onClick = onToggle,
        enabled = !editing,
        shape = MaterialTheme.shapes.medium,
        color = if (item.checked) scheme.surfaceVariant.copy(alpha = 0.6f) else scheme.surfaceContainerLowest,
        shadowElevation = if (item.checked) 0.dp else 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            CheckCircle(item.checked)
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleSmall,
                    textDecoration = if (item.checked) TextDecoration.LineThrough else null,
                    color = if (item.checked) scheme.onSurfaceVariant else scheme.onSurface,
                )
                if (spec != null) Text(spec, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
            if (editing) IconButton(onClick = onDelete, modifier = Modifier.size(TouchTarget)) {
                Icon(Icons.Filled.Delete, contentDescription = "「$name」をリストから消す", tint = scheme.error)
            }
        }
    }
}

@Composable
private fun AddRow(value: String, onChange: (String) -> Unit, label: String, onAdd: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value = value, onValueChange = { onChange(it.take(NAME_MAX)) }, label = { Text(label) }, modifier = Modifier.weight(1f), singleLine = true, shape = MaterialTheme.shapes.medium)
        Button(onClick = onAdd, enabled = value.isNotBlank(), modifier = Modifier.height(TouchTarget)) { Text("足す") }
    }
}

@Composable
private fun ChooseSetDialog(sets: List<SetWithGear>, hasItems: Boolean, onChoose: (Long?) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("どのセットで準備しますか") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (hasItems) item { Text("今のリストは作り直されます。", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp)) }
                items(sets, key = { it.set.id }) { s ->
                    TextButton(onClick = { onChoose(s.set.id) }, modifier = Modifier.fillMaxWidth().heightIn(min = TouchTarget)) {
                        Text("${s.set.name}（道具 ${s.gear.size}点）", modifier = Modifier.fillMaxWidth())
                    }
                }
                item {
                    TextButton(onClick = { onChoose(null) }, modifier = Modifier.fillMaxWidth().heightIn(min = TouchTarget)) {
                        Text("セットを使わない（いつもの持ち物だけ）", modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = TouchTarget)) { Text("やめる") } },
    )
}
