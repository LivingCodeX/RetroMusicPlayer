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

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.BaseColumns
import android.provider.MediaStore
import android.provider.MediaStore.Audio.AudioColumns
import android.provider.MediaStore.Audio.Media
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import code.name.monkey.appthemehelper.util.VersionUtils
import code.name.monkey.retromusic.Constants.IS_MUSIC
import code.name.monkey.retromusic.Constants.baseProjection
import code.name.monkey.retromusic.extensions.getInt
import code.name.monkey.retromusic.extensions.getLong
import code.name.monkey.retromusic.extensions.getString
import code.name.monkey.retromusic.extensions.getStringOrNull
import code.name.monkey.retromusic.helper.MusicPlayerRemote.removeFromQueue
import code.name.monkey.retromusic.helper.SortOrder
import code.name.monkey.retromusic.model.Song
import code.name.monkey.retromusic.util.PreferenceUtil
import kotlinx.coroutines.*
import java.text.Collator

/**
 * Created by hemanths on 10/08/17.
 */
interface SongRepository {

    fun songs(): List<Song>

    fun songsLiveData(): LiveData<List<Song>>

    fun sortedSongs(): List<Song>

    fun sortedSongsLiveData(): LiveData<List<Song>>

    fun songs(cursor: Cursor?): List<Song>

    fun sortedSongs(cursor: Cursor?): List<Song>

    suspend fun songs(query: String): List<Song>

    suspend fun songsByFilePath(filePath: String, ignoreBlacklist: Boolean = false): List<Song>

    fun song(cursor: Cursor?): Song

    fun song(songId: Long): Song

    suspend fun songsIgnoreBlacklist(uri: Uri): List<Song>

    fun updateSongCache(): Job?

}

class RealSongRepository(
    private val context: Context,
    private val applicationScope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val roomRepository: RoomRepository
) : SongRepository {

    private val songObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            onChange(selfChange, null)
        }

        override fun onChange(selfChange: Boolean, uri: Uri?) {
            //Reload everything for now
            updateSongCache()
        }

        @SuppressLint("SwitchIntDef")
        override fun onChange(selfChange: Boolean, uri: Uri?, flags: Int) {
            if (uri == null) return

            val songId = ContentUris.parseId(uri)

            when (flags) {
                ContentResolver.NOTIFY_INSERT -> {
                    applicationScope.launch {
                        val newSong = loadSongById(songId)
                        if (newSong != Song.emptySong) {
                            songs.add(newSong)
                            withContext(Dispatchers.Main) {
                                songsLiveData.value = songs
                            }
                        }
                    }
                }

                ContentResolver.NOTIFY_DELETE -> {
                    removeFromQueue(songId)

                    var songToRemove: Song? = null
                    for (song in songs) {
                        if (song.id == songId) {
                            songToRemove = song
                            break
                        }
                    }
                    if (songToRemove != null) {
                        songs.remove(songToRemove)
                        songsLiveData.value = songs
                    }
                }

                ContentResolver.NOTIFY_UPDATE -> {
                    applicationScope.launch {
                        val newSong = loadSongById(songId)
                        if (newSong == Song.emptySong) return@launch

                        var songToUpdate: Song? = null
                        for (song in songs) {
                            if (song.id == songId) {
                                songToUpdate = song
                                break
                            }
                        }
                        if (songToUpdate != null) {
                            songs.remove(songToUpdate)
                        }

                        songs.add(newSong)
                        withContext(Dispatchers.Main) {
                            songsLiveData.value = songs
                        }
                    }
                }
            }
        }
    }

    private val mediaUri by lazy {
        if (VersionUtils.hasQ()) {
            Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            Media.EXTERNAL_CONTENT_URI
        }
    }

    private var songLoadingJob: Job? = null
    private val songs = arrayListOf<Song>()
    private val songsLiveData = MutableLiveData<List<Song>>()

    init {
        updateSongCache()
        context.contentResolver.registerContentObserver(
            mediaUri, true, songObserver
        )
    }

    override fun songs(): List<Song> {
        return songs.toList()
    }

    override fun songsLiveData(): LiveData<List<Song>> {
        return songsLiveData
    }

    override fun sortedSongs() = sortSongs(songs)

    override fun sortedSongsLiveData() = Transformations.map(songsLiveData) { songs ->
        sortSongs(songs)
    }

    override fun songs(cursor: Cursor?): List<Song> {
        val songs = arrayListOf<Song>()
        if (cursor != null && cursor.moveToFirst()) {
            do {
                songs.add(getSongFromCursorImpl(cursor))
            } while (cursor.moveToNext())
        }
        cursor?.close()
        return songs
    }

    override fun sortedSongs(cursor: Cursor?)= sortSongs(songs(cursor))

    override fun song(cursor: Cursor?): Song {
        val song: Song = if (cursor != null && cursor.moveToFirst()) {
            getSongFromCursorImpl(cursor)
        } else {
            Song.emptySong
        }
        cursor?.close()
        return song
    }

    override suspend fun songs(query: String): List<Song> {
        return songs(makeSongCursor(AudioColumns.TITLE + " LIKE ?", arrayOf("%$query%")))
    }

    override fun song(songId: Long): Song {
        return songs.firstOrNull { it.id == songId } ?: Song.emptySong
    }

    override suspend fun songsByFilePath(filePath: String, ignoreBlacklist: Boolean): List<Song> {
        return songs(
            makeSongCursor(
                AudioColumns.DATA + "=?",
                arrayOf(filePath),
                ignoreBlacklist = ignoreBlacklist
            )
        )
    }

    override suspend fun songsIgnoreBlacklist(uri: Uri): List<Song> {
        var filePath = ""
        withContext(ioDispatcher) {
            context.contentResolver.query(
                uri,
                arrayOf(AudioColumns.DATA),
                null,
                null,
                null
            ).use { cursor ->
                if (cursor != null) {
                    if (cursor.count != 0) {
                        cursor.moveToFirst()
                        filePath = cursor.getString(AudioColumns.DATA)
                        println("File Path: $filePath")
                    }
                }
            }
        }
        return songsByFilePath(
            filePath, true
        )
    }

    override fun updateSongCache(): Job? {
        if (songLoadingJob == null) {
            songLoadingJob = applicationScope.launch {
                songs.clear()
                songs.addAll(songs(makeSongCursor(null, null)))
                withContext(Dispatchers.Main) {
                    songsLiveData.value = songs
                }
                songLoadingJob = null
            }
        }
        return songLoadingJob
    }

    private fun getSongFromCursorImpl(
        cursor: Cursor
    ): Song {
        val id = cursor.getLong(AudioColumns._ID)
        val title = cursor.getString(AudioColumns.TITLE)
        val trackNumber = cursor.getInt(AudioColumns.TRACK)
        val year = cursor.getInt(AudioColumns.YEAR)
        val duration = cursor.getLong(AudioColumns.DURATION)
        val data = cursor.getString(AudioColumns.DATA)
        val dateModified = cursor.getLong(AudioColumns.DATE_MODIFIED)
        val albumId = cursor.getLong(AudioColumns.ALBUM_ID)
        val albumName = cursor.getStringOrNull(AudioColumns.ALBUM)
        val artistId = cursor.getLong(AudioColumns.ARTIST_ID)
        val artistName = cursor.getStringOrNull(AudioColumns.ARTIST)
        val composer = cursor.getStringOrNull(AudioColumns.COMPOSER)
        val albumArtist = cursor.getStringOrNull("album_artist")
        return Song(
            id,
            title,
            trackNumber,
            year,
            duration,
            data,
            dateModified,
            albumId,
            albumName ?: "",
            artistId,
            artistName ?: "",
            composer ?: "",
            albumArtist ?: ""
        )
    }

    @JvmOverloads
    suspend fun makeSongCursor(
        selection: String?,
        selectionValues: Array<String>?,
        sortOrder: String = PreferenceUtil.songSortOrder,
        ignoreBlacklist: Boolean = false
    ): Cursor? {
        var selectionFinal = selection
        var selectionValuesFinal = selectionValues
        if (!ignoreBlacklist) {
            selectionFinal = if (selection != null && selection.trim { it <= ' ' } != "") {
                "$IS_MUSIC AND $selectionFinal"
            } else {
                IS_MUSIC
            }

            // Whitelist
            if (PreferenceUtil.isWhiteList) {
                val paths = roomRepository.getWhitelistPaths()
                if (paths.isNotEmpty()) {
                    selectionFinal = generateWhitelistSelection(selectionFinal, paths.size)
                    selectionValuesFinal = addSelectionValues(selectionValuesFinal, paths)
                } else {
                    return null
                }
            } else {
                // Blacklist
                val paths = roomRepository.getBlacklistPaths()
                if (paths.isNotEmpty()) {
                    selectionFinal = generateBlacklistSelection(selectionFinal, paths.size)
                    selectionValuesFinal = addSelectionValues(selectionValuesFinal, paths)
                }
            }

            selectionFinal =
                selectionFinal + " AND " + Media.DURATION + ">= " + (PreferenceUtil.filterLength * 1000)
        }
        return withContext(ioDispatcher) {
            try {
                context.contentResolver.query(
                    mediaUri,
                    baseProjection,
                    selectionFinal,
                    selectionValuesFinal,
                    sortOrder
                )
            } catch (ex: SecurityException) {
                null
            }
        }
    }

    private fun generateBlacklistSelection(
        selection: String?,
        pathCount: Int
    ): String {
        val newSelection = StringBuilder(
            if (selection != null && selection.trim { it <= ' ' } != "") "$selection AND " else "")
        newSelection.append(AudioColumns.DATA + " NOT LIKE ?")
        for (i in 0 until pathCount - 1) {
            newSelection.append(" AND " + AudioColumns.DATA + " NOT LIKE ?")
        }
        return newSelection.toString()
    }

    private fun addSelectionValues(
        selectionValues: Array<String>?,
        paths: List<String>
    ): Array<String> {
        var selectionValuesFinal = selectionValues
        if (selectionValuesFinal == null) {
            selectionValuesFinal = emptyArray()
        }
        val newSelectionValues = Array(selectionValuesFinal.size + paths.size) {
            "n = $it"
        }
        System.arraycopy(selectionValuesFinal, 0, newSelectionValues, 0, selectionValuesFinal.size)
        for (i in selectionValuesFinal.size until newSelectionValues.size) {
            newSelectionValues[i] = paths[i - selectionValuesFinal.size] + "%"
        }
        return newSelectionValues
    }

    private fun generateWhitelistSelection(
        selection: String?,
        pathCount: Int
    ): String {
        val newSelection = StringBuilder(
            if (selection != null && selection.trim { it <= ' ' } != "") "$selection AND " else "")
        newSelection.append("(" + AudioColumns.DATA + " LIKE ?")
        for (i in 0 until pathCount - 1) {
            newSelection.append(" OR " + AudioColumns.DATA + " LIKE ?")
        }
        newSelection.append(")")
        return newSelection.toString()
    }

    private suspend fun loadSongById(songId: Long): Song {
        return song(makeSongCursor("${BaseColumns._ID} LIKE ?", arrayOf("$songId")))
    }

    private fun sortSongs(songs: List<Song>): List<Song> {
        val collator = Collator.getInstance()
        return when (PreferenceUtil.songSortOrder) {
            SortOrder.SongSortOrder.SONG_A_Z -> {
                songs.sortedWith{ s1, s2 -> collator.compare(s1.title, s2.title) }
            }
            SortOrder.SongSortOrder.SONG_Z_A -> {
                songs.sortedWith{ s1, s2 -> collator.compare(s2.title, s1.title) }
            }
            SortOrder.SongSortOrder.SONG_ALBUM -> {
                songs.sortedWith{ s1, s2 -> collator.compare(s1.albumName, s2.albumName) }
            }
            SortOrder.SongSortOrder.SONG_ALBUM_ARTIST -> {
                songs.sortedWith{ s1, s2 -> collator.compare(s1.albumArtist, s2.albumArtist) }
            }
            SortOrder.SongSortOrder.SONG_ARTIST -> {
                songs.sortedWith{ s1, s2 -> collator.compare(s1.artistName, s2.artistName) }
            }
            SortOrder.SongSortOrder.COMPOSER -> {
                songs.sortedWith{ s1, s2 -> collator.compare(s1.composer, s2.composer) }
            }
            else -> songs
        }
    }

}
