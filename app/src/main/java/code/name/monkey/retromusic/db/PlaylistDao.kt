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

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface PlaylistDao {

    companion object {
        const val FAVORITES = "favorites"
    }

    @Insert
    suspend fun createPlaylist(playlistEntity: PlaylistEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongsToPlaylist(songEntities: List<SongEntity>)

    @Delete
    suspend fun deletePlaylist(playlistEntity: PlaylistEntity)

    @Delete
    suspend fun deletePlaylists(playlistEntities: List<PlaylistEntity>)

    @Delete
    suspend fun deletePlaylistSongs(songs: List<SongEntity>)

    @Query("DELETE FROM SongEntity WHERE song_id = :songId")
    suspend fun removeSongFromAllPlaylists(songId: Long)

    @Query("DELETE FROM SongEntity WHERE playlist_id = :playlistId")
    suspend fun deletePlaylistSongs(playlistId: Long)

    @Query("DELETE FROM SongEntity WHERE playlist_id = :playlistId AND song_id = :songId")
    suspend fun deleteSongFromPlaylist(playlistId: Long, songId: Long)

    @Query("UPDATE PlaylistEntity SET playlist_name = :name WHERE playlist_id = :playlistId")
    suspend fun renamePlaylist(playlistId: Long, name: String)

    @Query("SELECT * FROM SongEntity")
    suspend fun getAllSongEntities(): List<SongEntity>

    @Query("SELECT * FROM SongEntity WHERE playlist_id = :playlistId ORDER BY song_key asc")
    suspend fun songsFromPlaylist(playlistId: Long): List<SongEntity>

    @Query("SELECT * FROM PlaylistEntity")
    suspend fun playlists(): List<PlaylistEntity>

    @Query("SELECT * FROM PlaylistEntity WHERE playlist_name = :name")
    suspend fun playlist(name: String): List<PlaylistEntity>

    @Query("SELECT * FROM PlaylistEntity WHERE playlist_tag = :tag")
    suspend fun playlistsWithTag(tag: String): List<PlaylistEntity>

    @Query("SELECT COUNT(*) FROM SongEntity WHERE playlist_id = :playlistId AND song_id = :songId LIMIT 1")
    suspend fun isSongInPlaylist(songId: Long, playlistId: Long): Int

    @Transaction
    @Query("SELECT * FROM PlaylistEntity")
    suspend fun playlistsWithSongs(): List<PlaylistWithSongs>

    @Query("SELECT * FROM SongEntity WHERE playlist_id = :playlistId ORDER BY song_key asc")
    fun songsFromPlaylistLiveData(playlistId: Long): LiveData<List<SongEntity>>

    @Query("SELECT EXISTS(SELECT * FROM PlaylistEntity WHERE playlist_id = :playlistId)")
    fun checkPlaylistExists(playlistId: Long): LiveData<Boolean>

}
