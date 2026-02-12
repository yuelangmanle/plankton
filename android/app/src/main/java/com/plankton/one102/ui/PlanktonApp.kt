package com.plankton.one102.ui

import android.app.Activity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.TableView
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.material3.TextButton
import kotlinx.coroutines.flow.collect
import android.widget.Toast
import com.plankton.one102.ui.screens.DatasetsScreen
import com.plankton.one102.ui.screens.DatabaseScreen
import com.plankton.one102.ui.screens.DatabaseMindMapScreen
import com.plankton.one102.ui.screens.DatabaseTreeScreen
import com.plankton.one102.ui.screens.PointsScreen
import com.plankton.one102.ui.screens.PreviewScreen
import com.plankton.one102.ui.screens.ProjectDocsScreen
import com.plankton.one102.ui.screens.SettingsScreen
import com.plankton.one102.ui.screens.SpeciesScreen
import com.plankton.one102.ui.screens.AssistantScreen
import com.plankton.one102.ui.screens.WetWeightLibraryScreen
import com.plankton.one102.ui.screens.FocusCountScreen
import com.plankton.one102.ui.screens.AliasScreen
import com.plankton.one102.ui.screens.AiCacheScreen
import com.plankton.one102.ui.screens.ChartsScreen
import com.plankton.one102.ui.screens.SpeciesEditScreen
import com.plankton.one102.ui.screens.OpsCenterScreen
import com.plankton.one102.ui.components.GlobalAssistantOverlay
import com.plankton.one102.ui.components.GlassPrefs
import com.plankton.one102.ui.components.LocalGlassPrefs
import com.plankton.one102.ui.components.GlassCard
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.plankton.one102.domain.UiMode
import com.plankton.one102.ui.theme.GlassBorder
import com.plankton.one102.ui.theme.GlassShadow
import com.plankton.one102.ui.theme.LocalDensityTokens
import com.plankton.one102.ui.theme.LocalDesignTokens
import com.plankton.one102.ui.theme.AppDesignTokens
import com.plankton.one102.ui.theme.densityTokens

private enum class Tab(val route: String, val label: String) {
    Points("points", "采样点"),
    Species("species", "物种"),
    Assistant("assistant", "助手"),
    Preview("preview", "导出"),
}

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3WindowSizeClassApi::class)
fun PlanktonApp(viewModel: MainViewModel = viewModel()) {
    val navController = rememberNavController()
    val wetWeightsViewModel: WetWeightsViewModel = viewModel()
    val databaseViewModel: DatabaseViewModel = viewModel()
    val aliasViewModel: AliasViewModel = viewModel()
    val aiCacheViewModel: AiCacheViewModel = viewModel()
    val tabs = listOf(Tab.Points, Tab.Species, Tab.Assistant, Tab.Preview)
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val voicePayload by viewModel.voiceAssistantPayload.collectAsStateWithLifecycle()
    val canUndo by viewModel.canUndo.collectAsStateWithLifecycle()
    val canRedo by viewModel.canRedo.collectAsStateWithLifecycle()
    val taskFeedback by viewModel.taskFeedback.collectAsStateWithLifecycle()

    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.readOnlyEvents.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    val activity = LocalContext.current as? Activity
    val windowSize = activity?.let { calculateWindowSizeClass(it) }
    val autoUseRail = (windowSize?.widthSizeClass ?: WindowWidthSizeClass.Compact) >= WindowWidthSizeClass.Medium
    val useRail = when (settings.uiMode) {
        UiMode.Auto -> autoUseRail
        UiMode.Phone -> false
        UiMode.Tablet -> true
    }
    val mainRoutes = tabs.map { it.route }.toSet()
    val isMainRoute = currentDestination?.route in mainRoutes
    val isFocusRoute = currentDestination?.route == "focus"
    val isSpeciesEditRoute = currentDestination?.route?.startsWith("species/edit") == true
    var topMenuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(voicePayload?.requestId) {
        if (voicePayload?.requestMatched == true && currentDestination?.route != Tab.Species.route) {
            navController.navigate(Tab.Species.route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    CompositionLocalProvider(
        LocalGlassPrefs provides GlassPrefs(
            enabled = settings.glassEffectEnabled,
            blur = settings.blurEnabled,
            opacity = settings.glassOpacity,
        ),
        LocalDensityTokens provides densityTokens(settings.uiDensityMode),
        LocalDesignTokens provides AppDesignTokens(),
    ) {
        val densityTokens = LocalDensityTokens.current
        Scaffold(
            topBar = {
                if (!isFocusRoute) {
                    TopAppBar(
                        title = {
                            Text(
                                "浮游动物一体化",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                            if (isSpeciesEditRoute || currentDestination?.route in setOf("datasets", "wetweights", "database", "database/tree", "database/mindmap", "settings", "aliases", "aicache", "charts", "docs", "ops") || currentDestination?.route?.startsWith("docs/") == true) {
                                IconButton(onClick = { navController.popBackStack() }) {
                                    Icon(imageVector = Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                                }
                            }
                        },
                        actions = {
                            IconButton(onClick = { navController.navigate("datasets") { launchSingleTop = true } }) {
                                Icon(imageVector = Icons.Outlined.Folder, contentDescription = "历史数据集")
                            }
                            IconButton(onClick = { navController.navigate("wetweights") { launchSingleTop = true } }) {
                                Icon(imageVector = Icons.Outlined.Search, contentDescription = "湿重库")
                            }
                            IconButton(onClick = { topMenuExpanded = true }) {
                                Icon(imageVector = Icons.Outlined.MoreVert, contentDescription = "更多")
                            }
                            DropdownMenu(
                                expanded = topMenuExpanded,
                                onDismissRequest = { topMenuExpanded = false },
                            ) {
                                if (isMainRoute) {
                                    DropdownMenuItem(
                                        text = { Text("撤销") },
                                        enabled = canUndo,
                                        onClick = {
                                            topMenuExpanded = false
                                            viewModel.undoDatasetEdit()
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("重做") },
                                        enabled = canRedo,
                                        onClick = {
                                            topMenuExpanded = false
                                            viewModel.redoDatasetEdit()
                                        },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("数据库") },
                                    onClick = {
                                        topMenuExpanded = false
                                        navController.navigate("database") { launchSingleTop = true }
                                    },
                                    leadingIcon = { Icon(imageVector = Icons.Outlined.Storage, contentDescription = null) },
                                )
                                DropdownMenuItem(
                                    text = { Text("设置") },
                                    onClick = {
                                        topMenuExpanded = false
                                        navController.navigate("settings") { launchSingleTop = true }
                                    },
                                    leadingIcon = { Icon(imageVector = Icons.Outlined.Settings, contentDescription = null) },
                                )
                                DropdownMenuItem(
                                    text = { Text("运营中心") },
                                    onClick = {
                                        topMenuExpanded = false
                                        navController.navigate("ops") { launchSingleTop = true }
                                    },
                                    leadingIcon = { Icon(imageVector = Icons.Outlined.SmartToy, contentDescription = null) },
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(),
                    )
                }
            },
            bottomBar = {
                if (!useRail && isMainRoute) {
                    val navShape = RoundedCornerShape(densityTokens.cardCorner)
                    val navTint = if (settings.glassEffectEnabled) {
                        val baseAlpha = if (settings.blurEnabled) 0.4f else 0.8f
                        val opacity = settings.glassOpacity.coerceIn(0.5f, 1.5f)
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = (baseAlpha * opacity).coerceIn(0.25f, 0.95f))
                    } else {
                        MaterialTheme.colorScheme.surface
                    }
                    val itemColors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        indicatorColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                        unselectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        unselectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = (12.dp * densityTokens.scale),
                                vertical = (8.dp * densityTokens.scale),
                            )
                            .then(
                                if (settings.glassEffectEnabled) {
                                    Modifier
                                        .shadow(
                                            (6.dp * densityTokens.scale),
                                            navShape,
                                            spotColor = GlassShadow,
                                            ambientColor = GlassShadow,
                                        )
                                        .clip(navShape)
                                        .border(1.dp, GlassBorder, navShape)
                                } else {
                                    Modifier.clip(navShape)
                                },
                            )
                            .background(navTint),
                    ) {
                        NavigationBar(
                            containerColor = Color.Transparent,
                            tonalElevation = 0.dp,
                            windowInsets = NavigationBarDefaults.windowInsets,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            tabs.forEach { tab ->
                                val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = {
                                        navController.navigate(tab.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = {
                                        val icon = when (tab) {
                                            Tab.Points -> Icons.Outlined.TableView
                                            Tab.Species -> Icons.Outlined.Edit
                                            Tab.Assistant -> Icons.Outlined.SmartToy
                                            Tab.Preview -> Icons.Outlined.Analytics
                                        }
                                        Icon(imageVector = icon, contentDescription = tab.label)
                                    },
                                    label = { Text(tab.label) },
                                    colors = if (settings.glassEffectEnabled) itemColors else NavigationBarItemDefaults.colors(),
                                )
                            }
                        }
                    }
                }
            },
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.fillMaxSize()) {
                    if (useRail && isMainRoute) {
                        NavigationRail(modifier = Modifier.padding(top = innerPadding.calculateTopPadding())) {
                            tabs.forEach { tab ->
                                val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = {
                                        navController.navigate(tab.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = {
                                        val icon = when (tab) {
                                            Tab.Points -> Icons.Outlined.TableView
                                            Tab.Species -> Icons.Outlined.Edit
                                            Tab.Assistant -> Icons.Outlined.SmartToy
                                            Tab.Preview -> Icons.Outlined.Analytics
                                        }
                                        Icon(imageVector = icon, contentDescription = tab.label)
                                    },
                                    label = { Text(tab.label) },
                                )
                            }
                        }
                    }

                    NavHost(
                        navController = navController,
                        startDestination = Tab.Points.route,
                        modifier = Modifier
                            .weight(1f)
                            .padding(innerPadding)
                            .padding(horizontal = if (useRail) 12.dp else 0.dp),
                    ) {
                        composable(Tab.Points.route) { PointsScreen(viewModel, PaddingValues()) }
                        composable(Tab.Species.route) {
                            SpeciesScreen(
                                viewModel,
                                databaseViewModel,
                                PaddingValues(),
                                onOpenFocus = { navController.navigate("focus") },
                                onEditSpecies = { id -> navController.navigate("species/edit/$id") },
                            )
                        }
                        composable(Tab.Assistant.route) {
                            AssistantScreen(
                                viewModel,
                                PaddingValues(),
                                onOpenSpecies = {
                                    navController.navigate(Tab.Species.route) {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                onOpenPoints = {
                                    navController.navigate(Tab.Points.route) {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                onOpenFocus = { navController.navigate("focus") },
                                onOpenSpeciesEdit = { id -> navController.navigate("species/edit/$id") },
                            )
                        }
                        composable(Tab.Preview.route) { PreviewScreen(viewModel, PaddingValues(), onOpenCharts = { navController.navigate("charts") }) }
                        composable("datasets") { DatasetsScreen(viewModel, PaddingValues()) }
                        composable("wetweights") { WetWeightLibraryScreen(viewModel, wetWeightsViewModel, PaddingValues()) }
                        composable("database") {
                            DatabaseScreen(
                                viewModel,
                                databaseViewModel,
                                PaddingValues(),
                                onOpenTree = { navController.navigate("database/tree") },
                                onOpenMindMap = { navController.navigate("database/mindmap") },
                            )
                        }
                        composable("database/tree") { DatabaseTreeScreen(databaseViewModel, PaddingValues()) }
                        composable("database/mindmap") { DatabaseMindMapScreen(databaseViewModel, PaddingValues()) }
                        composable("settings") {
                            SettingsScreen(
                                viewModel,
                                PaddingValues(),
                                onOpenAliases = { navController.navigate("aliases") },
                                onOpenAiCache = { navController.navigate("aicache") },
                                onOpenDocs = { navController.navigate("docs") },
                                onOpenOps = { navController.navigate("ops") },
                            )
                        }
                        composable("docs") {
                            ProjectDocsScreen(
                                padding = PaddingValues(),
                                onOpenDoc = { id -> navController.navigate("docs/$id") },
                                onClose = { navController.popBackStack() },
                            )
                        }
                        composable("docs/{id}") { backStackEntry ->
                            val id = backStackEntry.arguments?.getString("id") ?: return@composable
                            ProjectDocsScreen(
                                padding = PaddingValues(),
                                docId = id,
                                onOpenDoc = { nextId ->
                                    navController.navigate("docs/$nextId") { launchSingleTop = true }
                                },
                                onClose = { navController.popBackStack() },
                            )
                        }
                        composable("focus") {
                            FocusCountScreen(
                                viewModel,
                                PaddingValues(),
                                onClose = { navController.popBackStack() },
                                onEditSpecies = { id -> navController.navigate("species/edit/$id") },
                            )
                        }
                        composable("aliases") { AliasScreen(aliasViewModel, PaddingValues()) }
                        composable("aicache") { AiCacheScreen(aiCacheViewModel, PaddingValues()) }
                        composable("charts") { ChartsScreen(viewModel, PaddingValues()) }
                        composable("ops") {
                            OpsCenterScreen(
                                viewModel = viewModel,
                                padding = PaddingValues(),
                                onOpenFocus = { navController.navigate("focus") },
                                onOpenAssistant = {
                                    navController.navigate(Tab.Assistant.route) {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                onOpenPreview = {
                                    navController.navigate(Tab.Preview.route) {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                onOpenCharts = { navController.navigate("charts") },
                                onOpenDatabase = { navController.navigate("database") },
                                onOpenDatasets = { navController.navigate("datasets") },
                            )
                        }
                        composable("species/edit/{id}") { backStackEntry ->
                            val id = backStackEntry.arguments?.getString("id") ?: return@composable
                            SpeciesEditScreen(
                                viewModel = viewModel,
                                databaseViewModel = databaseViewModel,
                                padding = PaddingValues(),
                                speciesId = id,
                                onClose = { navController.popBackStack() },
                            )
                        }
                    }
                }

                if (taskFeedback.title.isNotBlank() && !isFocusRoute) {
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .align(androidx.compose.ui.Alignment.TopCenter),
                        elevation = 0.dp,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            if (taskFeedback.running) {
                                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(end = 2.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                val titleColor = when (taskFeedback.level) {
                                    com.plankton.one102.domain.IssueLevel.Error -> MaterialTheme.colorScheme.error
                                    com.plankton.one102.domain.IssueLevel.Warn -> MaterialTheme.colorScheme.tertiary
                                    com.plankton.one102.domain.IssueLevel.Info -> MaterialTheme.colorScheme.onBackground
                                }
                                Text(taskFeedback.title, style = MaterialTheme.typography.titleSmall, color = titleColor)
                                if (taskFeedback.detail.isNotBlank()) {
                                    Text(
                                        taskFeedback.detail,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            if (!taskFeedback.running) {
                                TextButton(onClick = { viewModel.clearTaskFeedback() }) { Text("关闭") }
                            }
                        }
                    }
                }

                GlobalAssistantOverlay(
                    viewModel = viewModel,
                    databaseViewModel = databaseViewModel,
                    modifier = Modifier.fillMaxSize(),
                    onNavigate = { route ->
                        if (route in mainRoutes) {
                            navController.navigate(route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        } else {
                            navController.navigate(route) { launchSingleTop = true }
                        }
                    },
                )
            }
        }
    }
}
