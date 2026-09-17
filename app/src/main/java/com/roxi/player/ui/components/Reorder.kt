package com.roxi.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/**
 * Glisser-déposer dans une LazyColumn (sans bibliothèque externe).
 * [onMove] déplace l'élément dans la liste affichée pendant le geste,
 * [onDrop] enregistre le déplacement final (position de départ → position d'arrivée).
 * La LazyColumn ne doit contenir QUE les éléments déplaçables.
 */
@Stable
class ReorderState(
    private val listState: LazyListState,
    private val onMove: (from: Int, to: Int) -> Unit,
    private val onDrop: (from: Int, to: Int) -> Unit,
) {
    var draggingKey by mutableStateOf<Any?>(null)
        private set
    var offset by mutableFloatStateOf(0f)
        private set

    private var draggingIndex = -1
    private var startIndex = -1

    fun start(key: Any) {
        val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return
        draggingKey = key
        draggingIndex = info.index
        startIndex = info.index
        offset = 0f
    }

    fun drag(dy: Float) {
        if (draggingKey == null) return
        offset += dy
        val layout = listState.layoutInfo
        val current = layout.visibleItemsInfo.firstOrNull { it.index == draggingIndex } ?: return
        val top = current.offset + offset
        val bottom = top + current.size
        val center = (top + bottom) / 2f

        val target = layout.visibleItemsInfo.firstOrNull {
            it.index != draggingIndex && center > it.offset && center < it.offset + it.size
        }
        if (target != null) {
            onMove(draggingIndex, target.index)
            offset += (current.offset - target.offset)
            draggingIndex = target.index
        }

        // Défilement automatique près des bords
        val edge = 90f
        val step = when {
            top < layout.viewportStartOffset + edge -> -14f
            bottom > layout.viewportEndOffset - edge -> 14f
            else -> 0f
        }
        if (step != 0f) {
            val consumed = listState.dispatchRawDelta(step)
            offset += consumed
        }
    }

    fun end() {
        if (draggingKey != null && startIndex >= 0 && draggingIndex >= 0 && startIndex != draggingIndex) {
            onDrop(startIndex, draggingIndex)
        }
        draggingKey = null
        offset = 0f
        draggingIndex = -1
        startIndex = -1
    }

    fun isDragging(key: Any) = draggingKey == key
}

/** Modificateur de l'élément déplacé : il suit le doigt et passe au-dessus des autres. */
fun Modifier.draggedItem(state: ReorderState, dragging: Boolean, highlight: Color): Modifier =
    if (!dragging) this else this
        .zIndex(1f)
        .graphicsLayer {
            translationY = state.offset
            shadowElevation = 16f
            scaleX = 1.02f
            scaleY = 1.02f
        }
        .background(highlight)

/** Poignée ≡ : on la tient et on glisse pour déplacer le titre. */
@Composable
fun DragHandle(state: ReorderState, key: Any, tint: Color) {
    Icon(
        Icons.Rounded.DragHandle,
        contentDescription = "Déplacer",
        tint = tint,
        modifier = Modifier
            .size(48.dp)
            .pointerInput(state, key) {
                detectDragGestures(
                    onDragStart = { state.start(key) },
                    onDragEnd = { state.end() },
                    onDragCancel = { state.end() },
                ) { change, amount ->
                    change.consume()
                    state.drag(amount.y)
                }
            }
            .padding(12.dp),
    )
}
