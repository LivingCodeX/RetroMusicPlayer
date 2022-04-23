package code.name.monkey.retromusic.repository

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import androidx.annotation.WorkerThread
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import code.name.monkey.retromusic.R
import code.name.monkey.retromusic.WHITELIST_MUSIC
import code.name.monkey.retromusic.db.*
import code.name.monkey.retromusic.helper.SortOrder.PlaylistSortOrder.Companion.PLAYLIST_A_Z
import code.name.monkey.retromusic.helper.SortOrder.PlaylistSortOrder.Companion.PLAYLIST_SONG_COUNT
import code.name.monkey.retromusic.helper.SortOrder.PlaylistSortOrder.Companion.PLAYLIST_SONG_COUNT_DESC
import code.name.monkey.retromusic.helper.SortOrder.PlaylistSortOrder.Companion.PLAYLIST_Z_A
import code.name.monkey.retromusic.model.Song
import code.name.monkey.retromusic.util.FileUtil.safeCanonicalPath
import code.name.monkey.retromusic.util.PreferenceUtil
import code.name.monkey.retromusic.util.mapAsync
import kotlinx.coroutines.*
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject


interface RoomRepository {
    suspend fun historySongs(): List<HistoryEntity>
    suspend fun playCountSongs(): List<HistoryEntity>
    suspend fun historySongsReversed(): List<HistoryEntity>
    suspend fun historySize(): Int
    fun favoriteSongsLiveData(): LiveData<List<SongEntity>>
    fun observableHistorySongs(): LiveData<List<HistoryEntity>>
    fun observablePlayCountSongs(): LiveData<List<HistoryEntity>>
    fun getSongsFromPlaylistLiveData(playListId: Long): LiveData<List<SongEntity>>
    suspend fun getSongsFromPlaylist(playListId: Long): List<SongEntity>
    suspend fun createPlaylist(playlistEntity: PlaylistEntity): Long
    suspend fun checkPlaylistExists(playlistName: String): List<PlaylistEntity>
    suspend fun playlists(): List<PlaylistEntity>
    suspend fun playlistWithSongs(): List<PlaylistWithSongs>
    suspend fun insertSongs(songs: List<SongEntity>)
    suspend fun deletePlaylistEntities(playlistEntities: List<PlaylistEntity>)
    suspend fun renamePlaylistEntity(playlistId: Long, name: String)
    suspend fun deleteSongsInPlaylist(songs: List<SongEntity>)
    suspend fun deletePlaylistSongs(playlists: List<PlaylistEntity>)
    suspend fun removeSongFromPlaylist(songEntity: SongEntity)
    suspend fun addSongToHistory(currentSong: Song)
    suspend fun addHistoryEntitiesToHistory(historyEntities: List<HistoryEntity>)
    suspend fun songPresentInHistory(song: Song): HistoryEntity?
    suspend fun updateHistorySong(historyEntity: HistoryEntity)
    suspend fun deleteSongInHistory(songId: Long)
    suspend fun clearSongHistory()
    suspend fun deleteSong(song: Song)
    suspend fun isSongFavorite(songId: Long): Boolean
    suspend fun addSongToFavorites(songId: Long)
    suspend fun removeSongFromFavorites(songId: Long)
    suspend fun favoriteSongs(): List<SongEntity>
    fun checkPlaylistExists(playListId: Long): LiveData<Boolean>
    suspend fun addBlacklistPath(path: String)
    suspend fun removeBlacklistPath(path: String)
    suspend fun getBlacklistPaths(): List<String>
    fun getBlacklistPathsLiveData(): LiveData<List<BlacklistEntity>>
    suspend fun clearBlacklist()
    suspend fun addWhitelistPath(path: String)
    suspend fun removeWhitelistPath(path: String)
    suspend fun getWhitelistPaths(): List<String>
    fun getWhitelistPathsLiveData(): LiveData<List<WhitelistEntity>>
    suspend fun clearWhitelist()
    suspend fun setQueue(queue: List<QueueEntity>)
    suspend fun setOriginalQueue(originalQueue: List<OriginalQueueEntity>)
    suspend fun getQueue(): List<QueueEntity>
    suspend fun getOriginalQueue(): List<OriginalQueueEntity>
    suspend fun cleanInvalidRecords()
}

class RealRoomRepository(
    private val context: Context,
    private val applicationScope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val cpuDispatcher: CoroutineDispatcher,
    private val playlistDao: PlaylistDao,
    private val historyDao: HistoryDao,
    private val blacklistDao: BlacklistDao,
    private val whitelistDao: WhitelistDao,
    private val queueDao: QueueDao,
    private val originalQueueDao: OriginalQueueDao
) : RoomRepository, KoinComponent {

    private val songRepository: SongRepository by inject()

    private val sharedPreferenceChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            if (key == WHITELIST_MUSIC) {
                if (sharedPreferences.getBoolean(key, false)) {
                    applicationScope.launch {
                        cleanInvalidRecords()
                    }
                } else {
                    songRepository.updateSongCache()
                }
            }
        }

    init {
        applicationScope.launch {
            setDefaultBlacklist()
            setDefaultWhitelist()
            cleanInvalidRecords()
        }

        PreferenceUtil.registerOnSharedPreferenceChangedListener(sharedPreferenceChangeListener)
    }

    @WorkerThread
    override suspend fun createPlaylist(playlistEntity: PlaylistEntity): Long =
        withContext(ioDispatcher) {
            playlistDao.createPlaylist(playlistEntity)
        }

    @WorkerThread
    override suspend fun checkPlaylistExists(playlistName: String): List<PlaylistEntity> =
        withContext(ioDispatcher) {
            playlistDao.playlist(playlistName)
        }

    @WorkerThread
    override suspend fun playlists(): List<PlaylistEntity> =
        withContext(ioDispatcher) {
            playlistDao.playlists()
        }

    @WorkerThread
    override suspend fun playlistWithSongs(): List<PlaylistWithSongs> {
        cleanInvalidPlaylistSongRecords()
        return withContext(ioDispatcher) {
            when (PreferenceUtil.playlistSortOrder) {
                PLAYLIST_A_Z ->
                    playlistDao.playlistsWithSongs().sortedBy {
                        it.playlistEntity.playlistName
                    }
                PLAYLIST_Z_A -> playlistDao.playlistsWithSongs()
                    .sortedByDescending {
                        it.playlistEntity.playlistName
                    }
                PLAYLIST_SONG_COUNT -> playlistDao.playlistsWithSongs().sortedBy { it.songs.size }
                PLAYLIST_SONG_COUNT_DESC -> playlistDao.playlistsWithSongs()
                    .sortedByDescending { it.songs.size }
                else -> playlistDao.playlistsWithSongs().sortedBy {
                    it.playlistEntity.playlistName
                }
            }
        }
    }

    @WorkerThread
    override suspend fun insertSongs(songs: List<SongEntity>) = withContext(ioDispatcher) {
        playlistDao.insertSongsToPlaylist(songs)
    }

    override fun getSongsFromPlaylistLiveData(playListId: Long): LiveData<List<SongEntity>> =
        playlistDao.songsFromPlaylistLiveData(playListId).mapAsync(applicationScope) { songEntities ->
            val validSongIds = validSongIds()
            songEntities.filter {
                validSongIds.contains(it.songId)
            }
        }

    override suspend fun getSongsFromPlaylist(playListId: Long): List<SongEntity> {
        cleanInvalidPlaylistSongRecords()
        return withContext(ioDispatcher) {
            playlistDao.songsFromPlaylist(playListId)
        }
    }

    override fun checkPlaylistExists(playListId: Long): LiveData<Boolean> =
        playlistDao.checkPlaylistExists(playListId)

    override suspend fun deletePlaylistEntities(playlistEntities: List<PlaylistEntity>) = withContext(ioDispatcher) {
        playlistDao.deletePlaylists(playlistEntities)
    }

    override suspend fun renamePlaylistEntity(playlistId: Long, name: String) = withContext(ioDispatcher) {
        playlistDao.renamePlaylist(playlistId, name)
    }

    override suspend fun deleteSongsInPlaylist(songs: List<SongEntity>) = withContext(ioDispatcher) {
        songs.forEach {
            playlistDao.deleteSongFromPlaylist(it.playlistId, it.songId)
        }
    }

    override suspend fun deletePlaylistSongs(playlists: List<PlaylistEntity>) = withContext(ioDispatcher) {
        playlists.forEach {
            playlistDao.deletePlaylistSongs(it.playlistId)
        }
    }

    override suspend fun removeSongFromPlaylist(songEntity: SongEntity) = withContext(ioDispatcher) {
        playlistDao.deleteSongFromPlaylist(songEntity.playlistId, songEntity.songId)
    }

    override suspend fun addSongToHistory(currentSong: Song) = withContext(ioDispatcher) {
        historyDao.insertIntoHistory(currentSong.toHistoryEntity(System.currentTimeMillis(), 1))
    }

    override suspend fun addHistoryEntitiesToHistory(historyEntities: List<HistoryEntity>) = withContext(ioDispatcher) {
        historyDao.insertIntoHistory(historyEntities)
    }

    override suspend fun songPresentInHistory(song: Song): HistoryEntity? = withContext(ioDispatcher) {
        historyDao.isSongPresentInHistory(song.id)
    }

    override suspend fun updateHistorySong(historyEntity: HistoryEntity) = withContext(ioDispatcher) {
        historyDao.updateHistorySong(historyEntity)
    }

    override fun observableHistorySongs() =
        historyDao.observableHistorySongs().mapAsync(applicationScope) { history ->
            val validSongIds = validSongIds()
            history.filter {
                validSongIds.contains(it.songId)
            }
        }

    override fun observablePlayCountSongs() =
        historyDao.observablePlayCountSongs().mapAsync(applicationScope) { history ->
            val validSongIds = validSongIds()
            history.filter {
                validSongIds.contains(it.songId)
            }
        }

    override suspend fun historySongs(): List<HistoryEntity> {
        cleanInvalidHistoryRecords()
        return withContext(ioDispatcher) {
            historyDao.historySongs()
        }
    }

    override suspend fun playCountSongs(): List<HistoryEntity> {
        cleanInvalidHistoryRecords()
        return withContext(ioDispatcher) {
            historyDao.playCountSongs()
        }
    }

    override suspend fun historySongsReversed(): List<HistoryEntity> {
        cleanInvalidHistoryRecords()
        return withContext(ioDispatcher) {
            historyDao.historySongsReversed()
        }
    }

    override suspend fun historySize(): Int {
        cleanInvalidHistoryRecords()
        return withContext(ioDispatcher) {
            historyDao.historySize()
        }
    }

    override fun favoriteSongsLiveData(): LiveData<List<SongEntity>> {
        val liveData = MediatorLiveData<List<SongEntity>>()
        applicationScope.launch {
            val favoritesLiveData = withContext(ioDispatcher) {
                playlistDao.songsFromPlaylistLiveData(favoritePlaylistId()).mapAsync(applicationScope) { songEntities ->
                    val validSongIds = validSongIds()
                    songEntities.filter {
                        validSongIds.contains(it.songId)
                    }
                }
            }
            withContext(Dispatchers.Main) {
                liveData.addSource(favoritesLiveData) {
                    liveData.value = it
                }
            }
        }
        return liveData
    }

    override suspend fun deleteSongInHistory(songId: Long) = withContext(ioDispatcher) {
        historyDao.deleteSongInHistory(songId)
    }

    override suspend fun clearSongHistory() = withContext(ioDispatcher) {
        historyDao.clearHistory()
    }

    override suspend fun deleteSong(song: Song) = withContext(ioDispatcher) {
        historyDao.deleteSongInHistory(song.id)
        playlistDao.removeSongFromAllPlaylists(song.id)
    }

    override suspend fun isSongFavorite(songId: Long) = withContext(ioDispatcher) {
        playlistDao.isSongInPlaylist(songId, favoritePlaylistId()) != 0
    }

    override suspend fun addSongToFavorites(songId: Long) = withContext(ioDispatcher) {
        playlistDao.insertSongsToPlaylist(
            listOf(
                SongEntity(playlistId = favoritePlaylistId(), songId = songId)
            )
        )
    }

    override suspend fun removeSongFromFavorites(songId: Long) = withContext(ioDispatcher) {
        playlistDao.deleteSongFromPlaylist(
            favoritePlaylistId(), songId
        )
    }

    override suspend fun favoriteSongs() = getSongsFromPlaylist(favoritePlaylistId())

    override suspend fun addBlacklistPath(path: String) {
        withContext(ioDispatcher) {
            blacklistDao.insert(BlacklistEntity(path))
        }
        cleanInvalidRecords()
    }

    override suspend fun removeBlacklistPath(path: String) {
        withContext(ioDispatcher) {
            blacklistDao.delete(BlacklistEntity(path))
        }
        songRepository.updateSongCache()?.join()
    }

    override suspend fun getBlacklistPaths(): List<String> = withContext(ioDispatcher) {
        blacklistDao.getAll().map(BlacklistEntity::path)
    }

    override fun getBlacklistPathsLiveData() = blacklistDao.getLiveData()

    override suspend fun clearBlacklist() {
        withContext(ioDispatcher) {
            blacklistDao.deleteAll()
        }
        songRepository.updateSongCache()?.join()
    }

    override suspend fun addWhitelistPath(path: String) {
        withContext(ioDispatcher) {
            whitelistDao.insert(WhitelistEntity(path))
        }
        songRepository.updateSongCache()?.join()
    }

    override suspend fun removeWhitelistPath(path: String) {
        withContext(ioDispatcher) {
            whitelistDao.delete(WhitelistEntity(path))
        }
        cleanInvalidRecords()
    }

    override suspend fun getWhitelistPaths(): List<String> = withContext(ioDispatcher) {
        whitelistDao.getAll().map(WhitelistEntity::path)
    }

    override fun getWhitelistPathsLiveData() = whitelistDao.getLiveData()

    override suspend fun clearWhitelist() {
        withContext(ioDispatcher) {
            whitelistDao.deleteAll()
        }
        cleanInvalidRecords()
    }

    override suspend fun setQueue(queue: List<QueueEntity>) = withContext(ioDispatcher) {
        queueDao.deleteQueue()
        queueDao.insertQueue(queue)
    }

    override suspend fun setOriginalQueue(originalQueue: List<OriginalQueueEntity>) = withContext(ioDispatcher) {
        originalQueueDao.deleteOriginalQueue()
        originalQueueDao.insertOriginalQueue(originalQueue)
    }

    override suspend fun getQueue(): List<QueueEntity> {
        val validSongIds = validSongIds()
        return withContext(ioDispatcher) {
            queueDao.getQueue().filter {
                validSongIds.contains(it.songId)
            }
        }
    }

    override suspend fun getOriginalQueue(): List<OriginalQueueEntity> {
        val validSongIds = validSongIds()
        return withContext(ioDispatcher) {
            originalQueueDao.getOriginalQueue().filter {
                validSongIds.contains(it.songId)
            }
        }
    }

    override suspend fun cleanInvalidRecords() {
        val validSongIds = validSongIds()
        cleanInvalidHistoryRecords0(validSongIds)
        cleanInvalidPlaylistSongRecords0(validSongIds)
        println("Cleaned up invalid records")
    }

    private suspend fun favoritePlaylistId() = withContext(ioDispatcher) {
        val favoritePlaylists = playlistDao.playlistsWithTag(PlaylistDao.FAVORITES)

        if (favoritePlaylists.isEmpty()) {
            playlistDao.createPlaylist(
                PlaylistEntity(
                    playlistName = context.getString(R.string.favorites),
                    playlistTag = PlaylistDao.FAVORITES
                )
            )
        } else favoritePlaylists.first().playlistId
    }

    private suspend fun setDefaultBlacklist() = withContext(ioDispatcher) {
        if (!PreferenceUtil.isInitializedBlacklist) {
            clearBlacklist()
            addBlacklistPath(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_ALARMS)
                    .safeCanonicalPath()
            )
            addBlacklistPath(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_NOTIFICATIONS)
                    .safeCanonicalPath()
            )
            addBlacklistPath(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_RINGTONES)
                    .safeCanonicalPath()
            )
            PreferenceUtil.isInitializedBlacklist = true
        }
    }

    private suspend fun setDefaultWhitelist() = withContext(ioDispatcher) {
        if (!PreferenceUtil.isInitializedWhitelist) {
            clearWhitelist()
            addWhitelistPath(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
                    .safeCanonicalPath()
            )
            PreferenceUtil.isInitializedWhitelist = true
        }
    }

    private suspend fun cleanInvalidHistoryRecords() = cleanInvalidHistoryRecords0(validSongIds())

    private suspend fun cleanInvalidHistoryRecords0(validSongIds: List<Long>) {
        val completeHistory = withContext(ioDispatcher) {
            historyDao.getAllHistorySongs()
        }

        val invalidHistoryEntities = withContext(cpuDispatcher) {
            completeHistory.filter {
                !validSongIds.contains(it.songId)
            }
        }

        withContext(ioDispatcher) {
            historyDao.deleteHistorySongs(invalidHistoryEntities)
        }
    }

    private suspend fun cleanInvalidPlaylistSongRecords() = cleanInvalidPlaylistSongRecords0(validSongIds())

    private suspend fun cleanInvalidPlaylistSongRecords0(validSongIds: List<Long>) {
        val playlistSongEntities = withContext(ioDispatcher) {
            playlistDao.getAllSongEntities()
        }

        val invalidPlaylistSongEntities = withContext(cpuDispatcher) {
            playlistSongEntities.filter {
                !validSongIds.contains(it.songId)
            }
        }

        withContext(ioDispatcher) {
            playlistDao.deletePlaylistSongs(invalidPlaylistSongEntities)
        }
    }

    private suspend fun validSongIds() = withContext(ioDispatcher) {
        songRepository.updateSongCache()?.join()
        songRepository.songs().map(Song::id)
    }

}