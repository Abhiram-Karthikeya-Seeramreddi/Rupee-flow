@file:OptIn(ExperimentalMaterial3Api::class)

package com.rupeeflow.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.rupeeflow.app.notify.Notifier
import com.rupeeflow.app.ui.components.LocalMoney
import com.rupeeflow.app.ui.components.MoneyFmt
import com.rupeeflow.app.ui.screens.AccountsScreen
import com.rupeeflow.app.ui.screens.ActivityScreen
import com.rupeeflow.app.ui.screens.AddRequest
import com.rupeeflow.app.ui.screens.AddTransactionScreen
import com.rupeeflow.app.ui.screens.BillsScreen
import com.rupeeflow.app.ui.screens.CategoriesScreen
import com.rupeeflow.app.ui.screens.DataScreen
import com.rupeeflow.app.ui.screens.HomeScreen
import com.rupeeflow.app.ui.screens.ImportScreen
import com.rupeeflow.app.ui.screens.InsightsScreen
import com.rupeeflow.app.ui.screens.OnboardingScreen
import com.rupeeflow.app.ui.screens.PlanScreen
import com.rupeeflow.app.ui.screens.PlanTab
import com.rupeeflow.app.ui.screens.RulesScreen
import com.rupeeflow.app.ui.screens.SettingsScreen
import com.rupeeflow.app.ui.theme.Rf
import com.rupeeflow.core.TxType

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("home", "Home", Icons.Outlined.Home),
    Tab("activity", "Activity", Icons.AutoMirrored.Outlined.ReceiptLong),
    Tab("budgets", "Plan", Icons.Outlined.Savings),
    Tab("bills", "Bills", Icons.Outlined.CalendarMonth),
    Tab("insights", "Insights", Icons.Outlined.Insights),
)

@Composable
fun RupeeFlowRoot(
    vm: MainViewModel,
    pendingAction: String?,
    onActionConsumed: () -> Unit,
    locked: Boolean,
    onUnlock: () -> Unit,
) {
    val settings by vm.settings.collectAsState()
    val snap by vm.snapshot.collectAsState()
    val s = settings
    val fmt = MoneyFmt(s?.indianGrouping ?: true, s?.hideAmounts ?: false)

    CompositionLocalProvider(LocalMoney provides fmt) {
        when {
            s != null && s.appLock && locked -> LockScreen(onUnlock)
            s == null || snap == null -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            !s.onboarded -> OnboardingScreen(vm, snap!!)
            else -> MainScaffold(vm, pendingAction, onActionConsumed)
        }
    }
}

@Composable
private fun MainScaffold(vm: MainViewModel, pendingAction: String?, onActionConsumed: () -> Unit) {
    val snapState by vm.snapshot.collectAsState()
    val snap = snapState ?: return
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route?.substringBefore("?") ?: "home"
    val snackbar = remember { SnackbarHostState() }
    fun openAdd(req: AddRequest) {
        vm.addRequest = req
        nav.navigate("add") { launchSingleTop = true }
    }

    LaunchedEffect(Unit) {
        vm.messages.collect { m ->
            val r = snackbar.showSnackbar(m.text, m.actionLabel, withDismissAction = m.actionLabel == null, duration = SnackbarDuration.Short)
            if (r == SnackbarResult.ActionPerformed) m.action?.invoke()
        }
    }
    LaunchedEffect(pendingAction) {
        when (pendingAction) {
            Notifier.ACTION_QUICK_ADD -> openAdd(AddRequest())
            Notifier.ACTION_VOICE_ADD -> openAdd(AddRequest(startVoice = true))
            Notifier.ACTION_OPEN_BILLS -> nav.goTab("bills")
            Notifier.ACTION_OPEN_BUDGETS -> nav.goTab("budgets")
            Notifier.ACTION_OPEN_INSIGHTS -> nav.goTab("insights")
            "INCOME" -> openAdd(AddRequest(type = TxType.INCOME))
        }
        if (pendingAction != null) onActionConsumed()
    }

    val isTab = tabs.any { it.route == route }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (isTab) NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                tabs.forEach { t ->
                    NavigationBarItem(
                        selected = route == t.route,
                        onClick = { nav.goTab(t.route) },
                        icon = { Icon(t.icon, null) },
                        label = { Text(t.label, fontSize = 11.sp) },
                    )
                }
            }
        },
        floatingActionButton = {
            AnimatedVisibility(isTab, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SmallFloatingActionButton(
                        onClick = { openAdd(AddRequest(startVoice = true)) },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ) { Icon(Icons.Default.Mic, "Add by voice") }
                    FloatingActionButton(
                        onClick = { openAdd(AddRequest()) },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = RoundedCornerShape(20.dp),
                    ) { Icon(Icons.Default.Add, "Add expense") }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = "home") {
            composable("home") {
                HomeScreen(vm, snap, onAdd = { openAdd(it) }, onEdit = { openAdd(AddRequest(editId = it)) }, navigate = { nav.goTo(it) }, contentPadding = padding)
            }
            composable("activity?cat={cat}", arguments = listOf(navArgument("cat") { type = NavType.StringType; nullable = true; defaultValue = null })) { e ->
                ActivityScreen(vm, snap, onEdit = { openAdd(AddRequest(editId = it)) }, contentPadding = padding, initialCategory = e.arguments?.getString("cat"))
            }
            composable("budgets?tab={tab}", arguments = listOf(navArgument("tab") { type = NavType.StringType; nullable = true; defaultValue = null })) { e ->
                val tab = e.arguments?.getString("tab")?.let { t -> PlanTab.entries.firstOrNull { it.name == t } } ?: PlanTab.BUDGETS
                PlanScreen(vm, snap, padding, tab)
            }
            composable("bills") { BillsScreen(vm, snap, padding) }
            composable("insights") {
                InsightsScreen(vm, snap, padding, onCategory = { nav.navigate("activity?cat=$it") }, onEdit = { openAdd(AddRequest(editId = it)) })
            }
            composable("settings") { SettingsScreen(vm, snap, padding, navigate = { nav.goTo(it) }, onBack = { nav.popBackStack() }) }
            composable("accounts") { AccountsScreen(vm, snap, padding) { nav.popBackStack() } }
            composable("categories") { CategoriesScreen(vm, snap, padding) { nav.popBackStack() } }
            composable("rules") { RulesScreen(vm, snap, padding) { nav.popBackStack() } }
            composable("data") { DataScreen(vm, snap, padding) { nav.popBackStack() } }
            composable("import") { ImportScreen(vm, snap, padding) { nav.popBackStack() } }
            composable("add") { entry ->
                // Capture the request once per screen so later state changes can't swap or drop it.
                val req = remember(entry) { vm.addRequest }
                // Only ever remove the Add screen itself; never pop past it (that left a blank screen).
                val closeAdd = { nav.popBackStack("add", inclusive = true); Unit }
                if (req == null) LaunchedEffect(entry) { closeAdd() }
                else AddTransactionScreen(vm, snap, req, onClose = closeAdd)
            }
        }
    }

}

private fun NavHostController.goTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun NavHostController.goTo(route: String) {
    if (tabs.any { it.route == route }) goTab(route) else navigate(route) { launchSingleTop = true }
}

@Composable
fun LockScreen(onUnlock: () -> Unit) {
    LaunchedEffect(Unit) { onUnlock() }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🔒", fontSize = 56.sp)
            Spacer(Modifier.height(12.dp))
            Text("RupeeFlow is locked", style = MaterialTheme.typography.titleLarge)
            Text("Your finances stay private.", style = MaterialTheme.typography.bodyMedium, color = Rf.colors.subtle)
            Spacer(Modifier.height(20.dp))
            Button(onClick = onUnlock, modifier = Modifier.padding(8.dp)) { Text("Unlock") }
        }
    }
}
