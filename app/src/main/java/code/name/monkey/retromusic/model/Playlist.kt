package code.name.monkey.retromusic.model

import android.content.Context
import android.os.Parcelable
import code.name.monkey.retromusic.repository.PlaylistRepository
import code.name.monkey.retromusic.repository.RealPlaylistRepository
import code.name.monkey.retromusic.util.MusicUtil
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject

@Parcelize
open class Playlist(
    val id: Long,
    val name: String
) : Parcelable, KoinComponent {

    companion object {
        val empty = Playlist(-1, "")
    }

    @IgnoredOnParcel
    private val playlistRepository: PlaylistRepository by inject()

    // this default implementation covers static playlists
    suspend fun getSongs(): List<Song> {
        return playlistRepository.playlistSongs(id)
    }

    open suspend fun getInfoString(context: Context): String {
        val songCount = getSongs().size
        val songCountString = MusicUtil.getSongCountString(context, songCount)
        return MusicUtil.buildInfoString(
            songCountString,
            ""
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Playlist

        if (id != other.id) return false
        if (name != other.name) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        return result
    }
}