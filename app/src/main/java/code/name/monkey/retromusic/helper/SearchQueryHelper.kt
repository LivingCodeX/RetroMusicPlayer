/*
 * Copyright (c) 2020 Hemanth Savarla.
 *
 * Licensed under the GNU General Public License v3
 *
 * This is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 */
package code.name.monkey.retromusic.helper

import android.app.SearchManager
import android.os.Bundle
import android.provider.MediaStore
import code.name.monkey.retromusic.model.Song
import code.name.monkey.retromusic.repository.RealSongRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

object SearchQueryHelper : KoinComponent {
    private val songRepository by inject<RealSongRepository>()
    var songs = ArrayList<Song>()

    @JvmStatic
    fun getSongs(extras: Bundle): List<Song> {
        val query = extras.getString(SearchManager.QUERY, null)
        val artistName = extras.getString(MediaStore.EXTRA_MEDIA_ARTIST, null)
        val albumName = extras.getString(MediaStore.EXTRA_MEDIA_ALBUM, null)
        val titleName = extras.getString(MediaStore.EXTRA_MEDIA_TITLE, null)

        var songs = listOf<Song>()
        if (artistName != null && albumName != null && titleName != null) {
            songs = songRepository.sortedSongs().filter {
                it.artistName.lowercase().startsWith(artistName.lowercase())
                        && it.albumName.lowercase().startsWith(albumName.lowercase())
                        && it.title.lowercase().startsWith(titleName.lowercase())
            }
        }
        if (songs.isNotEmpty()) {
            return songs
        }
        if (artistName != null && titleName != null) {
            songs = songRepository.sortedSongs().filter {
                it.artistName.lowercase().startsWith(artistName.lowercase())
                        && it.title.lowercase().startsWith(titleName.lowercase())
            }
        }
        if (songs.isNotEmpty()) {
            return songs
        }
        if (albumName != null && titleName != null) {
            songs = songRepository.sortedSongs().filter {
                it.albumName.lowercase().startsWith(albumName.lowercase())
                        && it.title.lowercase().startsWith(titleName.lowercase())
            }
        }
        if (songs.isNotEmpty()) {
            return songs
        }
        if (artistName != null) {
            songs = songRepository.sortedSongs().filter {
                it.artistName.lowercase().startsWith(artistName.lowercase())
            }
        }
        if (songs.isNotEmpty()) {
            return songs
        }
        if (albumName != null) {
            songs = songRepository.sortedSongs().filter {
                it.albumName.lowercase().startsWith(albumName.lowercase())
            }
        }
        if (songs.isNotEmpty()) {
            return songs
        }
        if (titleName != null) {
            songs = songRepository.sortedSongs().filter {
                it.title.lowercase().startsWith(titleName.lowercase())
            }
        }
        if (songs.isNotEmpty()) {
            return songs
        }
        songs = songRepository.sortedSongs().filter {
            it.artistName.lowercase().startsWith(query.lowercase())
        }

        if (songs.isNotEmpty()) {
            return songs
        }
        songs = songRepository.sortedSongs().filter {
            it.albumName.lowercase().startsWith(query.lowercase())
        }
        if (songs.isNotEmpty()) {
            return songs
        }
        songs = songRepository.sortedSongs().filter {
            it.title.lowercase().startsWith(query.lowercase())
        }
        return if (songs.isNotEmpty()) {
            songs
        } else ArrayList()
    }
}
