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

import android.content.ContentResolver
import android.database.Cursor
import android.provider.BaseColumns
import android.provider.MediaStore.Audio.Genres
import code.name.monkey.retromusic.Constants.IS_MUSIC
import code.name.monkey.retromusic.Constants.baseProjection
import code.name.monkey.retromusic.extensions.getLong
import code.name.monkey.retromusic.extensions.getStringOrNull
import code.name.monkey.retromusic.model.Genre
import code.name.monkey.retromusic.model.Song
import code.name.monkey.retromusic.util.PreferenceUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface GenreRepository {
    suspend fun genres(query: String): List<Genre>

    suspend fun genres(): List<Genre>

    suspend fun songs(genreId: Long): List<Song>

    suspend fun song(genreId: Long): Song
}

class RealGenreRepository(
    private val contentResolver: ContentResolver,
    private val songRepository: RealSongRepository
) : GenreRepository {

    override suspend fun genres(query: String): List<Genre> {
        return getGenresFromCursor(makeGenreCursor(query))
    }

    override suspend fun genres(): List<Genre> {
        return getGenresFromCursor(makeGenreCursor())
    }

    override suspend fun songs(genreId: Long): List<Song> {
        // The genres table only stores songs that have a genre specified,
        // so we need to get songs without a genre a different way.
        return if (genreId == -1L) {
            getSongsWithNoGenre()
        } else songRepository.songs(makeGenreSongCursor(genreId))
    }

    override suspend fun song(genreId: Long): Song {
        return songRepository.song(makeGenreSongCursor(genreId))
    }

    private fun getSongCount(genreId: Long): Int {
        contentResolver.query(
            Genres.Members.getContentUri("external", genreId),
            null,
            null,
            null,
            null
        ).use {
            return it?.count ?: 0
        }
    }

    private fun getGenreFromCursor(cursor: Cursor): Genre {
        val id = cursor.getLong(Genres._ID)
        val name = cursor.getStringOrNull(Genres.NAME)
        val songCount = getSongCount(id)
        return Genre(id, name ?: "", songCount)
    }

    private suspend fun getSongsWithNoGenre(): List<Song> {
        val selection =
            BaseColumns._ID + " NOT IN " + "(SELECT " + Genres.Members.AUDIO_ID + " FROM audio_genres_map)"
        return songRepository.songs(songRepository.makeSongCursor(selection, null))
    }

    private suspend fun makeGenreSongCursor(genreId: Long): Cursor? =
        withContext(Dispatchers.IO) {
            try {
                contentResolver.query(
                    Genres.Members.getContentUri("external", genreId),
                    baseProjection,
                    IS_MUSIC,
                    null,
                    PreferenceUtil.songSortOrder
                )
            } catch (e: SecurityException) {
                null
            }
        }

    private fun getGenresFromCursor(cursor: Cursor?): ArrayList<Genre> {
        val genres = arrayListOf<Genre>()
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                do {
                    val genre = getGenreFromCursor(cursor)
                    if (genre.songCount > 0) {
                        genres.add(genre)
                    }
                } while (cursor.moveToNext())
            }
        }
        return genres
    }

    private suspend fun makeGenreCursor(): Cursor? {
        val projection = arrayOf(Genres._ID, Genres.NAME)
        return withContext(Dispatchers.IO) {
            try {
                contentResolver.query(
                    Genres.EXTERNAL_CONTENT_URI,
                    projection,
                    null,
                    null,
                    PreferenceUtil.genreSortOrder
                )
            } catch (e: SecurityException) {
                null
            }
        }
    }

    private suspend fun makeGenreCursor(query: String): Cursor? {
        val projection = arrayOf(Genres._ID, Genres.NAME)
        return withContext(Dispatchers.IO) {
            try {
                contentResolver.query(
                    Genres.EXTERNAL_CONTENT_URI,
                    projection,
                    Genres.NAME + " = ?",
                    arrayOf(query),
                    PreferenceUtil.genreSortOrder
                )
            } catch (e: SecurityException) {
                null
            }
        }
    }
}
