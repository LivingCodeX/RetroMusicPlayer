package code.name.monkey.retromusic

import androidx.room.Room
import code.name.monkey.retromusic.auto.AutoMusicProvider
import code.name.monkey.retromusic.db.PlaylistWithSongs
import code.name.monkey.retromusic.db.RetroDatabase
import code.name.monkey.retromusic.fragments.LibraryViewModel
import code.name.monkey.retromusic.fragments.albums.AlbumDetailsViewModel
import code.name.monkey.retromusic.fragments.artists.ArtistDetailsViewModel
import code.name.monkey.retromusic.fragments.genres.GenreDetailsViewModel
import code.name.monkey.retromusic.fragments.playlists.PlaylistDetailsViewModel
import code.name.monkey.retromusic.model.Genre
import code.name.monkey.retromusic.network.provideDefaultCache
import code.name.monkey.retromusic.network.provideLastFmRest
import code.name.monkey.retromusic.network.provideLastFmRetrofit
import code.name.monkey.retromusic.network.provideOkHttp
import code.name.monkey.retromusic.repository.*
import kotlinx.coroutines.*
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

val networkModule = module {

    factory {
        provideDefaultCache()
    }
    factory {
        provideOkHttp(get(), get())
    }
    single {
        provideLastFmRetrofit(get())
    }
    single {
        provideLastFmRest(get())
    }
}

private val roomModule = module {

    single {
        Room.databaseBuilder(androidContext(), RetroDatabase::class.java, "retro.db")
            .fallbackToDestructiveMigration()
            .build()
    }

    factory {
        get<RetroDatabase>().playlistDao()
    }

    factory {
        get<RetroDatabase>().historyDao()
    }

    factory {
        get<RetroDatabase>().blacklistDao()
    }

    factory {
        get<RetroDatabase>().whitelistDao()
    }

    factory {
        get<RetroDatabase>().queueDao()
    }

    factory {
        get<RetroDatabase>().originalQueueDao()
    }

    single {
        RealRoomRepository(get(), get(), Dispatchers.IO, Dispatchers.Default, get(), get(), get(), get(), get(), get())
    } bind RoomRepository::class
}
private val autoModule = module {
    single {
        AutoMusicProvider(androidContext(),
        get(),
        get(),
        get(),
        get(),
        get(),
        get()
        )
    }
}
private val mainModule = module {
    single {
        androidContext().contentResolver
    }

    single {
        SupervisorJob()
    }

    single {
        CoroutineScope(get<CompletableJob>() + Dispatchers.Default)
    }
}
private val dataModule = module {
    single {
        RealRepository(
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
        )
    } bind Repository::class

    single {
        RealSongRepository(get(), Dispatchers.IO, get())
    } bind SongRepository::class

    single {
        RealGenreRepository(get(), get())
    } bind GenreRepository::class

    single {
        RealAlbumRepository(get())
    } bind AlbumRepository::class

    single {
        RealArtistRepository(get(), get())
    } bind ArtistRepository::class

    single {
        RealPlaylistRepository(get(), Dispatchers.IO)
    } bind PlaylistRepository::class

    single {
        RealTopPlayedRepository(get(), get(), get(), get())
    } bind TopPlayedRepository::class

    single {
        RealLastAddedRepository(
            get(),
            get(),
            get()
        )
    } bind LastAddedRepository::class

    single {
        RealSearchRepository(
            get(),
            get(),
            get(),
            get(),
            get()
        )
    }
    single {
        RealLocalDataRepository(get())
    } bind LocalDataRepository::class
}

private val viewModules = module {

    viewModel {
        LibraryViewModel(get())
    }

    viewModel { (albumId: Long) ->
        AlbumDetailsViewModel(
            get(),
            albumId
        )
    }

    viewModel { (artistId: Long?, artistName: String?) ->
        ArtistDetailsViewModel(
            get(),
            artistId,
            artistName
        )
    }

    viewModel { (playlist: PlaylistWithSongs) ->
        PlaylistDetailsViewModel(
            get(),
            playlist
        )
    }

    viewModel { (genre: Genre) ->
        GenreDetailsViewModel(
            get(),
            genre
        )
    }
}

val appModules = listOf(mainModule, dataModule, autoModule, viewModules, networkModule, roomModule)