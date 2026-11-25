package com.example.fishingnote.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Backpack
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Phishing
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.SetMeal
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.example.fishingnote.FishingNoteApp
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.fishingnote.ui.gear.GearEditScreen
import com.example.fishingnote.ui.gear.GearListScreen
import com.example.fishingnote.ui.log.LogScreen
import com.example.fishingnote.ui.log.TripEditScreen
import com.example.fishingnote.ui.prep.PrepScreen
import com.example.fishingnote.ui.sets.SetEditScreen
import com.example.fishingnote.ui.sets.SetListScreen
import com.example.fishingnote.ui.spots.SpotEditScreen
import com.example.fishingnote.ui.spots.SpotListScreen

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    GEAR("gear", "道具", Icons.Filled.Inventory2),
    SETS("sets", "セット", Icons.Filled.Backpack),
    PREP("prep", "準備", Icons.Filled.Checklist),
    LOG("log", "記録", Icons.Filled.SetMeal),
    SPOTS("spots", "釣り場", Icons.Filled.Map),
}

/** タブの切り替え。タブごとの画面の状態（スクロール位置など）を残す */
private fun NavController.openTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

private fun idArg() = listOf(navArgument("id") { type = NavType.LongType })

@Composable
fun AppNav(shareRequests: Int = 0) {
    val nav = rememberNavController()

    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination?.route
    val showTabs = Tab.entries.any { it.route == current }
    val app = LocalContext.current.applicationContext as FishingNoteApp
    // 共有を受け取ったら釣り場の保存画面を開く。ただし編集中（タブの画面でない）は切り替えず、
    // 保存か破棄で一覧に戻ったときに開く（編集中の入力を確認なしに失わないため）
    LaunchedEffect(shareRequests, current) {
        if (current != null && showTabs && app.pendingShare != null) {
            nav.openTab(Tab.SPOTS.route)
            nav.navigate("spot/0")
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showTabs) NavigationBar {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = current == tab.route,
                        onClick = { nav.openTab(tab.route) },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Tab.GEAR.route, modifier = Modifier.padding(padding)) {
            composable(Tab.GEAR.route) { GearListScreen(onEdit = { nav.navigate("gear/$it") }) }
            composable("gear/{id}", idArg()) { GearEditScreen(it.arguments!!.getLong("id"), onDone = { nav.popBackStack() }) }
            composable(Tab.SETS.route) { SetListScreen(onEdit = { nav.navigate("set/$it") }, onPrepared = { nav.openTab(Tab.PREP.route) }) }
            composable("set/{id}", idArg()) { SetEditScreen(it.arguments!!.getLong("id"), onDone = { nav.popBackStack() }) }
            composable(Tab.PREP.route) { PrepScreen() }
            composable(Tab.LOG.route) { LogScreen(onEdit = { nav.navigate("trip/$it") }) }
            composable("trip/{id}", idArg()) { TripEditScreen(it.arguments!!.getLong("id"), onDone = { nav.popBackStack() }) }
            composable(Tab.SPOTS.route) { SpotListScreen(onEdit = { nav.navigate("spot/$it") }) }
            composable("spot/{id}", idArg()) { SpotEditScreen(it.arguments!!.getLong("id"), onDone = { nav.popBackStack() }) }
        }
    }
}
