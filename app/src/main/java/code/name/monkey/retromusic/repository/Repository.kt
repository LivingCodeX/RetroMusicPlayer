/*
 * Copyright (c) 2019 Hemanth Savarala.
 *
 * Licensed under the GNU General Public License v3
 *
 * This is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by
 *  the Free Software Foundation either version 3 of the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 */

package code.name.monkey.retromusic.repository

import android.content.Context
import android.content.Intent
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.Transformations
import code.name.monkey.retromusic.*
import code.name.monkey.retromusic.db.*
import code.name.monkey.retromusic.fragments.search.Filter
import code.name.monkey.retromusic.model.*
import code.name.monkey.retromusic.model.smartplaylist.NotPlayedPlaylist
import code.name.monkey.retromusic.network.LastFMService
import code.name.monkey.retromusic.network.Result
import code.name.monkey.retromusic.network.Result.*
import code.name.monkey.retromusic.network.model.LastFmAlbum
import code.name.monkey.retromusic.network.model.LastFmArtist
import code.name.monkey.retromusic.service.MusicService
import code.name.monkey.retromusic.util.PreferenceUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow

interface Repository {

    fun songsFlow(): Flow<Result<List<Song>>>
    fun albumsFlow(): Flow<Result<List<Album>>>
    fun artistsFlow(): Flow<Result<List<Artist>>>
    fun playlistsFlow(): Flow<Result<List<Playlist>>>
    fun genresFlow(): Flow<Result<List<Genre>>>
    suspend fun historySongs(): List<HistoryEntity>
    suspend fun playCountSongs(): List<HistoryEntity>
    fun observableFavorites(owner: LifecycleOwner): LiveData<List<Song>>
    fun observableHistorySongs(owner: LifecycleOwner): LiveData<List<Song>>
    fun observablePlayCountSongs(owner: LifecycleOwner): LiveData<List<Song>>
    suspend fun albumById(albumId: Long): Album
    fun playlistSongsLiveData(playListId: Long, owner: LifecycleOwner): LiveData<List<Song>?>
    suspend fun playlistSongs(playListId: Long): List<SongEntity>
    suspend fun fetchAlbums(): List<Album>
    suspend fun albumByIdAsync(albumId: Long): Album
    suspend fun allSongs(): List<Song>
    suspend fun sortedSongs(): List<Song>
    fun songsLiveData(): LiveData<List<Song>>
    fun sortedSongsLiveData(): LiveData<List<Song>>
    suspend fun fetchArtists(): List<Artist>
    suspend fun albumArtists(): List<Artist>
    suspend fun fetchLegacyPlaylist(): List<Playlist>
    suspend fun fetchGenres(): List<Genre>
    suspend fun search(query: String?, filter: Filter): MutableList<Any>
    suspend fun getGenre(genreId: Long): List<Song>
    suspend fun artistInfo(name: String, lang: String?, cache: String?): Result<LastFmArtist>
    suspend fun albumInfo(artist: String, album: String): Result<LastFmAlbum>
    suspend fun artistById(artistId: Long): Artist
    suspend fun albumArtistByName(name: String): Artist
    suspend fun recentArtists(): List<Artist>
    suspend fun topArtists(): List<Artist>
    suspend fun topAlbums(): List<Album>
    suspend fun recentAlbums(): List<Album>
    suspend fun recentArtistsHome(): Home
    suspend fun topArtistsHome(): Home
    suspend fun topAlbumsHome(): Home
    suspend fun recentAlbumsHome(): Home
    suspend fun favoritePlaylistHome(): Home
    suspend fun suggestionsHome(): Home
    suspend fun suggestions(): List<Song>
    suspend fun genresHome(): Home
    suspend fun playlists(): Home
    suspend fun homeSections(): List<Home>

    @ExperimentalCoroutinesApi
    suspend fun homeSectionsFlow(): Flow<Result<List<Home>>>
    suspend fun playlist(playlistId: Long): Playlist
    suspend fun fetchPlaylistWithSongs(): List<PlaylistWithSongs>
    suspend fun playlistSongs(playlistWithSongs: PlaylistWithSongs): List<Song>
    suspend fun insertSongs(songs: List<SongEntity>)
    suspend fun checkPlaylistExists(playlistName: String): List<PlaylistEntity>
    suspend fun createPlaylist(playlistEntity: PlaylistEntity): Long
    suspend fun fetchPlaylists(): List<PlaylistEntity>
    suspend fun deleteRoomPlaylist(playlists: List<PlaylistEntity>)
    suspend fun renameRoomPlaylist(playlistId: Long, name: String)
    suspend fun deleteSongsInPlaylist(songs: List<SongEntity>)
    suspend fun removeSongFromPlaylist(songEntity: SongEntity)
    suspend fun deletePlaylistSongs(playlists: List<PlaylistEntity>)
    suspend fun addSongToHistory(currentSong: Song)
    suspend fun addHistoryEntitiesToHistory(historyEntities: List<HistoryEntity>)
    suspend fun songPresentInHistory(currentSong: Song): HistoryEntity?
    suspend fun updateHistorySong(historyEntity: HistoryEntity)
    suspend fun favoritePlaylistSongs(): List<SongEntity>
    suspend fun recentSongs(): List<Song>
    fun recentSongsLiveData(scope: CoroutineScope, owner: LifecycleOwner): LiveData<List<Song>>
    suspend fun topPlayedSongs(): List<Song>
    suspend fun deleteSongInHistory(songId: Long)
    suspend fun clearSongHistory()
    suspend fun deleteSong(song: Song)
    suspend fun contributor(): List<Contributor>
    suspend fun searchArtists(query: String): List<Artist>
    suspend fun searchSongs(query: String): List<Song>
    suspend fun searchAlbums(query: String): List<Album>
    suspend fun isSongFavorite(songId: Long): Boolean
    suspend fun addSongToFavorites(songId: Long)
    suspend fun removeSongFromFavorites(songId: Long)
    suspend fun getSongByGenre(genreId: Long): Song
    fun checkPlaylistExists(playListId: Long): LiveData<Boolean>
    suspend fun addBlacklistPath(path: String)
    suspend fun removeBlacklistPath(path: String)
    suspend fun clearBlacklist()
    suspend fun getBlacklistPaths(): List<String>
    fun getBlacklistPathsLiveData(): LiveData<List<String>>
    suspend fun addWhitelistPath(path: String)
    suspend fun removeWhitelistPath(path: String)
    suspend fun clearWhitelist()
    suspend fun getWhitelistPaths(): List<String>
    fun getWhitelistPathsLiveData(): LiveData<List<String>>
    suspend fun setQueue(queue: List<Song>)
    suspend fun setOriginalQueue(originalQueue: List<Song>)
    suspend fun getQueue(): List<Song>
    suspend fun getOriginalQueue(): List<Song>
}

class RealRepository(
    private val context: Context,
    private val applicationScope: CoroutineScope,
    private val lastFMService: LastFMService,
    private val songRepository: SongRepository,
    private val albumRepository: AlbumRepository,
    private val artistRepository: ArtistRepository,
    private val genreRepository: GenreRepository,
    private val lastAddedRepository: LastAddedRepository,
    private val playlistRepository: PlaylistRepository,
    private val searchRepository: RealSearchRepository,
    private val topPlayedRepository: TopPlayedRepository,
    private val roomRepository: RoomRepository,
    private val localDataRepository: LocalDataRepository
) : Repository {


    override suspend fun deleteSong(song: Song) = roomRepository.deleteSong(song)

    override suspend fun contributor(): List<Contributor> = localDataRepository.contributors()

    override suspend fun searchSongs(query: String): List<Song> = songRepository.songs(query)

    override suspend fun searchAlbums(query: String): List<Album> = albumRepository.albums(query)

    override suspend fun isSongFavorite(songId: Long): Boolean =
        roomRepository.isSongFavorite(songId)

    override suspend fun addSongToFavorites(songId: Long) = roomRepository.addSongToFavorites(songId)

    override suspend fun removeSongFromFavorites(songId: Long) = roomRepository.removeSongFromFavorites(songId)

    override suspend fun getSongByGenre(genreId: Long): Song = genreRepository.song(genreId)

    override suspend fun searchArtists(query: String): List<Artist> =
        artistRepository.artists(query)

    override suspend fun fetchAlbums(): List<Album> = albumRepository.albums()

    override suspend fun albumByIdAsync(albumId: Long): Album = albumRepository.album(albumId)

    override suspend fun albumById(albumId: Long): Album = albumRepository.album(albumId)

    override suspend fun fetchArtists(): List<Artist> = artistRepository.artists()

    override suspend fun albumArtists(): List<Artist> = artistRepository.albumArtists()

    override suspend fun artistById(artistId: Long): Artist = artistRepository.artist(artistId)

    override suspend fun albumArtistByName(name: String): Artist =
        artistRepository.albumArtist(name)

    override suspend fun recentArtists(): List<Artist> = lastAddedRepository.recentArtists()

    override suspend fun recentAlbums(): List<Album> = lastAddedRepository.recentAlbums()

    override suspend fun topArtists(): List<Artist> = topPlayedRepository.topArtists()

    override suspend fun topAlbums(): List<Album> = topPlayedRepository.topAlbums()

    override suspend fun fetchLegacyPlaylist(): List<Playlist> = playlistRepository.playlists()

    override suspend fun fetchGenres(): List<Genre> = genreRepository.genres()

    override suspend fun allSongs(): List<Song> = songRepository.songs()

    override suspend fun sortedSongs(): List<Song> = songRepository.sortedSongs()

    override fun songsLiveData(): LiveData<List<Song>> = songRepository.songsLiveData()

    override fun sortedSongsLiveData(): LiveData<List<Song>> = songRepository.sortedSongsLiveData()

    override suspend fun search(query: String?, filter: Filter): MutableList<Any> =
        searchRepository.searchAll(context, query, filter)

    override suspend fun getGenre(genreId: Long): List<Song> = genreRepository.songs(genreId)

    override suspend fun artistInfo(
        name: String,
        lang: String?,
        cache: String?
    ): Result<LastFmArtist> {
        return try {
            Success(lastFMService.artistInfo(name, lang, cache))
        } catch (e: Exception) {
            println(e)
            Error(e)
        }
    }

    override suspend fun albumInfo(
        artist: String,
        album: String
    ): Result<LastFmAlbum> {
        return try {
            val lastFmAlbum = lastFMService.albumInfo(artist, album)
            Success(lastFmAlbum)
        } catch (e: Exception) {
            println(e)
            Error(e)
        }
    }

    @ExperimentalCoroutinesApi
    override suspend fun homeSectionsFlow(): Flow<Result<List<Home>>> {
        val homes = MutableStateFlow<Result<List<Home>>>(value = Loading)
        val homeSections = mutableListOf<Home>()
        val sections = listOf(
            topArtistsHome(),
            topAlbumsHome(),
            recentArtistsHome(),
            recentAlbumsHome(),
            suggestionsHome(),
            favoritePlaylistHome(),
            genresHome()
        )
        for (section in sections) {
            if (section.arrayList.isNotEmpty()) {
                println("${section.homeSection} -> ${section.arrayList.size}")
                homeSections.add(section)
            }
        }
        if (homeSections.isEmpty()) {
            homes.value = Error(Exception(Throwable("No items")))
        } else {
            homes.value = Success(homeSections)
        }
        return homes
    }

    override suspend fun homeSections(): List<Home> {
        val homeSections = mutableListOf<Home>()
        val sections: List<Home> = listOf(
            topArtistsHome(),
            topAlbumsHome(),
            recentArtistsHome(),
            recentAlbumsHome(),
            favoritePlaylistHome()
        )
        for (section in sections) {
            if (section.arrayList.isNotEmpty()) {
                homeSections.add(section)
            }
        }
        return homeSections
    }


    override suspend fun playlist(playlistId: Long) =
        playlistRepository.playlist(playlistId)

    override suspend fun fetchPlaylistWithSongs(): List<PlaylistWithSongs> =
        roomRepository.playlistWithSongs()

    override suspend fun playlistSongs(playlistWithSongs: PlaylistWithSongs): List<Song> =
        playlistWithSongs.songs.toSongs()

    override fun playlistSongsLiveData(playListId: Long, owner: LifecycleOwner): LiveData<List<Song>?> {
        val mediatorLiveData = MediatorLiveData<List<Song>?>()

        val lastPlaylistSongs = arrayListOf<SongEntity>()
        roomRepository.getSongsFromPlaylistLiveData(playListId).observe(owner) {
            lastPlaylistSongs.clear()
            lastPlaylistSongs.addAll(it)
            mediatorLiveData.value = it.toSongs()
        }
        songsLiveData().observe(owner) {
            println("${lastPlaylistSongs.size}")
            if (lastPlaylistSongs.isNotEmpty()) {
                mediatorLiveData.value = lastPlaylistSongs.toSongs()
            }
        }

        return mediatorLiveData
    }

    override suspend fun playlistSongs(playListId: Long): List<SongEntity> =
        roomRepository.getSongsFromPlaylist(playListId)

    override suspend fun insertSongs(songs: List<SongEntity>) =
        roomRepository.insertSongs(songs)

    override suspend fun checkPlaylistExists(playlistName: String): List<PlaylistEntity> =
        roomRepository.checkPlaylistExists(playlistName)

    override fun checkPlaylistExists(playListId: Long): LiveData<Boolean> =
        roomRepository.checkPlaylistExists(playListId)

    override suspend fun createPlaylist(playlistEntity: PlaylistEntity): Long =
        roomRepository.createPlaylist(playlistEntity)

    override suspend fun fetchPlaylists(): List<PlaylistEntity> = roomRepository.playlists()

    override suspend fun deleteRoomPlaylist(playlists: List<PlaylistEntity>) =
        roomRepository.deletePlaylistEntities(playlists)

    override suspend fun renameRoomPlaylist(playlistId: Long, name: String) =
        roomRepository.renamePlaylistEntity(playlistId, name)

    override suspend fun deleteSongsInPlaylist(songs: List<SongEntity>) =
        roomRepository.deleteSongsInPlaylist(songs)

    override suspend fun removeSongFromPlaylist(songEntity: SongEntity) =
        roomRepository.removeSongFromPlaylist(songEntity)

    override suspend fun deletePlaylistSongs(playlists: List<PlaylistEntity>) =
        roomRepository.deletePlaylistSongs(playlists)

    override suspend fun addSongToHistory(currentSong: Song) =
        roomRepository.addSongToHistory(currentSong)

    override suspend fun addHistoryEntitiesToHistory(historyEntities: List<HistoryEntity>) =
        roomRepository.addHistoryEntitiesToHistory(historyEntities)

    override suspend fun songPresentInHistory(currentSong: Song): HistoryEntity? =
        roomRepository.songPresentInHistory(currentSong)

    override suspend fun updateHistorySong(historyEntity: HistoryEntity) =
        roomRepository.updateHistorySong(historyEntity)

    override suspend fun favoritePlaylistSongs(): List<SongEntity> =
        roomRepository.favoriteSongs()

    override suspend fun recentSongs(): List<Song> = lastAddedRepository.recentSongs()

    override fun recentSongsLiveData(scope: CoroutineScope, owner: LifecycleOwner): LiveData<List<Song>> {
        val mediatorLiveData = MediatorLiveData<List<Song>>()

        scope.launch {
            val recentSongs = lastAddedRepository.recentSongs()

            if (recentSongs.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    mediatorLiveData.value = recentSongs

                    songsLiveData().observe(owner) {
                        mediatorLiveData.value = recentSongs.update()
                    }
                }
            }
        }

        return mediatorLiveData
    }

    override suspend fun topPlayedSongs(): List<Song> = topPlayedRepository.topTracks()

    override suspend fun deleteSongInHistory(songId: Long) =
        roomRepository.deleteSongInHistory(songId)

    override suspend fun clearSongHistory() {
        roomRepository.clearSongHistory()

    }

    override fun observableHistorySongs(owner: LifecycleOwner): LiveData<List<Song>> {
        val mediatorLiveData = MediatorLiveData<List<Song>>()

        val lastHistorySongs = arrayListOf<HistoryEntity>()
        roomRepository.observableHistorySongs().observe(owner) {
            lastHistorySongs.clear()
            lastHistorySongs.addAll(it)
            mediatorLiveData.value = it.historyToSongs()
        }
        songsLiveData().observe(owner) {
            if (lastHistorySongs.isNotEmpty()) {
                mediatorLiveData.value = lastHistorySongs.historyToSongs()
            }
        }

        return mediatorLiveData
    }

    override suspend fun historySongs(): List<HistoryEntity> =
        roomRepository.historySongs()

    override fun observablePlayCountSongs(owner: LifecycleOwner): LiveData<List<Song>> {
        val mediatorLiveData = MediatorLiveData<List<Song>>()

        val lastPlayCountSongs = arrayListOf<HistoryEntity>()
        roomRepository.observablePlayCountSongs().observe(owner) {
            lastPlayCountSongs.clear()
            lastPlayCountSongs.addAll(it)
            mediatorLiveData.value = it.historyToSongs()
        }
        songsLiveData().observe(owner) {
            if (lastPlayCountSongs.isNotEmpty()) {
                mediatorLiveData.value = lastPlayCountSongs.historyToSongs()
            }
        }

        return mediatorLiveData
    }

    override suspend fun playCountSongs(): List<HistoryEntity> =
        roomRepository.playCountSongs()

    override fun observableFavorites(owner: LifecycleOwner): LiveData<List<Song>> {
        val mediatorLiveData = MediatorLiveData<List<Song>>()

        val lastFavoriteSongs = arrayListOf<SongEntity>()
        roomRepository.favoriteSongsLiveData().observe(owner) {
            lastFavoriteSongs.clear()
            lastFavoriteSongs.addAll(it)
            mediatorLiveData.value = it.toSongs()
        }
        songsLiveData().observe(owner) {
            if (lastFavoriteSongs.isNotEmpty()) {
                mediatorLiveData.value = lastFavoriteSongs.toSongs()
            }
        }

        return mediatorLiveData
    }

    override suspend fun addBlacklistPath(path: String) {
        roomRepository.addBlacklistPath(path)
        notifyMediaStoreChanged()
    }

    override suspend fun removeBlacklistPath(path: String) {
        roomRepository.removeBlacklistPath(path)
        notifyMediaStoreChanged()
    }

    override suspend fun clearBlacklist() {
        roomRepository.clearBlacklist()
        notifyMediaStoreChanged()
    }

    override suspend fun getBlacklistPaths(): List<String> = roomRepository.getBlacklistPaths()

    override fun getBlacklistPathsLiveData() =
        Transformations.map(roomRepository.getBlacklistPathsLiveData()) {
            it.map(BlacklistEntity::path)
        }

    override suspend fun addWhitelistPath(path: String) {
        roomRepository.addWhitelistPath(path)
        notifyMediaStoreChanged()
    }

    override suspend fun removeWhitelistPath(path: String) {
        roomRepository.removeWhitelistPath(path)
        notifyMediaStoreChanged()
    }

    override suspend fun clearWhitelist() {
        roomRepository.clearWhitelist()
        notifyMediaStoreChanged()
    }

    override suspend fun getWhitelistPaths(): List<String> = roomRepository.getWhitelistPaths()

    override fun getWhitelistPathsLiveData() =
        Transformations.map(roomRepository.getWhitelistPathsLiveData()) {
            it.map(WhitelistEntity::path)
        }

    override suspend fun setQueue(queue: List<Song>) =
        roomRepository.setQueue(queue.toQueueEntities())

    override suspend fun setOriginalQueue(originalQueue: List<Song>) =
        roomRepository.setOriginalQueue(originalQueue.toOriginalQueueEntities())

    override suspend fun getQueue() =
        roomRepository.getQueue().queueToSongs()

    override suspend fun getOriginalQueue() =
        roomRepository.getOriginalQueue().originalQueueToSongs()

    var suggestions = Home(
        listOf(), SUGGESTIONS,
        R.string.suggestion_songs
    )

    override suspend fun suggestionsHome(): Home {
        if (!PreferenceUtil.homeSuggestions) return Home(
            listOf(),
            SUGGESTIONS,
            R.string.suggestion_songs
        )
        // Don't reload Suggestions everytime
        if (suggestions.arrayList.isEmpty()) {
            val songs = NotPlayedPlaylist().songs().takeIf {
                it.size > 9
            } ?: emptyList()
            suggestions = Home(songs, SUGGESTIONS, R.string.suggestion_songs)
        }
        return suggestions
    }

    override suspend fun suggestions(): List<Song> {
        if (!PreferenceUtil.homeSuggestions) return listOf()
        return NotPlayedPlaylist().songs().takeIf {
            it.size > 9
        } ?: emptyList()
    }

    override suspend fun genresHome(): Home {
        val genres = genreRepository.genres().shuffled()
        return Home(genres, GENRES, R.string.genres)
    }

    override suspend fun playlists(): Home {
        val playlist = playlistRepository.playlists()
        return Home(playlist, PLAYLISTS, R.string.playlists)
    }

    override suspend fun recentArtistsHome(): Home {
        val artists = lastAddedRepository.recentArtists().take(5)
        return Home(artists, RECENT_ARTISTS, R.string.recent_artists)
    }

    override suspend fun recentAlbumsHome(): Home {
        val albums = lastAddedRepository.recentAlbums().take(5)
        return Home(albums, RECENT_ALBUMS, R.string.recent_albums)
    }

    override suspend fun topAlbumsHome(): Home {
        val albums = topPlayedRepository.topAlbums().take(5)
        return Home(albums, TOP_ALBUMS, R.string.top_albums)
    }

    override suspend fun topArtistsHome(): Home {
        val artists = topPlayedRepository.topArtists().take(5)
        return Home(artists, TOP_ARTISTS, R.string.top_artists)
    }

    override suspend fun favoritePlaylistHome(): Home {
        val songs = favoritePlaylistSongs().toSongs()
        return Home(songs, FAVOURITES, R.string.favorites)
    }

    override fun songsFlow(): Flow<Result<List<Song>>> = flow {
        emit(Loading)
        val data = songRepository.songs()
        if (data.isEmpty()) {
            emit(Error(Exception(Throwable("No items"))))
        } else {
            emit(Success(data))
        }
    }

    override fun albumsFlow(): Flow<Result<List<Album>>> = flow {
        emit(Loading)
        val data = albumRepository.albums()
        if (data.isEmpty()) {
            emit(Error(Exception(Throwable("No items"))))
        } else {
            emit(Success(data))
        }
    }

    override fun artistsFlow(): Flow<Result<List<Artist>>> = flow {
        emit(Loading)
        val data = artistRepository.artists()
        if (data.isEmpty()) {
            emit(Error(Exception(Throwable("No items"))))
        } else {
            emit(Success(data))
        }
    }

    override fun playlistsFlow(): Flow<Result<List<Playlist>>> = flow {
        emit(Loading)
        val data = playlistRepository.playlists()
        if (data.isEmpty()) {
            emit(Error(Exception(Throwable("No items"))))
        } else {
            emit(Success(data))
        }
    }

    override fun genresFlow(): Flow<Result<List<Genre>>> = flow {
        emit(Loading)
        val data = genreRepository.genres()
        if (data.isEmpty()) {
            emit(Error(Exception(Throwable("No items"))))
        } else {
            emit(Success(data))
        }
    }

    private fun notifyMediaStoreChanged() {
        context.sendBroadcast(Intent(MusicService.MEDIA_STORE_CHANGED))
    }

}