package com.roxi.player

import android.app.Application
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.roxi.player.data.Backup
import com.roxi.player.data.Group
import com.roxi.player.data.Lyrics
import com.roxi.player.data.LyricsRepository
import com.roxi.player.data.MusicRepository
import com.roxi.player.data.Prefs
import com.roxi.player.data.Song
import com.roxi.player.data.SortOrder
import com.roxi.player.data.formatTime
import com.roxi.player.data.sortSongs
import com.roxi.player.playback.AudioFx
import com.roxi.player.playback.PlayerController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class MainViewModel(app: Application) : AndroidViewModel(app) {

    val prefs = Prefs(app)
    val lyrics = LyricsRepository(app)
    private val repo = MusicRepository(app)
    val player = PlayerController(app, viewModelScope)

    /** Résultat brut du scan. */
    private val _scanned = MutableStateFlow<List<Song>>(emptyList())

    /** Tous les fichiers trouvés (avec les infos modifiées), y compris les titres masqués. */
    private val _allSongs: StateFlow<List<Song>> = combine(_scanned, prefs.edits) { list, edits ->
        if (edits.isEmpty()) list else list.map { s ->
            edits[s.id]?.let { e -> s.copy(title = e.title, artist = e.artist, album = e.album) } ?: s
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Titres visibles (sans les titres masqués). */
    val songs: StateFlow<List<Song>> = combine(_allSongs, prefs.hidden) { all, hidden ->
        if (hidden.isEmpty()) all else all.filterNot { it.id in hidden }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val hiddenSongs: StateFlow<List<Song>> = combine(_allSongs, prefs.hidden) { all, hidden ->
        all.filter { it.id in hidden }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _hasScanned = MutableStateFlow(false)
    val hasScanned: StateFlow<Boolean> = _hasScanned.asStateFlow()

    private val _sleepTimerEnd = MutableStateFlow<Long?>(null)
    val sleepTimerEnd: StateFlow<Long?> = _sleepTimerEnd.asStateFlow()

    /** Tous les titres (même masqués) : utilisé pour la file d'attente et le lecteur. */
    val songMap: StateFlow<Map<Long, Song>> = _allSongs
        .map { list -> list.associateBy { it.id } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val visibleMap: StateFlow<Map<Long, Song>> = songs
        .map { list -> list.associateBy { it.id } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val currentSong: StateFlow<Song?> = combine(player.state, songMap) { st, map ->
        st.currentId?.let { map[it] }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val recentlyAdded: StateFlow<List<Song>> = songs
        .map { it.sortSongs(SortOrder.DATE_DESC) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val recentlyPlayed: StateFlow<List<Song>> = combine(prefs.recent, visibleMap) { ids, map ->
        ids.mapNotNull { map[it] }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val mostPlayed: StateFlow<List<Song>> = combine(prefs.playCounts, visibleMap) { counts, map ->
        counts.entries.sortedByDescending { it.value }.mapNotNull { map[it.key] }.take(30)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val likedSongs: StateFlow<List<Song>> = combine(prefs.liked, visibleMap) { ids, map ->
        ids.mapNotNull { map[it] }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val folders: StateFlow<List<Group>> = songs.map { list ->
        list.groupBy { it.folder }.map { (path, s) ->
            Group(path, File(path).name.ifEmpty { path }, path, s.sortSongs(SortOrder.AZ))
        }.sortedByDescending { it.songs.size }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val artists: StateFlow<List<Group>> = songs.map { list ->
        list.groupBy { it.artist }.map { (name, s) ->
            Group(name, name, "${s.size} titre(s)", s.sortSongs(SortOrder.AZ))
        }.sortedBy { it.name.lowercase() }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val albums: StateFlow<List<Group>> = songs.map { list ->
        list.groupBy { it.albumId }.map { (id, s) ->
            Group(id.toString(), s.first().album, s.first().artist, s)
        }.sortedBy { it.name.lowercase() }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private var observerRegistered = false
    private var rescanJob: Job? = null
    private var sleepJob: Job? = null
    private var restored = false

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            rescanJob?.cancel()
            rescanJob = viewModelScope.launch {
                delay(2_000)
                rescan()
            }
        }
    }

    init {
        player.onConnected = {
            val speed = prefs.playbackSpeed
            if (speed != 1f) player.setSpeed(speed)
            tryRestore()
        }
        player.onSongListened = { prefs.recordPlay(it) }
        player.onListenTime = { prefs.addListen(it) }
        player.onProgressSave = { id, pos, uri ->
            prefs.lastSongId = id
            prefs.lastPosition = pos
            prefs.lastSongUri = uri
        }
        player.connect()
        AudioFx.init(app)

        // Paroles automatiques : on cherche en ligne quand un titre sans paroles démarre
        viewModelScope.launch {
            currentSong.collect { song ->
                if (song != null && prefs.autoLyrics.value && lyrics.getLyrics(song) is Lyrics.None) {
                    lyrics.fetchOnline(song)
                }
            }
        }

        viewModelScope.launch {
            prefs.lyricsTree.collect { lyrics.setLyricsTree(it) }
        }
        viewModelScope.launch {
            prefs.minDurationSec.drop(1).collect { if (observerRegistered) rescan() }
        }
    }

    fun onPermissionGranted() {
        if (observerRegistered) return
        observerRegistered = true
        val resolver = getApplication<Application>().contentResolver
        val collections = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            MediaStore.getExternalVolumeNames(getApplication()).map { MediaStore.Audio.Media.getContentUri(it) }
        } else listOf(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
        collections.forEach { resolver.registerContentObserver(it, true, observer) }
        rescan()
    }

    fun rescan() {
        viewModelScope.launch {
            _scanning.value = true
            _scanned.value = repo.scan(prefs.minDurationSec.value * 1000L)
            lyrics.clearCache()
            _scanning.value = false
            _hasScanned.value = true
            tryRestore()
        }
    }

    private fun tryRestore() {
        if (restored) return
        val hidden = prefs.hidden.value
        val list = _scanned.value.filterNot { it.id in hidden }.sortSongs(SortOrder.DATE_DESC)
        if (list.isEmpty() || !player.state.value.connected) return
        restored = true
        val index = list.indexOfFirst { it.id == prefs.lastSongId }
        if (index >= 0) player.restore(list, index, prefs.lastPosition)
    }

    fun play(list: List<Song>, index: Int) = player.playSongs(list, index)

    fun shuffle(list: List<Song>) = player.playSongs(list, 0, shuffle = true)

    fun toggleLike(song: Song) = prefs.toggleLike(song.id)

    fun hide(song: Song) {
        prefs.hide(song.id)
        player.removeSong(song.id)
    }

    fun unhide(song: Song) = prefs.unhide(song.id)

    fun onSongDeleted(song: Song) {
        player.removeSong(song.id)
        _scanned.value = _scanned.value.filterNot { it.id == song.id }
        rescan()
    }

    // ------------------------------------------------------------ infos modifiées
    fun originalSong(id: Long): Song? = _scanned.value.firstOrNull { it.id == id }

    fun editSong(id: Long, title: String, artist: String, album: String) {
        val original = originalSong(id) ?: return
        val edit = Prefs.TagEdit(title.trim().ifEmpty { original.title }, artist.trim().ifEmpty { original.artist }, album.trim().ifEmpty { original.album })
        prefs.setEdit(id, if (edit.title == original.title && edit.artist == original.artist && edit.album == original.album) null else edit)
    }

    fun resetEdit(id: Long) = prefs.setEdit(id, null)

    // ------------------------------------------------------------ plusieurs titres
    fun playNextAll(list: List<Song>) = list.asReversed().forEach { player.playNext(it) }
    fun queueAll(list: List<Song>) = list.forEach { player.addToQueue(it) }
    fun hideAll(list: List<Song>) = list.forEach { hide(it) }
    fun addAllToPlaylist(playlistId: Long, list: List<Song>) = list.forEach { prefs.addToPlaylist(playlistId, it.id) }

    fun onSongsDeleted(list: List<Song>) {
        val ids = list.map { it.id }.toSet()
        ids.forEach { player.removeSong(it) }
        _scanned.value = _scanned.value.filterNot { it.id in ids }
        rescan()
    }

    // ------------------------------------------------------------ son
    fun setNormalize(on: Boolean) {
        prefs.setNormalize(on)
        AudioFx.refreshNormalization()
    }

    fun setNormalizeLevel(level: Int) {
        prefs.setNormalizeLevel(level)
        AudioFx.refreshNormalization()
    }

    // ------------------------------------------------------------ sauvegarde
    fun exportBackup(uri: android.net.Uri): Boolean {
        prefs.flushListen()
        return Backup.export(getApplication(), uri, prefs, _scanned.value)
    }

    fun importBackup(uri: android.net.Uri): Backup.Result =
        Backup.import(getApplication(), uri, _scanned.value)

    fun setSpeed(speed: Float) {
        prefs.playbackSpeed = speed
        player.setSpeed(speed)
    }

    /** Minuterie de sommeil : null = désactiver. */
    fun setSleepTimer(minutes: Int?) {
        sleepJob?.cancel()
        if (minutes == null) {
            _sleepTimerEnd.value = null
            return
        }
        val end = System.currentTimeMillis() + minutes * 60_000L
        _sleepTimerEnd.value = end
        sleepJob = viewModelScope.launch {
            delay(minutes * 60_000L)
            player.pause()
            _sleepTimerEnd.value = null
        }
    }

    fun sleepTimerLabel(end: Long?): String =
        if (end == null) "Désactivée" else "Arrêt dans " + formatTime(end - System.currentTimeMillis())

    override fun onCleared() {
        if (observerRegistered) {
            getApplication<Application>().contentResolver.unregisterContentObserver(observer)
        }
        prefs.flushListen()
        player.release()
        super.onCleared()
    }
}
