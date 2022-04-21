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

import code.name.monkey.retromusic.db.HistoryDao
import code.name.monkey.retromusic.db.historyToSongs
import code.name.monkey.retromusic.model.Album
import code.name.monkey.retromusic.model.Artist
import code.name.monkey.retromusic.model.Song
import kotlin.math.min


/**
 * Created by hemanths on 16/08/17.
 */

interface TopPlayedRepository {
    suspend fun recentlyPlayedTracks(): List<Song>

    suspend fun topTracks(): List<Song>

    suspend fun notRecentlyPlayedTracks(): List<Song>

    suspend fun topAlbums(): List<Album>

    suspend fun topArtists(): List<Artist>
}

class RealTopPlayedRepository(
    private val songRepository: RealSongRepository,
    private val albumRepository: RealAlbumRepository,
    private val artistRepository: RealArtistRepository,
    private val roomRepository: RealRoomRepository
) : TopPlayedRepository {

    override suspend fun recentlyPlayedTracks(): List<Song> {
        return roomRepository.historySongs().historyToSongs()
    }

    override suspend fun topTracks(): List<Song> {
        return roomRepository.playCountSongs().historyToSongs()
    }

    override suspend fun notRecentlyPlayedTracks(): List<Song> {
        val historyLimit = HistoryDao.HISTORY_LIMIT

        val allSongs = songRepository.songs()
        val recentSongs = recentlyPlayedTracks()

        val forgottenSongs = allSongs.subtract(recentSongs)
        val forgottenAmount = forgottenSongs.size

        return if (forgottenAmount >= historyLimit) {
            //Many songs haven't been heard in a while, return some of them
            forgottenSongs.shuffled().take(historyLimit)
        } else {
            //Smaller library, cannot purely return old songs
            val notRecentSongs = ArrayList(forgottenSongs)
            val recentsTakeAmount = min(historyLimit - forgottenAmount, recentSongs.size / 2)
            notRecentSongs.addAll(recentSongs.takeLast(recentsTakeAmount))
            notRecentSongs.shuffled()
        }
    }

    override suspend fun topAlbums(): List<Album> {
        return albumRepository.splitIntoAlbums(topTracks())
    }

    override suspend fun topArtists(): List<Artist> {
        return artistRepository.splitIntoArtists(topAlbums())
    }

}
