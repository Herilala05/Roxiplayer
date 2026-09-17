package com.roxi.player.video

import android.net.Uri

data class VideoItem(
    val id: String,
    val uri: Uri,
    val title: String,
    val durationMs: Long,
    val size: Long,
    val dateAdded: Long,
    val secret: Boolean = false,
)
