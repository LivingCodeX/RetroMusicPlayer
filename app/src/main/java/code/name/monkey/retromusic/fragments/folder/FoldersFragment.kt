/*
 * Copyright (c) 2020 Hemanth Savarala.
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
package code.name.monkey.retromusic.fragments.folder

import android.app.Dialog
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Bundle
import android.os.Environment
import android.text.Html
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.loader.app.LoaderManager
import androidx.loader.content.Loader
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import code.name.monkey.appthemehelper.ThemeStore.Companion.accentColor
import code.name.monkey.appthemehelper.common.ATHToolbarActivity
import code.name.monkey.appthemehelper.util.ToolbarContentTintHelper
import code.name.monkey.retromusic.R
import code.name.monkey.retromusic.adapter.SongFileAdapter
import code.name.monkey.retromusic.adapter.Storage
import code.name.monkey.retromusic.adapter.StorageAdapter
import code.name.monkey.retromusic.adapter.StorageClickListener
import code.name.monkey.retromusic.databinding.FragmentFolderBinding
import code.name.monkey.retromusic.extensions.*
import code.name.monkey.retromusic.fragments.ReloadType
import code.name.monkey.retromusic.fragments.base.AbsMainActivityFragment
import code.name.monkey.retromusic.fragments.folder.FoldersFragment.ListPathsAsyncTask.OnPathsListedCallback
import code.name.monkey.retromusic.helper.MusicPlayerRemote.openQueue
import code.name.monkey.retromusic.helper.MusicPlayerRemote.playingQueue
import code.name.monkey.retromusic.helper.menu.SongMenuHelper.handleMenuClick
import code.name.monkey.retromusic.helper.menu.SongsMenuHelper
import code.name.monkey.retromusic.interfaces.ICabCallback
import code.name.monkey.retromusic.interfaces.ICabHolder
import code.name.monkey.retromusic.interfaces.ICallbacks
import code.name.monkey.retromusic.interfaces.IMainActivityFragmentCallbacks
import code.name.monkey.retromusic.misc.DialogAsyncTask
import code.name.monkey.retromusic.misc.UpdateToastMediaScannerCompletionListener
import code.name.monkey.retromusic.misc.WrappedAsyncTaskLoader
import code.name.monkey.retromusic.model.Song
import code.name.monkey.retromusic.repository.RoomRepository
import code.name.monkey.retromusic.util.FileUtil
import code.name.monkey.retromusic.util.FileUtil.safeCanonicalPath
import code.name.monkey.retromusic.util.PreferenceUtil.startDirectory
import code.name.monkey.retromusic.util.RetroColorUtil
import code.name.monkey.retromusic.util.ThemedFastScroller.create
import code.name.monkey.retromusic.views.BreadCrumbLayout.Crumb
import code.name.monkey.retromusic.views.BreadCrumbLayout.SelectionCallback
import com.afollestad.materialcab.attached.AttachedCab
import com.afollestad.materialcab.attached.destroy
import com.afollestad.materialcab.attached.isActive
import com.afollestad.materialcab.createCab
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.transition.MaterialFadeThrough
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import java.io.File
import java.io.FileFilter
import java.io.IOException
import java.lang.ref.WeakReference
import java.util.*

class FoldersFragment : AbsMainActivityFragment(R.layout.fragment_folder),
    IMainActivityFragmentCallbacks, ICabHolder, SelectionCallback, ICallbacks,
    LoaderManager.LoaderCallbacks<List<File>>, StorageClickListener {
    private var _binding: FragmentFolderBinding? = null
    private val binding get() = _binding!!
    private var adapter: SongFileAdapter? = null
    private var storageAdapter: StorageAdapter? = null
    private var cab: AttachedCab? = null
    private val fileComparator = Comparator { lhs: File, rhs: File ->
        if (lhs.isDirectory && !rhs.isDirectory) {
            return@Comparator -1
        } else if (!lhs.isDirectory && rhs.isDirectory) {
            return@Comparator 1
        } else {
            return@Comparator lhs.name.compareTo(rhs.name, ignoreCase = true)
        }
    }
    private var storageItems = ArrayList<Storage>()

    private val roomRepository: RoomRepository by inject()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentFolderBinding.bind(view)

        mainActivity.addMusicServiceEventListener(libraryViewModel)
        mainActivity.setSupportActionBar(binding.toolbar)
        mainActivity.supportActionBar?.title = null
        enterTransition = MaterialFadeThrough()
        reenterTransition = MaterialFadeThrough()

        setUpBreadCrumbs()
        setUpRecyclerView()
        setUpAdapter()
        setUpTitle()
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (!handleBackPress()) {
                        remove()
                        requireActivity().onBackPressed()
                    }
                }
            })
        binding.toolbarContainer.drawNextToNavbar()
        binding.appBarLayout.statusBarForeground =
            MaterialShapeDrawable.createWithElevationOverlay(requireContext())
    }

    private fun setUpTitle() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigate(R.id.action_search, null, navOptions)
        }
        binding.appNameText.text = resources.getString(R.string.folders)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        setHasOptionsMenu(true)
        if (savedInstanceState == null) {
            switchToFileAdapter()
            setCrumb(
                Crumb(
                    FileUtil.safeGetCanonicalFile(startDirectory)
                ),
                true
            )
        } else {
            binding.breadCrumbs.restoreFromStateWrapper(savedInstanceState.getParcelable(CRUMBS))
            LoaderManager.getInstance(this).initLoader(LOADER_ID, null, this)
        }
    }

    override fun onPause() {
        super.onPause()
        saveScrollPosition()
        if (cab.isActive()) {
            cab.destroy()
        }
    }

    override fun handleBackPress(): Boolean {
        if (cab != null && cab!!.isActive()) {
            cab?.destroy()
            return true
        }
        if (binding.breadCrumbs.popHistory()) {
            setCrumb(binding.breadCrumbs.lastHistory(), false)
            return true
        }
        return false
    }

    override fun onCreateLoader(id: Int, args: Bundle?): Loader<List<File>> {
        return AsyncFileLoader(this)
    }

    override fun onCrumbSelection(crumb: Crumb, index: Int) {
        setCrumb(crumb, true)
    }

    override fun onFileMenuClicked(file: File, view: View) {
        val popupMenu = PopupMenu(requireActivity(), view)
        if (file.isDirectory) {
            popupMenu.inflate(R.menu.menu_item_directory)
            popupMenu.setOnMenuItemClickListener { item: MenuItem ->
                when (val itemId = item.itemId) {
                    R.id.action_play_next, R.id.action_add_to_current_playing, R.id.action_add_to_playlist, R.id.action_delete_from_device -> {
                        listSongs(
                            null,
                            LoadingInfo(
                                toList(file), AUDIO_FILE_FILTER, fileComparator
                            ),
                            object : OnSongsListedCallback {
                                override fun onSongsListed(songs: List<Song>, extra: Any?) {
                                    if (songs.isNotEmpty()) {
                                        SongsMenuHelper.handleMenuClick(
                                            requireActivity(), songs, itemId
                                        )
                                    }
                                }
                            })
                        return@setOnMenuItemClickListener true
                    }
                    R.id.action_add_to_blacklist -> {
                        lifecycleScope.launch {
                            roomRepository.addBlacklistPath(file.safeCanonicalPath())
                            libraryViewModel.forceReload(ReloadType.Songs)
                        }
                        return@setOnMenuItemClickListener true
                    }
                    R.id.action_set_as_start_directory -> {
                        startDirectory = file
                        Toast.makeText(
                            activity,
                            String.format(getString(R.string.new_start_directory), file.path),
                            Toast.LENGTH_SHORT
                        )
                            .show()
                        return@setOnMenuItemClickListener true
                    }
                    R.id.action_scan -> {
                        ListPathsAsyncTask(
                            activity,
                            object : OnPathsListedCallback {
                                override fun onPathsListed(paths: Array<String?>) {
                                    scanPaths(paths)
                                }
                            })
                            .execute(ListPathsAsyncTask.LoadingInfo(file, AUDIO_FILE_FILTER))
                        return@setOnMenuItemClickListener true
                    }
                }
                false
            }
        } else {
            popupMenu.inflate(R.menu.menu_item_file)
            popupMenu.setOnMenuItemClickListener { item: MenuItem ->
                when (val itemId = item.itemId) {
                    R.id.action_play_next, R.id.action_add_to_current_playing, R.id.action_add_to_playlist, R.id.action_go_to_album, R.id.action_go_to_artist, R.id.action_share, R.id.action_tag_editor, R.id.action_details, R.id.action_set_as_ringtone, R.id.action_delete_from_device -> {
                        listSongs(
                            null,
                            LoadingInfo(
                                toList(file), AUDIO_FILE_FILTER, fileComparator
                            ),
                            object : OnSongsListedCallback {
                                override fun onSongsListed(songs: List<Song>, extra: Any?) {
                                    handleMenuClick(
                                        requireActivity(), songs[0], itemId
                                    )
                                }
                            })
                        return@setOnMenuItemClickListener true
                    }
                    R.id.action_scan -> {
                        ListPathsAsyncTask(
                            activity,
                            object : OnPathsListedCallback {
                                override fun onPathsListed(paths: Array<String?>) {
                                    scanPaths(paths)
                                }
                            })
                            .execute(ListPathsAsyncTask.LoadingInfo(file, AUDIO_FILE_FILTER))
                        return@setOnMenuItemClickListener true
                    }
                }
                false
            }
        }
        popupMenu.show()
    }

    override fun onFileSelected(file: File) {
        var mFile = file
        mFile = tryGetCanonicalFile(mFile) // important as we compare the path value later
        if (mFile.isDirectory) {
            setCrumb(Crumb(mFile), true)
        } else {
            val fileFilter = FileFilter { pathname: File ->
                !pathname.isDirectory && AUDIO_FILE_FILTER.accept(pathname)
            }
            listSongs(
                mFile,
                LoadingInfo(
                    toList(mFile.parentFile), fileFilter, fileComparator
                ),
                object : OnSongsListedCallback {
                    override fun onSongsListed(songs: List<Song>, extra: Any?) {
                        val file1 = extra as File
                        var startIndex = -1
                        for (i in songs.indices) {
                            if (file1
                                    .path
                                == songs[i].data
                            ) { // path is already canonical here
                                startIndex = i
                                break
                            }
                        }
                        if (startIndex > -1) {
                            openQueue(songs, startIndex, true)
                        } else {
                            Snackbar.make(
                                mainActivity.slidingPanel,
                                Html.fromHtml(
                                    String.format(
                                        getString(R.string.not_listed_in_media_store), file1.name
                                    )
                                ),
                                Snackbar.LENGTH_LONG
                            )
                                .setAction(
                                    R.string.action_scan
                                ) {
                                    ListPathsAsyncTask(
                                        requireActivity(),
                                        object : OnPathsListedCallback {
                                            override fun onPathsListed(paths: Array<String?>) {
                                                scanPaths(paths)
                                            }
                                        })
                                        .execute(
                                            ListPathsAsyncTask.LoadingInfo(
                                                file1, AUDIO_FILE_FILTER
                                            )
                                        )
                                }
                                .setActionTextColor(accentColor(requireActivity()))
                                .show()
                        }
                    }
                })
        }
    }

    override fun onLoadFinished(loader: Loader<List<File>>, data: List<File>) {
        updateAdapter(data)
    }

    override fun onLoaderReset(loader: Loader<List<File>>) {
        updateAdapter(LinkedList())
    }

    override fun onMultipleItemAction(item: MenuItem, files: ArrayList<File>) {
        val itemId = item.itemId
        listSongs(
            null,
            LoadingInfo(files, AUDIO_FILE_FILTER, fileComparator),
            object : OnSongsListedCallback {
                override fun onSongsListed(songs: List<Song>, extra: Any?) {
                    SongsMenuHelper.handleMenuClick(
                        requireActivity(),
                        songs,
                        itemId
                    )
                }
            })
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        ToolbarContentTintHelper.handleOnPrepareOptionsMenu(requireActivity(), binding.toolbar)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        menu.add(0, R.id.action_scan, 0, R.string.scan_media)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu.add(0, R.id.action_go_to_start_directory, 1, R.string.action_go_to_start_directory)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu.add(0, R.id.action_settings, 2, R.string.action_settings)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu.removeItem(R.id.action_grid_size)
        menu.removeItem(R.id.action_layout_type)
        menu.removeItem(R.id.action_sort_order)
        ToolbarContentTintHelper.handleOnCreateOptionsMenu(
            requireContext(), binding.toolbar, menu, ATHToolbarActivity.getToolbarBackgroundColor(
                binding.toolbar
            )
        )
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_go_to_start_directory -> {
                setCrumb(
                    Crumb(
                        tryGetCanonicalFile(startDirectory)
                    ),
                    true
                )
                return true
            }
            R.id.action_scan -> {
                val crumb = activeCrumb
                if (crumb != null) {
                    ListPathsAsyncTask(
                        activity,
                        object : OnPathsListedCallback {
                            override fun onPathsListed(paths: Array<String?>) {
                                scanPaths(paths)
                            }
                        })
                        .execute(ListPathsAsyncTask.LoadingInfo(crumb.file, AUDIO_FILE_FILTER))
                }
                return true
            }
            R.id.action_settings -> {
                findNavController().navigate(
                    R.id.settingsActivity,
                    null,
                    navOptions
                )
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onQueueChanged() {
        super.onQueueChanged()
        checkForPadding()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        checkForPadding()
    }

    override fun openCab(menuRes: Int, callback: ICabCallback): AttachedCab {
        if (cab != null && cab!!.isActive()) {
            cab?.destroy()
        }
        cab = createCab(R.id.toolbar_container) {
            menu(menuRes)
            closeDrawable(R.drawable.ic_close)
            backgroundColor(literal = RetroColorUtil.shiftBackgroundColor(surfaceColor()))
            slideDown()
            onCreate { cab, menu -> callback.onCabCreated(cab, menu) }
            onSelection {
                callback.onCabItemClicked(it)
            }
            onDestroy { callback.onCabFinished(it) }
        }
        return cab as AttachedCab
    }

    private fun checkForPadding() {
        val count = adapter?.itemCount ?: 0
        if (_binding != null) {
            binding.recyclerView.updatePadding(
                bottom = if (count > 0 && playingQueue.isNotEmpty()) dip(R.dimen.mini_player_height_expanded)
                else dip(R.dimen.mini_player_height_expanded)
            )
        }
    }

    private fun checkIsEmpty() {
        if (_binding != null) {
            binding.emptyEmoji.text = getEmojiByUnicode(0x1F631)
            binding.empty.isVisible = adapter?.itemCount == 0
        }
    }

    private val activeCrumb: Crumb?
        get() = if (_binding != null) {
            if (binding.breadCrumbs.size() > 0) binding.breadCrumbs.getCrumb(binding.breadCrumbs.activeIndex) else null
        } else null

    private fun getEmojiByUnicode(unicode: Int): String {
        return String(Character.toChars(unicode))
    }

    private fun saveScrollPosition() {
        val crumb = activeCrumb
        if (crumb != null) {
            crumb.scrollPosition =
                (binding.recyclerView.layoutManager as LinearLayoutManager?)!!.findFirstVisibleItemPosition()
        }
    }

    private fun scanPaths(toBeScanned: Array<String?>) {
        if (activity == null) {
            return
        }
        if (toBeScanned.isEmpty()) {
            Toast.makeText(activity, R.string.nothing_to_scan, Toast.LENGTH_SHORT).show()
        } else {
            MediaScannerConnection.scanFile(
                requireContext(),
                toBeScanned,
                null,
                UpdateToastMediaScannerCompletionListener(activity, listOf(*toBeScanned))
            )
        }
    }

    private fun setCrumb(crumb: Crumb?, addToHistory: Boolean) {
        if (crumb == null) {
            return
        }
        val path = crumb.file.path
        if (path == "/" || path == "/storage" || path == "/storage/emulated") {
            switchToStorageAdapter()
        } else {
            saveScrollPosition()
            binding.breadCrumbs.setActiveOrAdd(crumb, false)
            if (addToHistory) {
                binding.breadCrumbs.addHistory(crumb)
            }
            LoaderManager.getInstance(this).restartLoader(LOADER_ID, null, this)
        }
    }

    private fun setUpAdapter() {
        switchToFileAdapter()
    }

    private fun setUpBreadCrumbs() {
        binding.breadCrumbs.setActivatedContentColor(
            textColorPrimary()
        )
        binding.breadCrumbs.setDeactivatedContentColor(
            textColorSecondary()

        )
        binding.breadCrumbs.setCallback(this)
    }

    private fun setUpRecyclerView() {
        binding.recyclerView.layoutManager = LinearLayoutManager(activity)
        create(
            binding.recyclerView
        )
    }

    private fun toList(file: File): ArrayList<File> {
        val files = ArrayList<File>(1)
        files.add(file)
        return files
    }

    private fun updateAdapter(files: List<File>) {
        adapter?.swapDataSet(files)
        val crumb = activeCrumb
        if (crumb != null) {
            (binding.recyclerView.layoutManager as LinearLayoutManager?)
                ?.scrollToPositionWithOffset(crumb.scrollPosition, 0)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    class ListPathsAsyncTask(context: Context?, callback: OnPathsListedCallback) :
        ListingFilesDialogAsyncTask<ListPathsAsyncTask.LoadingInfo, String?, Array<String?>>(
            context
        ) {
        private val onPathsListedCallbackWeakReference: WeakReference<OnPathsListedCallback> =
            WeakReference(callback)

        override fun doInBackground(vararg params: LoadingInfo): Array<String?> {
            return try {
                if (isCancelled || checkCallbackReference() == null) {
                    return arrayOf()
                }
                val info = params[0]
                val paths: Array<String?>
                if (info.file.isDirectory) {
                    val files = FileUtil.listFilesDeep(info.file, info.fileFilter)
                    if (isCancelled || checkCallbackReference() == null) {
                        return arrayOf()
                    }
                    paths = arrayOfNulls(files.size)
                    for (i in files.indices) {
                        val f = files[i]
                        paths[i] = FileUtil.safeGetCanonicalPath(f)
                        if (isCancelled || checkCallbackReference() == null) {
                            return arrayOf()
                        }
                    }
                } else {
                    paths = arrayOfNulls(1)
                    paths[0] = info.file.path
                }
                paths
            } catch (e: Exception) {
                e.printStackTrace()
                cancel(false)
                arrayOf()
            }
        }

        override fun onPostExecute(paths: Array<String?>) {
            super.onPostExecute(paths)
            checkCallbackReference()?.onPathsListed(paths)
        }

        override fun onPreExecute() {
            super.onPreExecute()
            checkCallbackReference()
        }

        private fun checkCallbackReference(): OnPathsListedCallback? {
            val callback = onPathsListedCallbackWeakReference.get()
            if (callback == null) {
                cancel(false)
            }
            return callback
        }

        interface OnPathsListedCallback {
            fun onPathsListed(paths: Array<String?>)
        }

        class LoadingInfo(val file: File, val fileFilter: FileFilter)

    }

    private class AsyncFileLoader(foldersFragment: FoldersFragment) :
        WrappedAsyncTaskLoader<List<File>>(foldersFragment.requireActivity()) {
        private val fragmentWeakReference: WeakReference<FoldersFragment> =
            WeakReference(foldersFragment)

        override fun loadInBackground(): List<File> {
            val foldersFragment = fragmentWeakReference.get()
            var directory: File? = null
            if (foldersFragment != null) {
                val crumb = foldersFragment.activeCrumb
                if (crumb != null) {
                    directory = crumb.file
                }
            }
            return if (directory != null) {
                val files = FileUtil.listFiles(
                    directory,
                    AUDIO_FILE_FILTER
                )
                Collections.sort(files, foldersFragment!!.fileComparator)
                files
            } else {
                LinkedList()
            }
        }

    }

    private fun listSongs(extra: Any?, info: LoadingInfo, callback: OnSongsListedCallback) = lifecycleScope.launch(Dispatchers.IO) {
        val songs = try {
            val files = FileUtil.listFilesDeep(info.files, info.fileFilter)
            Collections.sort(files, info.fileComparator)
            FileUtil.matchFilesWithMediaStore(files)
        } catch (e: Exception) {
            e.printStackTrace()
            return@launch
        }

        callback.onSongsListed(songs, extra)
    }

    private class LoadingInfo(
        val files: List<File>,
        val fileFilter: FileFilter,
        val fileComparator: Comparator<File>
    )

    private interface OnSongsListedCallback {
        fun onSongsListed(songs: List<Song>, extra: Any?)
    }

    abstract class ListingFilesDialogAsyncTask<Params, Progress, Result> internal constructor(
        context: Context?
    ) :
        DialogAsyncTask<Params, Progress, Result>(context) {

        override fun createDialog(context: Context): Dialog {
            return MaterialAlertDialogBuilder(context)
                .setTitle(R.string.listing_files)
                .setCancelable(false)
                .setView(R.layout.loading)
                .setOnCancelListener { cancel(false) }
                .setOnDismissListener { cancel(false) }
                .create()
        }
    }

    override fun onStorageClicked(storage: Storage) {
        switchToFileAdapter()
        setCrumb(
            Crumb(
                FileUtil.safeGetCanonicalFile(storage.file)
            ),
            true
        )
    }

    private fun switchToFileAdapter() {
        adapter = SongFileAdapter(mainActivity, LinkedList(), R.layout.item_list, this, this)
        adapter!!.registerAdapterDataObserver(
            object : RecyclerView.AdapterDataObserver() {
                override fun onChanged() {
                    super.onChanged()
                    checkIsEmpty()
                    checkForPadding()
                }
            })
        binding.recyclerView.adapter = adapter
        checkIsEmpty()
    }

    private fun switchToStorageAdapter() {
        storageItems = FileUtil.listRoots()
        storageAdapter = StorageAdapter(storageItems, this)
        binding.recyclerView.adapter = storageAdapter
        binding.breadCrumbs.clearCrumbs()
    }

    companion object {
        val TAG: String = FoldersFragment::class.java.simpleName
        val AUDIO_FILE_FILTER = FileFilter { file: File ->
            (!file.isHidden
                    && (file.isDirectory
                    || FileUtil.fileIsMimeType(file, "audio/*", MimeTypeMap.getSingleton())
                    || FileUtil.fileIsMimeType(file, "application/opus", MimeTypeMap.getSingleton())
                    || FileUtil.fileIsMimeType(
                file,
                "application/ogg",
                MimeTypeMap.getSingleton()
            )))
        }
        private const val CRUMBS = "crumbs"
        private const val LOADER_ID = 5

        // root
        val defaultStartDirectory: File
            get() {
                val musicDir =
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
                val startFolder = if (musicDir.exists() && musicDir.isDirectory) {
                    musicDir
                } else {
                    val externalStorage = Environment.getExternalStorageDirectory()
                    if (externalStorage.exists() && externalStorage.isDirectory) {
                        externalStorage
                    } else {
                        File("/") // root
                    }
                }
                return startFolder
            }

        private fun tryGetCanonicalFile(file: File): File {
            return try {
                file.canonicalFile
            } catch (e: IOException) {
                e.printStackTrace()
                file
            }
        }
    }
}