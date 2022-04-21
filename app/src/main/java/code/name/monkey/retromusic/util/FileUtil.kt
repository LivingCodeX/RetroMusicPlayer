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
package code.name.monkey.retromusic.util

import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import code.name.monkey.retromusic.adapter.Storage
import code.name.monkey.retromusic.model.Song
import code.name.monkey.retromusic.repository.RealSongRepository
import code.name.monkey.retromusic.repository.SortedCursor
import code.name.monkey.retromusic.util.PreferenceUtil.songSortOrder
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.*
import java.util.*
import kotlin.collections.ArrayList

object FileUtil : KoinComponent {

    private val songRepository: RealSongRepository by inject()

    @JvmStatic
    @Throws(IOException::class)
    fun readBytes(stream: InputStream): ByteArray {
        val baos = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        var count: Int
        while (stream.read(buffer).also { count = it } != -1) {
            baos.write(buffer, 0, count)
        }
        stream.close()
        return baos.toByteArray()
    }

    suspend fun matchFilesWithMediaStore(files: List<File>?): List<Song> {
        return songRepository.songs(makeSongCursor(files))
    }

    @JvmStatic
    fun safeGetCanonicalPath(file: File): String {
        return try {
            file.canonicalPath
        } catch (e: IOException) {
            e.printStackTrace()
            file.absolutePath
        }
    }

    fun File.safeCanonicalPath(): String {
        return try {
            canonicalPath
        } catch (e: IOException) {
            e.printStackTrace()
            absolutePath
        }
    }

    private suspend fun makeSongCursor(
        files: List<File>?
    ): SortedCursor? {
        var selection: String? = null
        var paths: Array<String>? = null
        if (files != null) {
            paths = toPathArray(files)
            if (files.size in 1..998
            ) { // 999 is the max amount Androids SQL implementation can handle.
                selection =
                    MediaStore.Audio.AudioColumns.DATA + " IN (" + makePlaceholders(files.size) + ")"
            }
        }
        val songCursor = songRepository.makeSongCursor(
            selection,
            if (selection == null) null else paths,
            songSortOrder,
            true
        )
        return if (songCursor == null) null else SortedCursor(
            songCursor,
            paths,
            MediaStore.Audio.AudioColumns.DATA
        )
    }

    private fun makePlaceholders(len: Int): String {
        val sb = StringBuilder(len * 2 - 1)
        sb.append("?")
        for (i in 1 until len) {
            sb.append(",?")
        }
        return sb.toString()
    }

    private fun toPathArray(files: List<File>?): Array<String>? {
        if (files != null) {
            val paths = ArrayList<String>(files.size)
            files.forEach {
                /*try {
            paths[i] = files.get(i).getCanonicalPath(); // canonical path is important here because we want to compare the path with the media store entry later
        } catch (IOException e) {
            e.printStackTrace();
            paths[i] = files.get(i).getPath();
        }*/
                paths.add(safeGetCanonicalPath(it))
            }
            return paths.toTypedArray()
        }
        return null
    }

    fun listFiles(directory: File, fileFilter: FileFilter?): List<File> {
        val fileList = arrayListOf<File>()
        val found = directory.listFiles(fileFilter)
        if (found != null) {
            fileList.addAll(found)
        }
        return fileList
    }

    fun listFilesDeep(directory: File, fileFilter: FileFilter?): List<File> {
        val files: MutableList<File> = LinkedList()
        internalListFilesDeep(files, directory, fileFilter)
        return files
    }

    fun listFilesDeep(
        files: Collection<File>, fileFilter: FileFilter?
    ): List<File> {
        val resFiles: MutableList<File> = LinkedList()
        for (file in files) {
            if (file.isDirectory) {
                internalListFilesDeep(resFiles, file, fileFilter)
            } else if (fileFilter == null || fileFilter.accept(file)) {
                resFiles.add(file)
            }
        }
        return resFiles
    }

    private fun internalListFilesDeep(
        files: MutableCollection<File>, directory: File, fileFilter: FileFilter?
    ) {
        val found = directory.listFiles(fileFilter)
        if (found != null) {
            for (file in found) {
                if (file.isDirectory) {
                    internalListFilesDeep(files, file, fileFilter)
                } else {
                    files.add(file)
                }
            }
        }
    }

    fun fileIsMimeType(file: File, mimeType: String?, mimeTypeMap: MimeTypeMap): Boolean {
        return if (mimeType == null || mimeType == "*/*") {
            true
        } else {
            // get the file mime type
            val filename = file.toURI().toString()
            val dotPos = filename.lastIndexOf('.')
            if (dotPos == -1) {
                return false
            }
            val fileExtension = filename.substring(dotPos + 1).toLowerCase()
            val fileType = mimeTypeMap.getMimeTypeFromExtension(fileExtension) ?: return false
            // check the 'type/subtype' pattern
            if (fileType == mimeType) {
                return true
            }
            // check the 'type/*' pattern
            val mimeTypeDelimiter = mimeType.lastIndexOf('/')
            if (mimeTypeDelimiter == -1) {
                return false
            }
            val mimeTypeMainType = mimeType.substring(0, mimeTypeDelimiter)
            val mimeTypeSubtype = mimeType.substring(mimeTypeDelimiter + 1)
            if (mimeTypeSubtype != "*") {
                return false
            }
            val fileTypeDelimiter = fileType.lastIndexOf('/')
            if (fileTypeDelimiter == -1) {
                return false
            }
            val fileTypeMainType = fileType.substring(0, fileTypeDelimiter)
            if (fileTypeMainType == mimeTypeMainType) {
                true
            } else fileTypeMainType == mimeTypeMainType
        }
    }

    fun stripExtension(str: String?): String? {
        if (str == null) {
            return null
        }
        val pos = str.lastIndexOf('.')
        return if (pos == -1) {
            str
        } else str.substring(0, pos)
    }

    @Throws(Exception::class)
    fun readFromStream(`is`: InputStream?): String {
        val reader = BufferedReader(InputStreamReader(`is`))
        val sb = StringBuilder()
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            if (sb.length > 0) {
                sb.append("\n")
            }
            sb.append(line)
        }
        reader.close()
        return sb.toString()
    }

    @Throws(Exception::class)
    fun read(file: File?): String {
        val fin = FileInputStream(file)
        val ret = readFromStream(fin)
        fin.close()
        return ret
    }

    // yes SD-card is present
    // Sorry
    val isExternalMemoryAvailable: Boolean
        get() {
            val isSDPresent = Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
            val isSDSupportedDevice = Environment.isExternalStorageRemovable()

            // yes SD-card is present
            // Sorry
            return isSDSupportedDevice && isSDPresent
        }

    fun safeGetCanonicalFile(file: File): File {
        return try {
            file.canonicalFile
        } catch (e: IOException) {
            e.printStackTrace()
            file.absoluteFile
        }
    }

    // https://github.com/DrKLO/Telegram/blob/ab221dafadbc17459d78d9ea3e643ae18e934b16/TMessagesProj/src/main/java/org/telegram/ui/Components/ChatAttachAlertDocumentLayout.java#L939
    fun listRoots(): ArrayList<Storage> {
        val storageItems = ArrayList<Storage>()
        val paths = HashSet<String>()
        val defaultPath = Environment.getExternalStorageDirectory().path
        val defaultPathState = Environment.getExternalStorageState()
        if (defaultPathState == Environment.MEDIA_MOUNTED || defaultPathState == Environment.MEDIA_MOUNTED_READ_ONLY) {
            val ext = Storage()
            if (Environment.isExternalStorageRemovable()) {
                ext.title = "SD Card"
            } else {
                ext.title = "Internal Storage"
            }
            ext.file = Environment.getExternalStorageDirectory()
            storageItems.add(ext)
            paths.add(defaultPath)
        }
        var bufferedReader: BufferedReader? = null
        try {
            bufferedReader = BufferedReader(FileReader("/proc/mounts"))
            var line: String
            while (bufferedReader.readLine().also { line = it } != null) {
                if (line.contains("vfat") || line.contains("/mnt")) {
                    val tokens = StringTokenizer(line, " ")
                    tokens.nextToken()
                    var path = tokens.nextToken()
                    if (paths.contains(path)) {
                        continue
                    }
                    if (line.contains("/dev/block/vold")) {
                        if (!line.contains("/mnt/secure") && !line.contains("/mnt/asec") && !line.contains(
                                "/mnt/obb"
                            ) && !line.contains("/dev/mapper") && !line.contains("tmpfs")
                        ) {
                            if (!File(path).isDirectory) {
                                val index = path.lastIndexOf('/')
                                if (index != -1) {
                                    val newPath = "/storage/" + path.substring(index + 1)
                                    if (File(newPath).isDirectory) {
                                        path = newPath
                                    }
                                }
                            }
                            paths.add(path)
                            try {
                                val item = Storage()
                                if (path.toLowerCase().contains("sd")) {
                                    item.title = "SD Card"
                                } else {
                                    item.title = "External Storage"
                                }
                                item.file = File(path)
                                storageItems.add(item)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            if (bufferedReader != null) {
                try {
                    bufferedReader.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        return storageItems
    }
}