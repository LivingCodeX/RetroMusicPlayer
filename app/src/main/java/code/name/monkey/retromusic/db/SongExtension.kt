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
package code.name.monkey.retromusic.db

import code.name.monkey.retromusic.model.Song
import code.name.monkey.retromusic.util.MusicUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun Song.toSongEntity(playListId: Long): SongEntity {
    return SongEntity(
        playlistId = playListId,
        songId = id
    )
}

fun Song.toHistoryEntity(timePlayed: Long, playCount: Int): HistoryEntity {
    return HistoryEntity(
        songId = id,
        timePlayed = timePlayed,
        playCount = playCount
    )
}

suspend fun SongEntity.toSong(): Song {
    return MusicUtil.songById(songId)
}

fun List<Song>.toSongsEntity(playlistEntity: PlaylistEntity): List<SongEntity> {
    return map {
        it.toSongEntity(playlistEntity.playlistId)
    }
}

fun List<Song>.toQueueEntities(): List<QueueEntity> {
    val list = arrayListOf<QueueEntity>()
    forEachIndexed { index, song ->
        list.add(QueueEntity((index + 1).toLong(), song.id))
    }
    return list
}

fun List<Song>.toOriginalQueueEntities(): List<OriginalQueueEntity> {
    val list = arrayListOf<OriginalQueueEntity>()
    forEachIndexed { index, song ->
        list.add(OriginalQueueEntity((index + 1).toLong(), song.id))
    }
    return list
}

suspend fun List<SongEntity>.toSongs(): List<Song> {
    return map(SongEntity::songId).songIdsToSongs()
}

suspend fun List<HistoryEntity>.historyToSongs(): List<Song> {
    return map(HistoryEntity::songId).songIdsToSongs()
}

suspend fun List<QueueEntity>.queueToSongs(): List<Song> {
    return map(QueueEntity::songId).songIdsToSongs()
}

suspend fun List<OriginalQueueEntity>.originalQueueToSongs(): List<Song> {
    return map(OriginalQueueEntity::songId).songIdsToSongs()
}

suspend fun List<Long>.songIdsToSongs(): List<Song> {
    val songs = MusicUtil.songs()
    return withContext(Dispatchers.Default) {
        val list = arrayListOf<Song>()

        forEach {
            for (song in songs) {
                if (song.id == it) {
                    list.add(song)
                    break
                }
            }
        }

        list
    }
}
