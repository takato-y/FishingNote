@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.fishingnote.ui.log

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import com.example.fishingnote.data.QuickCatch
import com.example.fishingnote.data.TripWithCatches
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import com.example.fishingnote.domain.quickSpecies
import com.example.fishingnote.ui.common.MEMO_MAX
import com.example.fishingnote.ui.common.editKey
import com.example.fishingnote.ui.common.TEXT_MAX
import com.example.fishingnote.ui.common.NAME_MAX
import com.example.fishingnote.ui.common.BigButtonHeight
import com.example.fishingnote.ui.common.ChoiceChips
import com.example.fishingnote.ui.common.AppCard
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.example.fishingnote.domain.greeting
import com.example.fishingnote.domain.monthlyFish
import com.example.fishingnote.ui.common.GradientCard
import androidx.compose.ui.unit.sp
import com.example.fishingnote.ui.common.SaveBar
import com.example.fishingnote.ui.common.rememberGuardedBack
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.example.fishingnote.ui.common.TouchTarget
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.fishingnote.data.CatchEntry
import com.example.fishingnote.data.Repository
import com.example.fishingnote.data.Trip
import com.example.fishingnote.domain.YearSummary
import com.example.fishingnote.domain.catchLine
import com.example.fishingnote.domain.formatCm
import com.example.fishingnote.domain.suggestions
import com.example.fishingnote.domain.summarize
import com.example.fishingnote.ui.common.ConfirmDialog
import com.example.fishingnote.ui.common.AddAction
import com.example.fishingnote.ui.common.DateField
import com.example.fishingnote.ui.common.EmptyState
import com.example.fishingnote.ui.common.NumberInput
import com.example.fishingnote.ui.common.ScreenScaffold
import com.example.fishingnote.ui.common.Stepper
import com.example.fishingnote.ui.common.SuggestionRow
import com.example.fishingnote.ui.common.appViewModel
import com.example.fishingnote.ui.common.label
import com.example.fishingnote.ui.common.parseNumber
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class LogViewModel(private val repo: Repository) : ViewModel() {
    val trips = repo.trips.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val sets = repo.sets.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val summary = combine(repo.trips, repo.sets) { t, s -> summarize(t, s.map { it.set }, LocalDate.now().year) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val monthly = repo.trips.map { monthlyFish(it, LocalDate.now().year) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), List(12) { 0 })

    /** 場所の候補：マイ釣り場の名前と、前に入力した場所 */
    val placeSuggestions = combine(repo.trips, repo.spots) { t, s -> suggestions(s.map { it.name } + t.map { it.trip.place }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val speciesSuggestions = repo.trips.map { t -> suggestions(t.flatMap { tr -> tr.catches.map { it.species } }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 魚のボタンの並び。記録を読み終えるまでは null（既定の並びを一瞬出してから入れ替えないため） */
    val quickButtons = repo.trips.map<List<TripWithCatches>, List<String>?> { t -> quickSpecies(t.flatMap { tr -> tr.catches.map { it.species } }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** 今日の魚ごとの数（ワンタップ記録のボタンに出す） */
    val todayCounts = repo.trips.map { t ->
        val today = LocalDate.now()
        t.filter { it.trip.date == today }.flatMap { it.catches }
            .groupBy { it.species.trim() }.mapValues { (_, v) -> v.sumOf { it.count } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /**
     * 直前のワンタップ記録（「元に戻す」に使う）。画面を回しても消えないよう、画面ではなくここに持つ。
     * 押してから8秒で消す。続けて押したときは、最後に押した分から8秒数え直す。
     */
    val lastQuick = MutableStateFlow<Pair<String, QuickCatch>?>(null)
    private var expireJob: Job? = null

    fun quickAdd(species: String) = viewModelScope.launch {
        val q = repo.addQuickCatch(species, LocalDate.now())
        lastQuick.value = species.trim() to q
        expireJob?.cancel()
        expireJob = viewModelScope.launch { delay(8_000); lastQuick.value = null }
    }

    fun undoLastQuick() {
        val q = lastQuick.value?.second ?: return
        lastQuick.value = null
        expireJob?.cancel()
        viewModelScope.launch { repo.undoQuickCatch(q) }
    }

    suspend fun load(id: Long) = repo.getTrip(id)
    suspend fun lastUsedSetId(): Long? = repo.trips.first().firstOrNull { it.trip.setId != null }?.trip?.setId
    fun save(trip: Trip, catches: List<CatchEntry>, done: () -> Unit) = viewModelScope.launch { repo.saveTrip(trip, catches); done() }
    fun delete(trip: Trip, done: () -> Unit) = viewModelScope.launch { repo.deleteTrip(trip); done() }
}

@Composable
fun LogScreen(onEdit: (Long) -> Unit) {
    val vm = appViewModel { LogViewModel(it) }
    val trips by vm.trips.collectAsStateWithLifecycle()
    val sets by vm.sets.collectAsStateWithLifecycle()
    val summary by vm.summary.collectAsStateWithLifecycle()
    val quick by vm.quickButtons.collectAsStateWithLifecycle()
    val todayCounts by vm.todayCounts.collectAsStateWithLifecycle()
    val monthly by vm.monthly.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current
    val setNames = sets.associate { it.set.id to it.set.name }
    val pastSpecies by vm.speciesSuggestions.collectAsStateWithLifecycle()
    var askOther by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    // 魚のボタンの並びは、画面を開いて最初に決まった並びで固定する。
    // 押した数で並びが入れ替わると、続けて押したときに指の下へ別の魚が来て、違う魚に足してしまうため。
    var buttons by remember { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(quick) { if (buttons == null && quick != null) buttons = quick }
    // 直前に足した魚。「元に戻す」はお知らせ（画面下）ではなくカードの中に出す（「ほかの魚」を隠さないため）
    val lastQuick by vm.lastQuick.collectAsStateWithLifecycle()

    fun quickAdd(species: String) {
        // 押したことが手に伝わるよう、軽く振動させる（画面を見ずに押しても分かる）
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        vm.quickAdd(species)
    }

    if (askOther) OtherFishDialog(
        // 候補は多い順に12件まで（増えすぎてダイアログが画面からはみ出さないように）
        past = pastSpecies.filter { it !in buttons.orEmpty() }.take(12),
        onAdd = { quickAdd(it); askOther = false },
        onDismiss = { askOther = false },
    )

    ScreenScaffold(
        title = "釣行の記録",
        topLevel = true,
        add = AddAction("詳しく記録", onClick = { onEdit(0) }),
    ) { padding ->
        val list = trips ?: return@ScreenScaffold
        LazyColumn(
            Modifier.padding(padding),
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            summary?.let { s -> item { HeroStats(todayCounts.values.sum(), s) } }
            val todayTrip = list.firstOrNull { it.trip.date == LocalDate.now() }
            buttons?.let { b ->
                item {
                    QuickCatchCard(
                        b, todayCounts, onAdd = ::quickAdd, onOther = { askOther = true },
                        lastAdded = lastQuick?.first,
                        onUndo = vm::undoLastQuick,
                        onOpenToday = todayTrip?.let { t -> { onEdit(t.trip.id) } },
                    )
                }
            }
            if (list.isEmpty()) item {
                Text("記録はまだありません。釣れたら上のボタンを押すだけで、今日の記録に残ります。場所やメモは「詳しく記録」から書けます。", style = MaterialTheme.typography.bodyLarge)
            }
            summary?.takeIf { list.isNotEmpty() && (it.topSpecies.isNotEmpty() || it.bySet.isNotEmpty()) }?.let { s -> item { SummaryCard(s, monthly) } }
            if (list.isNotEmpty()) item { Text("これまでの記録", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
            items(list, key = { it.trip.id }) { t ->
                AppCard(onClick = { onEdit(t.trip.id) }) {
                  Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    DateTile(t.trip.date)
                    Column(Modifier.weight(1f).padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        // 見出しは釣果にする（ワンタップで残した記録も「足りない物」ではなく釣れた魚が目に入るように）
                        Text(catchLine(t), style = MaterialTheme.typography.titleMedium)
                        Text(
                            t.trip.place.ifBlank { "場所を書き足す" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (t.trip.place.isBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        t.trip.setId?.let(setNames::get)?.let {
                            Text("セット：$it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                  }
                }
            }
        }
    }
}

/** 釣り場で片手で押す前提の、大きな魚のボタン（2列・高さ 76dp） */
@Composable
private fun QuickCatchCard(
    species: List<String>,
    todayCounts: Map<String, Int>,
    onAdd: (String) -> Unit,
    onOther: () -> Unit,
    lastAdded: String?,
    onUndo: () -> Unit,
    onOpenToday: (() -> Unit)?,
) {
    AppCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("釣れたら押す", style = MaterialTheme.typography.titleLarge)
            // 説明の行は高さを固定し、押した直後だけ「元に戻す」に差し替える。
            // 魚のボタンのすぐ上に出るので見落とさず、出たり消えたりしてもボタンの位置は動かない。
            Box(Modifier.fillMaxWidth().height(TouchTarget), contentAlignment = Alignment.CenterStart) {
                if (lastAdded == null) {
                    Text("押すたびに今日の記録へ1匹足します", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("${lastAdded}を1匹足しました", style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        OutlinedButton(onClick = onUndo, modifier = Modifier.height(TouchTarget)) {
                            Text("元に戻す", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
            species.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { sp ->
                        Button(
                            onClick = { onAdd(sp) },
                            modifier = Modifier.weight(1f).height(76.dp),
                            shape = MaterialTheme.shapes.large,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(sp, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                AnimatedContent(
                                    targetState = todayCounts[sp] ?: 0,
                                    transitionSpec = { (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut()) },
                                    label = "count",
                                ) { n -> Text("今日 ${n}匹", style = MaterialTheme.typography.labelMedium) }
                            }
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            OutlinedButton(onClick = onOther, modifier = Modifier.fillMaxWidth().height(BigButtonHeight)) {
                Text("ほかの魚", style = MaterialTheme.typography.labelLarge)
            }
            // 今日の記録をそのまま開く（新しい記録を作らずに、場所や大きさを書き足せるように）
            onOpenToday?.let { open ->
                TextButton(onClick = open, modifier = Modifier.fillMaxWidth().heightIn(min = TouchTarget)) {
                    Text("今日の記録に場所・大きさを書き足す", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun OtherFishDialog(past: List<String>, onAdd: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ほかの魚を1匹足す") },
        text = {
            // 文字を大きくした端末や横向きでも「足す」まで届くよう、中身をスクロールできるようにする
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // 前に記録した魚は押すだけで足せる（濡れた手でキーボードを打たずに済むように）
                if (past.isNotEmpty()) {
                    Text("前に記録した魚（押すと足します）", style = MaterialTheme.typography.bodyMedium)
                    SuggestionRow(past, onPick = onAdd)
                }
                OutlinedTextField(value = name, onValueChange = { name = it.take(NAME_MAX) }, label = { Text("魚の種類") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = { onAdd(name) }, enabled = name.isNotBlank(), modifier = Modifier.height(TouchTarget)) { Text("足す") }
        },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.height(TouchTarget)) { Text("やめる") } },
    )
}

@Composable
private fun SummaryCard(s: YearSummary, monthly: List<Int>) {
    AppCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("月ごとの釣果", style = MaterialTheme.typography.titleSmall)
            MonthlyBars(monthly)
            if (s.topSpecies.isNotEmpty()) {
                Text("よく釣れた魚", style = MaterialTheme.typography.labelLarge)
                s.topSpecies.forEach { sp ->
                    Text("・${sp.species} ${sp.count}匹" + (sp.maxSizeCm?.let { "（最大 ${formatCm(it)}cm）" } ?: ""))
                }
            }
            if (s.bySet.any { it.setName != "セットなし" }) {
                Text("セットごとの釣果", style = MaterialTheme.typography.labelLarge)
                s.bySet.forEach { st -> Text("・${st.setName}：${st.trips}回で ${st.fish}匹") }
            }
        }
    }
}

/** 画面の先頭に出す、今日と今年の数（大きな数字で、ひと目で分かるようにする） */
@Composable
private fun HeroStats(today: Int, s: YearSummary) {
    GradientCard {
        Text(greeting(java.time.LocalTime.now().hour), style = MaterialTheme.typography.titleLarge)
        Text(
            when {
                today > 0 -> "今日はもう ${today}匹。いい調子です"
                s.biggest != null -> "今年いちばんの大物：${s.biggest.species} ${formatCm(s.biggest.maxSizeCm!!)}cm"
                else -> "今日も良い釣りを"
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            Stat("今日", "$today", "匹", animate = true)
            Stat("今年", "${s.fishCount}", "匹")
            Stat("釣行", "${s.tripCount}", "回")
        }
    }
}

@Composable
private fun Stat(label: String, value: String, unit: String, animate: Boolean = false) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Row(verticalAlignment = Alignment.Bottom) {
            val big = MaterialTheme.typography.headlineSmall.copy(fontSize = 40.sp, lineHeight = 44.sp)
            if (animate) AnimatedContent(
                targetState = value,
                transitionSpec = { (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut()) },
                label = "stat",
            ) { v -> Text(v, style = big) } else Text(value, style = big)
            Text(unit, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 2.dp, bottom = 4.dp))
        }
    }
}

/** 編集中の釣果の1行。数字は入力途中の文字のまま持つ */
private data class CatchRow(val species: String = "", val size: String = "", val count: Int = 1)

@Composable
fun TripEditScreen(id: Long, onDone: () -> Unit) {
    val vm = appViewModel { LogViewModel(it) }
    val sets by vm.sets.collectAsStateWithLifecycle()
    val places by vm.placeSuggestions.collectAsStateWithLifecycle()
    val species by vm.speciesSuggestions.collectAsStateWithLifecycle()
    // 読み込み済みの印も保存する（画面の回転などで作り直されたとき、入力中の内容を DB の値で上書きしないため）
    var loaded by rememberSaveable { mutableStateOf(id == 0L) }
    var original by remember { mutableStateOf<Trip?>(null) }
    LaunchedEffect(id) { if (id != 0L && original == null) original = vm.load(id)?.trip }
    var day by rememberSaveable { mutableStateOf(LocalDate.now().toEpochDay()) }
    var place by rememberSaveable { mutableStateOf("") }
    var setId by rememberSaveable { mutableStateOf<Long?>(null) }
    var memo by rememberSaveable { mutableStateOf("") }
    // 釣果の行も画面の作り直しで消えないよう、文字の並びにして保存する
    val rows = rememberSaveable(
        saver = listSaver<SnapshotStateList<CatchRow>, String>(
            save = { list -> list.flatMap { listOf(it.species, it.size, it.count.toString()) } },
            restore = { flat -> mutableStateListOf<CatchRow>().apply { flat.chunked(3).forEach { add(CatchRow(it[0], it[1], it[2].toInt())) } } },
        ),
    ) { mutableStateListOf<CatchRow>() }
    var showErrors by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var initial by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(id) {
        if (id != 0L && !loaded) {
            vm.load(id)?.let { t ->
                original = t.trip; day = t.trip.date.toEpochDay(); place = t.trip.place; setId = t.trip.setId; memo = t.trip.memo
                rows.clear(); rows.addAll(t.catches.map { CatchRow(it.species, it.sizeCm?.let(::formatCm).orEmpty(), it.count) })
            }
            loaded = true
        } else if (id == 0L && initial == null) {
            // 新しい記録は、前回使ったセットを最初から選んでおく
            setId = vm.lastUsedSetId()
        }
        // 回転などで作り直されたときは、復元した入力を「最初の値」にしない（未保存の確認が効かなくなるため）
        if (initial == null) initial = editKey(day, place, setId, memo, rows.map { listOf(it.species, it.size, it.count) })
    }
    val back = rememberGuardedBack(
        dirty = initial != null && editKey(day, place, setId, memo, rows.map { listOf(it.species, it.size, it.count) }) != initial,
        onLeave = onDone,
    )

    /** 候補の魚を押したとき、同じ魚の行があれば数を足し、無ければ行を足す */
    fun addSpecies(sp: String) {
        val i = rows.indexOfFirst { it.species.trim() == sp }
        if (i >= 0) rows[i] = rows[i].copy(count = rows[i].count + 1) else rows.add(CatchRow(species = sp))
    }

    // 大きさは 500cm まで（打ち間違いの 99999cm などを、最大の記録として残さないため）
    fun rowError(r: CatchRow) = r.species.isBlank() || parseNumber(r.size).let { it is NumberInput.Invalid || (it is NumberInput.Value && it.number > 500) }

    fun save() {
        if (rows.any(::rowError)) { showErrors = true; return }
        val trip = Trip(id = id, date = LocalDate.ofEpochDay(day), place = place.trim(), setId = setId, memo = memo.trim())
        val catches = rows.map {
            CatchEntry(tripId = id, species = it.species.trim(), sizeCm = (parseNumber(it.size) as? NumberInput.Value)?.number, count = it.count)
        }
        vm.save(trip, catches, onDone)
    }

    if (confirmDelete) original?.let { t ->
        ConfirmDialog("記録を消す", "${t.date.label()} の記録を消します。", "消す", onConfirm = { vm.delete(t, onDone) }, onDismiss = { confirmDelete = false })
    }

    ScreenScaffold(
        title = if (id == 0L) "釣行を記録" else "記録を編集",
        onBack = back,
        actions = { if (original != null) IconButton(onClick = { confirmDelete = true }, modifier = Modifier.heightIn(min = TouchTarget)) { Icon(Icons.Filled.Delete, contentDescription = "この記録を消す") } },
        // 「最初の値」が決まるまでは入力欄を出さない（前回のセットを読む間に入力されると、
        // その入力が「最初の値」になり、保存せずに戻るときの確認が出なくなるため）
        bottomBar = { if (loaded && initial != null) SaveBar(onSave = ::save) },
    ) { padding ->
        if (!loaded || initial == null) return@ScreenScaffold
        LazyColumn(
            Modifier.padding(padding).fillMaxSize().imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { DateField("日付", LocalDate.ofEpochDay(day), onChange = { d -> d?.let { day = it.toEpochDay() } }) }
            item {
                OutlinedTextField(value = place, onValueChange = { place = it.take(NAME_MAX) }, label = { Text("場所") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
            item { SuggestionRow(places.filter { it != place }) { place = it } }
            item {
                Text("使ったセット", style = MaterialTheme.typography.labelLarge)
                ChoiceChips(
                    options = listOf<Long?>(null) + sets.map { it.set.id },
                    selected = { it == setId },
                    label = { o -> if (o == null) "なし" else sets.first { it.set.id == o }.set.name },
                    onClick = { setId = it },
                )
            }
            item { Text("釣れた魚（${rows.sumOf { it.count }}匹）", style = MaterialTheme.typography.titleSmall) }
            itemsIndexed(rows) { i, r ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = r.species, onValueChange = { rows[i] = r.copy(species = it.take(NAME_MAX)) },
                                label = { Text("魚の種類") }, singleLine = true, modifier = Modifier.weight(1f),
                                isError = showErrors && r.species.isBlank(),
                            )
                            IconButton(onClick = { rows.removeAt(i) }, modifier = Modifier.size(TouchTarget)) { Icon(Icons.Filled.Close, contentDescription = "「${r.species}」の行を消す") }
                        }
                        // 狭い画面や文字を大きくした設定でも窮屈にならないよう、縦に並べる
                        OutlinedTextField(
                            value = r.size, onValueChange = { rows[i] = r.copy(size = it.take(6)) },
                            label = { Text("最大の大きさ cm（空欄でも可）") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                            isError = showErrors && parseNumber(r.size).let { it is NumberInput.Invalid || (it is NumberInput.Value && it.number > 500) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("数", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Stepper(r.count, onChange = { rows[i] = r.copy(count = it) }, min = 1, label = "${r.count}匹", name = r.species.ifBlank { "魚" })
                        }
                    }
                }
            }
            if (showErrors && rows.any(::rowError)) item {
                Text("魚の種類を入れ、大きさは 500 までの数字で入れてください（空欄でも可）", color = MaterialTheme.colorScheme.error)
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SuggestionRow(species, onPick = ::addSpecies)
                    OutlinedButton(onClick = { rows.add(CatchRow()) }, modifier = Modifier.fillMaxWidth().height(BigButtonHeight)) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Text("魚を追加")
                    }
                }
            }
            item { OutlinedTextField(value = memo, onValueChange = { memo = it.take(MEMO_MAX) }, label = { Text("メモ（潮・天気・ヒットしたルアーなど）") }, modifier = Modifier.fillMaxWidth(), minLines = 2) }
        }
    }
}

/** 月ごとの釣れた数の棒グラフ。今月の棒だけ濃い色にする */
@Composable
private fun MonthlyBars(values: List<Int>) {
    // 先の月の空の棒で間延びさせないよう、今月までを出す
    val thisMonth = LocalDate.now().monthValue - 1
    val shown = values.take(thisMonth + 1)
    val max = (shown.maxOrNull() ?: 0).coerceAtLeast(1)
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().height(120.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
        shown.forEachIndexed { i, v ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                if (v > 0) Text("$v", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                Box(
                    Modifier.fillMaxWidth().height((80f * v / max).coerceAtLeast(4f).dp)
                        .background(if (i == thisMonth) scheme.primary else scheme.primaryContainer, RoundedCornerShape(6.dp))
                )
                Text("${i + 1}", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            }
        }
    }
}

/** 記録のカードの左に置く日付（日を大きく） */
@Composable
private fun DateTile(date: LocalDate) {
    Column(
        Modifier.size(56.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(16.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("${date.monthValue}月", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Text("${date.dayOfMonth}", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
    }
}
