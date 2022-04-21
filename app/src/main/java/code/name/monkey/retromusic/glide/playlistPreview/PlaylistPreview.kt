package code.name.monkey.retromusic.glide.playlistPreview

import code.name.monkey.retromusic.db.PlaylistEntity
import code.name.monkey.retromusic.db.PlaylistWithSongs
import code.name.monkey.retromusic.db.toSongs
import code.name.monkey.retromusic.model.Song

class PlaylistPreview(val playlistWithSongs: PlaylistWithSongs) {

    val playlistEntity: PlaylistEntity get() = playlistWithSongs.playlistEntity
    suspend fun songs(): List<Song> = playlistWithSongs.songs.toSongs()

    override fun equals(other: Any?): Boolean {
        if (other is PlaylistPreview) {
            if (other.playlistEntity.playlistId != playlistEntity.playlistId) {
                return false
            }
            if (other.playlistWithSongs.songs.size != playlistWithSongs.songs.size) {
                return false
            }
            return true
        }
        return false
    }

    override fun hashCode(): Int {
        var result = playlistEntity.playlistId.hashCode()
        result = 31 * result + playlistWithSongs.songs.size
        return result
    }
}