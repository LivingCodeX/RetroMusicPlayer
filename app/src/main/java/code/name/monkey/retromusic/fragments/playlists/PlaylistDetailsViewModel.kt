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
package code.name.monkey.retromusic.fragments.playlists

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import code.name.monkey.retromusic.db.PlaylistWithSongs
import code.name.monkey.retromusic.model.Song
import code.name.monkey.retromusic.repository.RealRepository

class PlaylistDetailsViewModel(
    private val realRepository: RealRepository,
    private var playlist: PlaylistWithSongs
) : ViewModel() {
    fun getSongs(owner: LifecycleOwner): LiveData<List<Song>?> =
        realRepository.playlistSongsLiveData(playlist.playlistEntity.playlistId, owner)

    fun playlistExists(): LiveData<Boolean> =
        realRepository.checkPlaylistExists(playlist.playlistEntity.playlistId)
}
