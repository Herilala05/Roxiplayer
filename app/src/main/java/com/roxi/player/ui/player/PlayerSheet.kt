package com.roxi.player.ui.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.MediaRouter2
import android.media.audiofx.AudioEffect
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.VolumeDown
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.roxi.player.data.Lyrics
import com.roxi.player.data.Song
import com.roxi.player.data.formatTime
import com.roxi.player.playback.PlaybackService
import com.roxi.player.playback.PlayerUi
import com.roxi.player.playback.QueueEntry
import com.roxi.player.ui.components.AlbumArt
import com.roxi.player.ui.components.ArtColors
import com.roxi.player.ui.components.DragHandle
import com.roxi.player.ui.components.LocalVm
import com.roxi.player.ui.components.ReorderState
import com.roxi.player.ui.components.SongMenu
import com.roxi.player.ui.components.draggedItem
import com.roxi.player.ui.theme.Roxi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

val MiniPlayerHeight = 64.dp

/** Mesures du lecteur plein écran (partagées par la mise en page et la pochette animée). */
private object FullLayout {
    val TopBar = 48.dp
    val ArtGap = 12.dp
    val Side = 24.dp
    /** Hauteur de tout ce qui n'est pas la pochette (hors barres système). */
    val Fixed = 470.dp
}

private data class SheetPx(
    val fullW: Float,
    val fullH: Float,
    val margin: Float,
    val miniH: Float,
    val collapsedTop: Float,
    val miniArt: Float,
    val miniArtPad: Float,
    val artFull: Float,
    val artXFull: Float,
    val artYFull: Float,
)

private fun lerpF(a: Float, b: Float, t: Float) = a + (b - a) * t

/**
 * État du lecteur : [fraction] = 0 → mini-lecteur, 1 → plein écran.
 * Tout (taille, position, pochette, couleurs) est calculé à partir de cette valeur.
 */
@Stable
class PlayerSheetState(private val scope: CoroutineScope) {
    var fraction by mutableFloatStateOf(0f)
        private set
    private var job: Job? = null

    fun expand() = animateTo(1f)
    fun collapse() = animateTo(0f)
    fun stop() {
        job?.cancel()
    }

    fun dragBy(delta: Float) {
        job?.cancel()
        fraction = (fraction + delta).coerceIn(0f, 1f)
    }

    /** Au relâchement : on va vers le plus proche, ou dans le sens du geste s'il est rapide. */
    fun settle(velocity: Float) {
        val target = when {
            velocity > 1.2f -> 1f
            velocity < -1.2f -> 0f
            fraction > 0.5f -> 1f
            else -> 0f
        }
        animateTo(target)
    }

    private fun animateTo(target: Float) {
        job?.cancel()
        job = scope.launch {
            animate(
                initialValue = fraction,
                targetValue = target,
                animationSpec = tween(durationMillis = 340, easing = FastOutSlowInEasing),
            ) { value, _ -> fraction = value }
        }
    }
}

@Composable
fun rememberPlayerSheetState(): PlayerSheetState {
    val scope = rememberCoroutineScope()
    return remember { PlayerSheetState(scope) }
}

/**
 * Panneau superposé à l'écran actuel (l'écran d'accueil reste visible dessous).
 * Transition « container transform » : la pochette du mini-lecteur grossit sur place.
 */
@Composable
fun PlayerSheet(
    state: PlayerSheetState,
    song: Song,
    collapsedBottom: Dp,
    onOpenEqualizer: () -> Unit,
) {
    val vm = LocalVm.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val context = LocalContext.current
        val fullW = maxWidth
        val fullH = maxHeight
        val statusTop = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
        val navBottom = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }

        val margin = 8.dp
        val miniArt = 48.dp
        val miniArtPad = 8.dp
        val artFull = max(
            160.dp,
            min(fullW - FullLayout.Side * 2, fullH - statusTop - navBottom - FullLayout.Fixed),
        )
        val artTopFull = statusTop + FullLayout.TopBar + FullLayout.ArtGap
        val collapsedTop = fullH - collapsedBottom - MiniPlayerHeight - 6.dp

        val px = with(density) {
            SheetPx(
                fullW = fullW.toPx(),
                fullH = fullH.toPx(),
                margin = margin.toPx(),
                miniH = MiniPlayerHeight.toPx(),
                collapsedTop = collapsedTop.toPx(),
                miniArt = miniArt.toPx(),
                miniArtPad = miniArtPad.toPx(),
                artFull = artFull.toPx(),
                artXFull = ((fullW - artFull) / 2).toPx(),
                artYFull = artTopFull.toPx(),
            )
        }
        val dragRange = px.collapsedTop.coerceAtLeast(1f)

        // Couleur tirée de la pochette, avec transition douce
        val artColor by produceState(ArtColors.peek(song.id) ?: ArtColors.Fallback, song.id) {
            value = ArtColors.get(context, song)
        }
        val animColor = animateColorAsState(artColor, tween(600), label = "artColor")

        // La pochette rétrécit légèrement en pause, rebondit à la reprise
        val playingFlow = remember { vm.player.state.map { it.isPlaying }.distinctUntilChanged() }
        val playing by playingFlow.collectAsStateWithLifecycle(initialValue = false)
        val pauseScale = animateFloatAsState(
            if (playing) 1f else 0.88f,
            spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow),
            label = "pauseScale",
        )

        val expanded by remember { derivedStateOf { state.fraction > 0.5f } }
        val showScrim by remember { derivedStateOf { state.fraction > 0.001f } }
        BackHandler(enabled = expanded) { state.collapse() }

        // Lecteur toujours sombre : icônes de la barre d'état claires quand il est ouvert
        val view = androidx.compose.ui.platform.LocalView.current
        val appIsDark = Roxi.isDark
        LaunchedEffect(expanded, appIsDark) {
            val window = (view.context as? android.app.Activity)?.window ?: return@LaunchedEffect
            androidx.core.view.WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = !expanded && !appIsDark
        }

        if (showScrim) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = state.fraction * 0.6f }
                    .background(Color.Black)
            )
        }

        val dragState = rememberDraggableState { delta -> state.dragBy(-delta / dragRange) }

        Box(
            Modifier
                .offset {
                    val f = state.fraction
                    IntOffset(lerpF(px.margin, 0f, f).roundToInt(), lerpF(px.collapsedTop, 0f, f).roundToInt())
                }
                .layout { measurable, _ ->
                    val f = state.fraction
                    val w = lerpF(px.fullW - 2 * px.margin, px.fullW, f).roundToInt()
                    val h = lerpF(px.miniH, px.fullH, f).roundToInt()
                    val placeable = measurable.measure(Constraints.fixed(w, h))
                    layout(w, h) { placeable.place(0, 0) }
                }
                .graphicsLayer {
                    shape = RoundedCornerShape(lerpF(14f, 0f, state.fraction).dp)
                    clip = true
                }
                .drawBehind {
                    val top = animColor.value
                    // Plein écran : dégradé couleur de pochette → presque noir (comme le modèle)
                    drawRect(
                        Brush.verticalGradient(
                            0f to lerpColor(top, Color.Black, 0.28f),
                            0.48f to lerpColor(top, Color(0xFF15151B), 0.72f),
                            1f to Color(0xFF101014),
                        )
                    )
                    // Mini-lecteur : couleur unie, qui s'efface pendant l'ouverture
                    val miniAlpha = (1f - state.fraction * 1.6f).coerceIn(0f, 1f)
                    if (miniAlpha > 0f) drawRect(lerpColor(top, Color.Black, 0.35f), alpha = miniAlpha)
                }
                .draggable(
                    state = dragState,
                    orientation = Orientation.Vertical,
                    onDragStarted = { state.stop() },
                    onDragStopped = { velocity -> state.settle(-velocity / dragRange) },
                )
        ) {
            // 1. Lecteur plein écran (dessiné à taille réelle, révélé en fin d'animation)
            FullPlayer(
                song = song,
                state = state,
                statusTop = statusTop,
                navBottom = navBottom,
                artSize = artFull,
                sheetColor = { animColor.value },
                onOpenEqualizer = onOpenEqualizer,
                modifier = Modifier
                    .wrapContentSize(Alignment.TopStart, unbounded = true)
                    .size(fullW, fullH)
                    .graphicsLayer {
                        val a = ((state.fraction - 0.45f) / 0.55f).coerceIn(0f, 1f)
                        alpha = a
                        // léger glissement vers le haut pendant l'apparition
                        translationY = (1f - a) * 40.dp.toPx()
                    },
            )

            // 2. La pochette : UNE seule image qui se déplace et grossit
            Box(
                Modifier
                    .offset {
                        val f = state.fraction
                        IntOffset(
                            lerpF(px.miniArtPad, px.artXFull, f).roundToInt(),
                            lerpF(px.miniArtPad, px.artYFull, f).roundToInt(),
                        )
                    }
                    .layout { measurable, _ ->
                        val s = lerpF(px.miniArt, px.artFull, state.fraction).roundToInt()
                        val placeable = measurable.measure(Constraints.fixed(s, s))
                        layout(s, s) { placeable.place(0, 0) }
                    }
                    .graphicsLayer {
                        val f = state.fraction
                        val sc = lerpF(1f, pauseScale.value, f)
                        scaleX = sc
                        scaleY = sc
                        shape = RoundedCornerShape(lerpF(8f, 26f, f).dp)
                        clip = true
                        shadowElevation = lerpF(0f, 24f, f)
                    }
            ) {
                Crossfade(targetState = song, animationSpec = tween(350), label = "art") { s ->
                    AlbumArt(s, Modifier.fillMaxSize(), size = 720)
                }
                // Petit bouton rond sur la pochette (favori), visible en plein écran
                if (expanded) {
                    val liked by vm.prefs.liked.collectAsStateWithLifecycle()
                    val isLiked = song.id in liked
                    val bump by animateFloatAsState(
                        if (isLiked) 1f else 0.9f,
                        spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium),
                        label = "likeBump",
                    )
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(14.dp)
                            .graphicsLayer {
                                alpha = ((state.fraction - 0.7f) / 0.3f).coerceIn(0f, 1f)
                                scaleX = bump
                                scaleY = bump
                            }
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(0xCC1E1E26))
                            .clickable { vm.toggleLike(song) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            "J'aime",
                            tint = if (isLiked) Roxi.Pink else Color.White,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }

            // 3. Mini-lecteur (seulement quand le panneau est fermé)
            if (!expanded) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { state.expand() }
                )
                MiniPlayer(
                    song,
                    onExpand = { state.expand() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(MiniPlayerHeight)
                        .graphicsLayer { alpha = (1f - state.fraction / 0.3f).coerceIn(0f, 1f) },
                )
            }
        }
    }
}

@Composable
private fun MiniPlayer(song: Song, onExpand: () -> Unit, modifier: Modifier) {
    val vm = LocalVm.current
    val ui by vm.player.state.collectAsStateWithLifecycle()
    val threshold = with(LocalDensity.current) { 72.dp.toPx() }
    var dx by remember { mutableFloatStateOf(0f) }
    val position = rememberSmoothPosition(ui)
    Box(modifier) {
        Row(
            Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onExpand,
                )
                // Glisser à gauche = suivant, à droite = précédent
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (dx < -threshold) vm.player.next()
                            else if (dx > threshold) vm.player.previous()
                            dx = 0f
                        },
                        onDragCancel = { dx = 0f },
                    ) { change, amount ->
                        change.consume()
                        dx += amount
                    }
                }
                .padding(start = 66.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .graphicsLayer {
                        translationX = dx
                        alpha = 1f - (abs(dx) / (threshold * 2f)).coerceIn(0f, 0.7f)
                    }
            ) {
                Text(
                    song.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
                Text(
                    song.artist,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.65f),
                )
            }
            IconButton(onClick = { vm.player.previous() }) {
                Icon(Icons.Rounded.SkipPrevious, "Précédent", tint = Color.White)
            }
            IconButton(onClick = { vm.player.togglePlay() }) {
                PlayPauseIcon(ui.isPlaying, Modifier.size(28.dp))
            }
            IconButton(onClick = { vm.player.next() }) {
                Icon(Icons.Rounded.SkipNext, "Suivant", tint = Color.White)
            }
        }
        // Fine barre de progression (fluide, dessinée à chaque image)
        val duration = ui.durationMs.takeIf { it > 0 } ?: song.durationMs
        Canvas(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(2.dp)
                .padding(horizontal = 12.dp)
        ) {
            val p = (position.value.toFloat() / duration.coerceAtLeast(1L)).coerceIn(0f, 1f)
            drawRect(Color.White.copy(alpha = 0.15f))
            drawRect(Color.White, size = size.copy(width = size.width * p))
        }
    }
}

/**
 * Position de lecture « lissée » : entre deux mises à jour du lecteur (toutes les 500 ms),
 * on avance nous-mêmes à chaque image → la barre glisse au lieu de sauter.
 */
@Composable
private fun rememberSmoothPosition(ui: PlayerUi): State<Long> {
    val pos = remember { mutableLongStateOf(ui.positionMs) }
    LaunchedEffect(ui.positionMs, ui.isPlaying, ui.speed, ui.currentId) {
        val base = ui.positionMs
        pos.longValue = base
        if (ui.isPlaying) {
            val t0 = withFrameMillis { it }
            val limit = if (ui.durationMs > 0) ui.durationMs else Long.MAX_VALUE
            while (true) {
                withFrameMillis { t ->
                    pos.longValue = (base + ((t - t0) * ui.speed).toLong()).coerceAtMost(limit)
                }
            }
        }
    }
    return pos
}

/** Icône lecture/pause qui se transforme en douceur. */
@Composable
private fun PlayPauseIcon(playing: Boolean, modifier: Modifier, tint: Color = Color.White) {
    AnimatedContent(
        targetState = playing,
        transitionSpec = {
            (scaleIn(initialScale = 0.6f) + fadeIn(tween(180))) togetherWith
                (scaleOut(targetScale = 0.6f) + fadeOut(tween(120)))
        },
        label = "playPause",
    ) { isPlaying ->
        Icon(
            if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            if (isPlaying) "Pause" else "Lecture",
            tint = tint,
            modifier = modifier,
        )
    }
}

/** Bouton qui se « tasse » quand on appuie dessus. */
@Composable
private fun PressButton(
    size: Dp,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.82f else 1f,
        spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow),
        label = "press",
    )
    Box(
        Modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/**
 * Barre fine (progression ou volume) : le rond grossit quand on la touche.
 * [value] est lu au moment du dessin → pas de recomposition à chaque image.
 */
@Composable
private fun ThinSlider(
    value: () -> Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onValueChangeFinished: () -> Unit = {},
) {
    var pressed by remember { mutableStateOf(false) }
    val thumb by animateDpAsState(if (pressed) 18.dp else 12.dp, spring(dampingRatio = 0.6f), label = "thumb")
    val track by animateDpAsState(if (pressed) 6.dp else 4.dp, label = "track")
    val change by rememberUpdatedState(onValueChange)
    val finished by rememberUpdatedState(onValueChangeFinished)

    Canvas(
        modifier
            .fillMaxWidth()
            .height(28.dp)
            .pointerInput(Unit) {
                val pad = 9.dp.toPx()
                fun at(x: Float) = ((x - pad) / (size.width - 2 * pad)).coerceIn(0f, 1f)
                awaitEachGesture {
                    val down = awaitFirstDown()
                    pressed = true
                    change(at(down.position.x))
                    down.consume()
                    drag(down.id) { c ->
                        change(at(c.position.x))
                        c.consume()
                    }
                    pressed = false
                    finished()
                }
            }
    ) {
        val pad = 9.dp.toPx()
        val y = size.height / 2
        val w = size.width - 2 * pad
        val x = pad + w * value().coerceIn(0f, 1f)
        drawLine(Color.White.copy(alpha = 0.25f), Offset(pad, y), Offset(pad + w, y), track.toPx(), StrokeCap.Round)
        drawLine(Color.White, Offset(pad, y), Offset(x, y), track.toPx(), StrokeCap.Round)
        drawCircle(Color.White, radius = thumb.toPx() / 2, center = Offset(x, y))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FullPlayer(
    song: Song,
    state: PlayerSheetState,
    statusTop: Dp,
    navBottom: Dp,
    artSize: Dp,
    sheetColor: () -> Color,
    onOpenEqualizer: () -> Unit,
    modifier: Modifier,
) {
    val vm = LocalVm.current
    val context = LocalContext.current
    val ui by vm.player.state.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var showSpeed by remember { mutableStateOf(false) }
    val eqLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}
    val position = rememberSmoothPosition(ui)
    val sub = Color.White.copy(alpha = 0.6f)

    Column(modifier) {
        Spacer(Modifier.height(statusTop))

        // Barre du haut : flèche + poignée (on peut tirer vers le bas)
        Box(
            Modifier
                .fillMaxWidth()
                .height(FullLayout.TopBar)
                .padding(horizontal = 8.dp)
        ) {
            IconButton(onClick = { state.collapse() }, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(Icons.Rounded.KeyboardArrowDown, "Réduire", tint = Color.White, modifier = Modifier.size(30.dp))
            }
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(width = 40.dp, height = 5.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.3f))
            )
        }
        Spacer(Modifier.height(FullLayout.ArtGap))
        Spacer(Modifier.height(artSize)) // place réservée à la pochette animée
        Spacer(Modifier.height(28.dp))

        // Titre + bouton « … »
        Row(Modifier.padding(horizontal = FullLayout.Side), verticalAlignment = Alignment.CenterVertically) {
            AnimatedContent(
                targetState = song,
                transitionSpec = {
                    (slideInVertically { it / 2 } + fadeIn(tween(250))) togetherWith
                        (slideOutVertically { -it / 2 } + fadeOut(tween(150)))
                },
                modifier = Modifier.weight(1f),
                label = "title",
            ) { s ->
                Column {
                    Text(
                        s.title,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee(),
                    )
                    Text(
                        s.artist,
                        style = MaterialTheme.typography.bodyLarge,
                        color = sub,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Box {
                PressButton(size = 36.dp, onClick = { menu = true }) {
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.MoreHoriz, "Options", tint = Color.White, modifier = Modifier.size(20.dp)) }
                }
                SongMenu(
                    song = song,
                    expanded = menu,
                    onDismiss = { menu = false },
                    showQueueActions = false,
                    extraMenu = { close ->
                        DropdownMenuItem(
                            text = { Text(if (ui.shuffle) "Aléatoire : activé" else "Aléatoire : désactivé") },
                            leadingIcon = { Icon(Icons.Rounded.Shuffle, null) },
                            onClick = { vm.player.toggleShuffle() },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    when (ui.repeatMode) {
                                        Player.REPEAT_MODE_ONE -> "Répétition : ce titre"
                                        Player.REPEAT_MODE_ALL -> "Répétition : tout"
                                        else -> "Répétition : désactivée"
                                    }
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    if (ui.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                                    null,
                                )
                            },
                            onClick = { vm.player.cycleRepeat() },
                        )
                        DropdownMenuItem(
                            text = { Text("Vitesse de lecture (${speedLabel(ui.speed)})") },
                            leadingIcon = { Icon(Icons.Rounded.Speed, null) },
                            onClick = {
                                showSpeed = true
                                close()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Égaliseur") },
                            leadingIcon = { Icon(Icons.Rounded.Equalizer, null) },
                            onClick = {
                                close()
                                onOpenEqualizer()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Minuterie de sommeil") },
                            leadingIcon = { Icon(Icons.Rounded.Timer, null) },
                            onClick = {
                                showSleep = true
                                close()
                            },
                        )
                    },
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // Barre de progression fine + temps écoulé / temps restant
        val duration = ui.durationMs.takeIf { it > 0 } ?: song.durationMs
        var dragging by remember { mutableStateOf(false) }
        var dragValue by remember { mutableFloatStateOf(0f) }
        ThinSlider(
            value = {
                if (dragging) dragValue
                else position.value.toFloat() / duration.coerceAtLeast(1L)
            },
            onValueChange = {
                dragging = true
                dragValue = it
            },
            onValueChangeFinished = {
                vm.player.seekTo((dragValue * duration).toLong())
                dragging = false
            },
            modifier = Modifier.padding(horizontal = FullLayout.Side - 9.dp),
        )
        val shownMs by remember(duration) {
            derivedStateOf {
                val ms = if (dragging) (dragValue * duration).toLong() else position.value
                ms / 1000L * 1000L // change seulement chaque seconde
            }
        }
        Row(Modifier.padding(horizontal = FullLayout.Side)) {
            Text(formatTime(shownMs), style = MaterialTheme.typography.labelMedium, color = sub)
            Spacer(Modifier.weight(1f))
            Text(
                "-" + formatTime((duration - shownMs).coerceAtLeast(0L)),
                style = MaterialTheme.typography.labelMedium,
                color = sub,
            )
        }

        Spacer(Modifier.height(16.dp))

        // Les 3 commandes principales
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(36.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PressButton(size = 64.dp, onClick = { vm.player.previous() }) {
                Icon(Icons.Rounded.SkipPrevious, "Précédent", tint = Color.White, modifier = Modifier.size(42.dp))
            }
            PressButton(size = 80.dp, onClick = { vm.player.togglePlay() }) {
                PlayPauseIcon(ui.isPlaying, Modifier.size(64.dp))
            }
            PressButton(size = 64.dp, onClick = { vm.player.next() }) {
                Icon(Icons.Rounded.SkipNext, "Suivant", tint = Color.White, modifier = Modifier.size(42.dp))
            }
        }

        Spacer(Modifier.weight(1f))
        LyricsPreview(song, ui.positionMs) { showLyrics = true }
        Spacer(Modifier.weight(1f))

        // Volume
        VolumeRow(Modifier.padding(horizontal = FullLayout.Side - 4.dp))

        Spacer(Modifier.height(16.dp))

        // 3 icônes du bas : paroles, sortie audio, file d'attente
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            PressButton(size = 48.dp, onClick = { showLyrics = true }) {
                Icon(Icons.Rounded.ChatBubbleOutline, "Paroles", tint = sub)
            }
            PressButton(size = 48.dp, onClick = { openOutputPicker(context) }) {
                Icon(Icons.Rounded.Cast, "Sortie audio", tint = sub)
            }
            PressButton(size = 48.dp, onClick = { showQueue = true }) {
                Icon(Icons.AutoMirrored.Rounded.QueueMusic, "File d'attente", tint = sub)
            }
        }
        Spacer(Modifier.height(navBottom + 12.dp))
    }

    if (showQueue) QueueSheet { showQueue = false }
    if (showLyrics) LyricsSheet(song, sheetColor()) { showLyrics = false }
    if (showSleep) SleepTimerDialog { showSleep = false }
    if (showSpeed) SpeedDialog { showSpeed = false }
}

/** Volume du téléphone (flux musique), synchronisé avec les boutons physiques. */
@Composable
private fun VolumeRow(modifier: Modifier) {
    val context = LocalContext.current
    val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val max = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var volume by remember { mutableFloatStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max) }
    var touching by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (!touching) volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
            }
        }
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter("android.media.VOLUME_CHANGED_ACTION"),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {
                // déjà désinscrit
            }
        }
    }

    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.VolumeDown, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
        ThinSlider(
            value = { volume },
            onValueChange = { v ->
                touching = true
                volume = v
                try {
                    audio.setStreamVolume(AudioManager.STREAM_MUSIC, (v * max).roundToInt(), 0)
                } catch (e: SecurityException) {
                    // mode Ne pas déranger : changement refusé
                }
            },
            onValueChangeFinished = { touching = false },
            modifier = Modifier.weight(1f),
        )
        Icon(Icons.Rounded.VolumeUp, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
    }
}

private fun openEqualizer(context: Context, launch: (Intent) -> Unit) {
    val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
        putExtra(AudioEffect.EXTRA_AUDIO_SESSION, PlaybackService.audioSessionId)
        putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
        putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
    }
    try {
        launch(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Aucun égaliseur disponible sur ce téléphone", Toast.LENGTH_SHORT).show()
    }
}

/** Choix de la sortie audio (haut-parleur, Bluetooth…). */
private fun openOutputPicker(context: Context) {
    if (Build.VERSION.SDK_INT >= 34) {
        try {
            if (MediaRouter2.getInstance(context).showSystemOutputSwitcher()) return
        } catch (e: Exception) {
            // on essaie la méthode suivante
        }
    }
    try {
        context.startActivity(
            Intent("com.android.settings.panel.action.MEDIA_OUTPUT")
                .putExtra("com.android.settings.panel.extra.PACKAGE_NAME", context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return
    } catch (e: Exception) {
        // panneau absent sur ce téléphone
    }
    try {
        context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        Toast.makeText(context, "Choix de sortie audio indisponible", Toast.LENGTH_SHORT).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueSheet(onDismiss: () -> Unit) {
    val background = Color(0xFF17171D)
    val vm = LocalVm.current
    val ui by vm.player.state.collectAsStateWithLifecycle()
    val map by vm.songMap.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    val items = remember { mutableStateListOf<QueueEntry>().apply { addAll(ui.queue) } }
    val startPos = remember { ui.queue.indexOfFirst { it.index == ui.queueIndex }.coerceAtLeast(0) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (startPos - 2).coerceAtLeast(0))
    val reorder = remember(listState) {
        ReorderState(
            listState,
            onMove = { from, to -> items.add(to, items.removeAt(from)) },
            onDrop = { from, to -> vm.player.moveQueueItem(from, to) },
        )
    }
    // Resynchronise la liste locale quand la vraie file change (hors glisser-déposer)
    LaunchedEffect(ui.queue) {
        if (reorder.draggingKey == null) {
            items.clear()
            items.addAll(ui.queue)
        }
    }
    val canReorder = !ui.shuffle

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = background,
        contentColor = Color.White,
    ) {
        Row(
            Modifier.padding(start = 20.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "File d'attente (${items.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = {
                val pos = items.indexOfFirst { it.index == ui.queueIndex }
                if (pos >= 0) scope.launch { listState.animateScrollToItem((pos - 2).coerceAtLeast(0)) }
            }) { Icon(Icons.Rounded.MyLocation, "Titre en cours") }
        }
        if (!canReorder) {
            Text(
                "Désactive le mode aléatoire pour réorganiser la file.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.68f),
        ) {
            itemsIndexed(items, key = { _, e -> "${e.index}-${e.songId}" }) { _, entry ->
                val s = map[entry.songId]
                if (s != null) {
                    val key = "${entry.index}-${entry.songId}"
                    val dragging = reorder.isDragging(key)
                    val isCurrent = entry.index == ui.queueIndex
                    Row(
                        Modifier
                            .then(if (dragging) Modifier else Modifier.animateItem())
                            .draggedItem(reorder, dragging, lerpColor(background, Color.White, 0.12f))
                            .fillMaxWidth()
                            .background(if (isCurrent && !dragging) Color.White.copy(alpha = 0.08f) else Color.Transparent)
                            .clickable { if (reorder.draggingKey == null) vm.player.skipTo(entry.index) }
                            .padding(start = 20.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AlbumArt(s, Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                s.title, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                color = if (isCurrent) Roxi.AccentOnDark else Color.White,
                                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                            )
                            Text(
                                s.artist, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f),
                            )
                        }
                        if (isCurrent) {
                            Icon(Icons.Rounded.Equalizer, "En cours", tint = Roxi.AccentOnDark, modifier = Modifier.padding(12.dp))
                        } else {
                            IconButton(onClick = { vm.player.removeFromQueue(entry.index) }) {
                                Icon(Icons.Rounded.Close, "Retirer", tint = Color.White.copy(alpha = 0.6f))
                            }
                        }
                        if (canReorder) DragHandle(reorder, key, Color.White.copy(alpha = 0.6f))
                    }
                }
            }
        }

        // Boutons du bas : répétition et aléatoire
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            val repeatLabel = when (ui.repeatMode) {
                Player.REPEAT_MODE_ONE -> "Répéter 1"
                Player.REPEAT_MODE_ALL -> "Répéter tout"
                else -> "Sans répétition"
            }
            ToggleChip(
                icon = if (ui.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                label = repeatLabel,
                active = ui.repeatMode != Player.REPEAT_MODE_OFF,
            ) { vm.player.cycleRepeat() }
            ToggleChip(Icons.Rounded.Shuffle, "Aléatoire", ui.shuffle) { vm.player.toggleShuffle() }
        }
    }
}

@Composable
private fun ToggleChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (active) Roxi.AccentOnDark else Color.White.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val color = if (active) Roxi.OnAccentOnDark else Color.White
        Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = color, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun LyricsPreview(song: Song, positionMs: Long, onOpen: () -> Unit) {
    val vm = LocalVm.current
    val version by vm.lyrics.version.collectAsStateWithLifecycle()
    val lyrics by produceState<Lyrics?>(null, song.id, version) { value = vm.lyrics.getLyrics(song) }
    val text = when (val l = lyrics) {
        is Lyrics.Synced -> (l.lines.lastOrNull { it.timeMs <= positionMs } ?: l.lines.firstOrNull())
            ?.text?.ifBlank { "♪" }
        is Lyrics.Plain -> l.text.lineSequence().firstOrNull { it.isNotBlank() }
        else -> null
    }
    if (text != null) {
        Text(
            text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleSmall,
            color = Color.White.copy(alpha = 0.85f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onOpen)
                .padding(8.dp),
        )
    }
}

private fun speedLabel(speed: Float): String =
    if (speed == 1f) "normale" else "${speed.toString().removeSuffix(".0")}x"

@Composable
private fun SpeedDialog(onDismiss: () -> Unit) {
    val vm = LocalVm.current
    val ui by vm.player.state.collectAsStateWithLifecycle()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Roxi.Surface,
        title = { Text("Vitesse de lecture") },
        text = {
            Column {
                listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f).forEach { sp ->
                    Text(
                        if (sp == 1f) "Normale (1x)" else speedLabel(sp),
                        color = if (sp == ui.speed) Roxi.VioletSoft else Roxi.Text,
                        fontWeight = if (sp == ui.speed) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                vm.setSpeed(sp)
                                onDismiss()
                            }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LyricsSheet(song: Song, background: Color, onDismiss: () -> Unit) {
    val vm = LocalVm.current
    val ui by vm.player.state.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val version by vm.lyrics.version.collectAsStateWithLifecycle()
    val lyrics by produceState<Lyrics?>(null, song.id, version) { value = vm.lyrics.getLyrics(song) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var searching by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = background,
        contentColor = Color.White,
    ) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.FormatQuote, null, tint = Roxi.AccentOnDark)
            Spacer(Modifier.width(8.dp))
            Text(
                song.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        HorizontalDivider()
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.7f)
        ) {
            when (val l = lyrics) {
                null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                Lyrics.None -> Column(
                    Modifier
                        .align(Alignment.Center)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Paroles non disponibles pour ce morceau.", textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    if (searching) {
                        CircularProgressIndicator(color = Color.White)
                    } else {
                        Button(
                            onClick = {
                                searching = true
                                scope.launch {
                                    val found = vm.lyrics.fetchOnline(song, force = true)
                                    searching = false
                                    if (!found) {
                                        Toast.makeText(
                                            context,
                                            "Aucune parole trouvée (ou pas d'Internet)",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Roxi.AccentOnDark,
                                contentColor = Roxi.OnAccentOnDark,
                            ),
                        ) {
                            Icon(Icons.Rounded.Search, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Rechercher en ligne")
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Tu peux aussi mettre un fichier .lrc portant exactement le même nom que la chanson " +
                            "dans ton dossier des paroles (onglet Moi → Dossier des paroles).",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                    )
                }
                is Lyrics.Plain -> Text(
                    l.text,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                )
                is Lyrics.Synced -> SyncedLyrics(l, ui.positionMs) { vm.player.seekTo(it) }
            }
        }
    }
}

@Composable
private fun SyncedLyrics(lyrics: Lyrics.Synced, positionMs: Long, onSeek: (Long) -> Unit) {
    val current = remember(positionMs, lyrics) { lyrics.lines.indexOfLast { it.timeMs <= positionMs } }
    val listState = rememberLazyListState()
    LaunchedEffect(current) {
        if (current >= 0) listState.animateScrollToItem((current - 3).coerceAtLeast(0))
    }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        itemsIndexed(lyrics.lines) { i, line ->
            val active = i == current
            Text(
                line.text.ifBlank { "♪" },
                style = if (active) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                color = if (active) Color.White else Color.White.copy(alpha = 0.6f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSeek(line.timeMs) }
                    .padding(horizontal = 24.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
fun SleepTimerDialog(onDismiss: () -> Unit) {
    val vm = LocalVm.current
    val end by vm.sleepTimerEnd.collectAsStateWithLifecycle()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Roxi.Surface,
        title = { Text("Minuterie de sommeil") },
        text = {
            Column {
                Text(vm.sleepTimerLabel(end), color = Roxi.TextSub)
                Spacer(Modifier.height(8.dp))
                listOf(15, 30, 45, 60, 90).forEach { m ->
                    Text(
                        "$m minutes",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                vm.setSleepTimer(m)
                                onDismiss()
                            }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                vm.setSleepTimer(null)
                onDismiss()
            }) { Text("Désactiver") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
    )
}
