package com.roxi.player.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.roxi.player.MainViewModel
import com.roxi.player.R
import com.roxi.player.ui.components.LocalBottomPadding
import com.roxi.player.ui.components.LocalVm
import com.roxi.player.ui.player.MiniPlayerHeight
import com.roxi.player.ui.player.PlayerSheet
import com.roxi.player.ui.player.rememberPlayerSheetState
import com.roxi.player.ui.screens.EqualizerScreen
import com.roxi.player.ui.screens.ExploreScreen
import com.roxi.player.ui.screens.StatsScreen
import com.roxi.player.ui.screens.HomeScreen
import com.roxi.player.ui.screens.LibraryScreen
import com.roxi.player.ui.screens.ProfileScreen
import com.roxi.player.ui.screens.SongListScreen
import com.roxi.player.ui.screens.VideoScreen
import com.roxi.player.ui.theme.Roxi
import com.roxi.player.ui.theme.RoxiTheme
import com.roxi.player.ui.theme.AccentColor
import com.roxi.player.ui.theme.roxiPalette
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

private val audioPermission =
    if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
    else Manifest.permission.READ_EXTERNAL_STORAGE

private fun hasAudioPermission(context: Context) =
    ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED

@Composable
fun RoxiApp() {
    val vm: MainViewModel = viewModel()
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasAudioPermission(context)) }
    var asked by rememberSaveable { mutableStateOf(false) }

    val permissions = remember {
        when {
            Build.VERSION.SDK_INT >= 34 -> arrayOf(audioPermission, Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED, Manifest.permission.POST_NOTIFICATIONS)
            Build.VERSION.SDK_INT >= 33 -> arrayOf(audioPermission, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.POST_NOTIFICATIONS)
            Build.VERSION.SDK_INT <= 28 -> arrayOf(audioPermission, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            else -> arrayOf(audioPermission)
        }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        asked = true
        granted = hasAudioPermission(context)
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { granted = hasAudioPermission(context) }
    LaunchedEffect(Unit) {
        val notifMissing = Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS,
        ) != PackageManager.PERMISSION_GRANTED
        if (!granted || notifMissing) launcher.launch(permissions)
    }
    LaunchedEffect(granted) { if (granted) vm.onPermissionGranted() }

    // Thème : couleur d'accent + clair / sombre, appliqués en direct
    val themeMode by vm.prefs.themeMode.collectAsStateWithLifecycle()
    val accent by vm.prefs.accent.collectAsStateWithLifecycle()
    val systemDark = isSystemInDarkTheme()
    val dark = when (themeMode) {
        1 -> false
        2 -> systemDark
        else -> true
    }
    val selectedAccent = AccentColor.entries.getOrElse(accent) { AccentColor.VIOLET }
    val windowBg = remember(selectedAccent, dark) { roxiPalette(selectedAccent, dark).bg.toArgb() }
    LaunchedEffect(accent, dark) {
        (context as? ComponentActivity)?.let { activity ->
            val transparent = android.graphics.Color.TRANSPARENT
            activity.enableEdgeToEdge(
                statusBarStyle = if (dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent),
                navigationBarStyle = if (dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent),
            )
            activity.window.decorView.setBackgroundColor(windowBg)
        }
    }

    var showSplash by rememberSaveable { mutableStateOf(true) }

    CompositionLocalProvider(LocalVm provides vm) {
        RoxiTheme(accent = selectedAccent, dark = dark) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Roxi.Bg)
            ) {
                if (granted) MainScaffold() else PermissionScreen(asked) { launcher.launch(permissions) }
                if (showSplash) SplashOverlay { showSplash = false }
            }
        }
    }
}

/** Animation d'entrée courte et stable : le titre reste toujours sur une seule ligne. */
@Composable
private fun SplashOverlay(onFinished: () -> Unit) {
    val title = remember { Animatable(1f) }
    val image = remember { Animatable(0f) }
    val overlay = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        // Titre visible dès la première image ; aucune attente artificielle.
        image.animateTo(1f, tween(280))
        overlay.animateTo(0f, tween(160))
        onFinished()
    }
    Column(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = overlay.value }
            .background(Roxi.Bg)
            // bloque les touches pendant l'animation
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "Roxi Player",
            fontSize = 34.sp,
            fontWeight = FontWeight.SemiBold,
            color = Roxi.Text,
            modifier = Modifier.graphicsLayer {
                alpha = title.value
                translationY = (1f - title.value) * 18.dp.toPx()
            },
        )
        Spacer(Modifier.height(28.dp))
        Image(
            painterResource(R.drawable.roxi_splash),
            contentDescription = null,
            modifier = Modifier
                .size(180.dp)
                .graphicsLayer {
                    val v = image.value
                    alpha = v.coerceIn(0f, 1f)
                    scaleX = 0.6f + 0.4f * v
                    scaleY = 0.6f + 0.4f * v
                    translationY = (1f - v) * 40.dp.toPx()
                },
        )
    }
}

// ------------------------------------------------------------------ navigation

private data class TabItem(val route: String, val label: String, val selected: ImageVector, val normal: ImageVector)

private val tabs = listOf(
    TabItem("home", "Accueil", Icons.Rounded.Home, Icons.Outlined.Home),
    TabItem("explore", "Explorer", Icons.Rounded.Explore, Icons.Outlined.Explore),
    TabItem("library", "Musique", Icons.Rounded.LibraryMusic, Icons.Outlined.LibraryMusic),
    TabItem("videos", "Vidéos", Icons.Rounded.VideoLibrary, Icons.Outlined.VideoLibrary),
    TabItem("profile", "Moi", Icons.Rounded.Person, Icons.Outlined.Person),
)

private fun NavBackStackEntry.baseRoute(): String? = destination.route?.substringBefore('?')?.substringBefore('/')

private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch(): Boolean {
    val names = tabs.map { it.route }
    return initialState.baseRoute() in names && targetState.baseRoute() in names
}

/** Changement d'onglet : garde l'état de chaque onglet, pas d'empilement. */
fun NavController.navigateTab(route: String, restore: Boolean = true) {
    // Ignore les doubles appuis et les changements pendant une transition.
    val entry = currentBackStackEntry ?: return
    if (entry.destination.route == route) return
    if (entry.lifecycle.currentState != Lifecycle.State.RESUMED) return
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = restore
    }
}

@Composable
private fun MainScaffold() {
    val vm = LocalVm.current
    val nav = rememberNavController()
    val current by vm.currentSong.collectAsStateWithLifecycle()
    val sheet = rememberPlayerSheetState()
    val density = LocalDensity.current

    val navInset = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
    val barTotal = 64.dp + navInset
    val barTotalPx = with(density) { barTotal.toPx() }
    val bottomPad = barTotal + if (current != null) MiniPlayerHeight + 12.dp else 0.dp

    Box(
        Modifier
            .fillMaxSize()
            .background(Roxi.Bg)
    ) {
        CompositionLocalProvider(LocalBottomPadding provides bottomPad) {
            RoxiNavHost(nav)
        }
        current?.let { song ->
            PlayerSheet(sheet, song, collapsedBottom = barTotal) {
                sheet.collapse()
                nav.navigate("equalizer") { launchSingleTop = true }
            }
        }
        BottomBar(
            nav,
            Modifier
                .align(Alignment.BottomCenter)
                .offset { IntOffset(0, (sheet.fraction * barTotalPx).roundToInt()) }
                .height(barTotal),
        )
    }
}

@Composable
private fun RoxiNavHost(nav: NavHostController) {
    val duration = 260
    NavHost(
        navController = nav,
        startDestination = "home",
        modifier = Modifier
            .fillMaxSize()
            .background(Roxi.Bg),
        enterTransition = {
            if (isTabSwitch()) EnterTransition.None
            else slideInHorizontally(tween(duration)) { it }
        },
        exitTransition = {
            if (isTabSwitch()) ExitTransition.None
            else slideOutHorizontally(tween(duration)) { -it / 4 }
        },
        popEnterTransition = {
            if (isTabSwitch()) EnterTransition.None
            else slideInHorizontally(tween(duration)) { -it / 4 }
        },
        popExitTransition = {
            if (isTabSwitch()) ExitTransition.None
            else slideOutHorizontally(tween(duration)) { it }
        },
    ) {
        composable("home") { HomeScreen(nav) }
        composable("explore") { ExploreScreen(nav) }
        composable(
            "library?tab={tab}",
            arguments = listOf(navArgument("tab") { type = NavType.IntType; defaultValue = 0 }),
        ) { entry -> LibraryScreen(nav, entry.arguments?.getInt("tab") ?: 0) }
        composable("profile") { ProfileScreen(nav) }
        composable("videos") { VideoScreen() }
        composable("stats") { StatsScreen(nav) }
        composable("equalizer") { EqualizerScreen(nav) }
        composable(
            "songs/{kind}?key={key}",
            arguments = listOf(
                navArgument("kind") { type = NavType.StringType },
                navArgument("key") { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) { entry ->
            SongListScreen(
                nav,
                kind = entry.arguments?.getString("kind") ?: "all",
                key = entry.arguments?.getString("key"),
            )
        }
    }
}

@Composable
private fun BottomBar(nav: NavController, modifier: Modifier) {
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.baseRoute()
    Column(
        modifier
            .fillMaxWidth()
            .background(Roxi.Bg)
    ) {
        HorizontalDivider(color = Roxi.SurfaceHigh, thickness = 0.5.dp)
        Row(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
        ) {
            tabs.forEach { t ->
                val selected = route == t.route
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { if (!selected) nav.navigateTab(t.route) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (selected) Roxi.Violet.copy(alpha = 0.35f) else Color.Transparent)
                            .padding(horizontal = 18.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            if (selected) t.selected else t.normal, t.label,
                            tint = if (selected) Roxi.Text else Roxi.TextSub,
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        t.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) Roxi.Text else Roxi.TextSub,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------ permission

@Composable
private fun PermissionScreen(asked: Boolean, onRequest: () -> Unit) {
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painterResource(R.drawable.roxi_logo), null,
            modifier = Modifier
                .size(120.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color.White),
        )
        Spacer(Modifier.height(20.dp))
        Text("Bienvenue sur Roxi Player", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text(
            "Pour trouver et lire ta musique, Roxi Player a besoin d'accéder aux fichiers audio de ton téléphone.",
            textAlign = TextAlign.Center,
            color = Roxi.TextSub,
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onRequest,
            colors = ButtonDefaults.buttonColors(containerColor = Roxi.Violet, contentColor = Color.White),
        ) { Text("Autoriser l'accès à la musique") }
        if (asked) {
            TextButton(onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                )
            }) { Text("Ouvrir les paramètres de l'appli") }
        }
    }
}

