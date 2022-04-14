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

package code.name.monkey.retromusic.preferences

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.util.AttributeSet
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.BlendModeColorFilterCompat
import androidx.core.graphics.BlendModeCompat.SRC_IN
import androidx.core.text.parseAsHtml
import androidx.fragment.app.DialogFragment
import code.name.monkey.appthemehelper.common.prefs.supportv7.ATEDialogPreference
import code.name.monkey.retromusic.App
import code.name.monkey.retromusic.R
import code.name.monkey.retromusic.dialogs.BlacklistFolderChooserDialog
import code.name.monkey.retromusic.extensions.accentTextColor
import code.name.monkey.retromusic.extensions.colorButtons
import code.name.monkey.retromusic.extensions.colorControlNormal
import code.name.monkey.retromusic.extensions.materialDialog
import code.name.monkey.retromusic.providers.WhitelistStore
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File

class WhitelistPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = -1,
    defStyleRes: Int = -1
) : ATEDialogPreference(context, attrs, defStyleAttr, defStyleRes) {

    init {
        icon?.colorFilter =
            BlendModeColorFilterCompat.createBlendModeColorFilterCompat(
                context.colorControlNormal(),
                SRC_IN
            )
    }
}

class WhitelistPreferenceDialog : DialogFragment(), BlacklistFolderChooserDialog.FolderCallback {
    companion object {
        fun newInstance(): WhitelistPreferenceDialog {
            return WhitelistPreferenceDialog()
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val chooserDialog =
            childFragmentManager.findFragmentByTag("FOLDER_CHOOSER") as BlacklistFolderChooserDialog?
        chooserDialog?.setCallback(this)
        refreshWhitelistData()
        return materialDialog(R.string.whitelist)
            .setPositiveButton(R.string.done) { _, _ ->
                dismiss()
            }
            .setNeutralButton(R.string.clear_action) { _, _ ->
                materialDialog(R.string.clear_whitelist)
                    .setMessage(R.string.do_you_want_to_clear_the_whitelist)
                    .setPositiveButton(R.string.clear_action, null)
                    .setNegativeButton(android.R.string.cancel, null)
                    .create()
                    .colorButtons()
                    .show()
            }
            .setNegativeButton(R.string.add_action) { _, _ ->
                val dialog = BlacklistFolderChooserDialog.create()
                dialog.setCallback(this@WhitelistPreferenceDialog)
                dialog.show(requireActivity().supportFragmentManager, "FOLDER_CHOOSER")
            }
            .setItems(paths.toTypedArray()) { _, which ->
                materialDialog(R.string.remove_from_whitelist)
                    .setMessage(
                        String.format(
                            getString(R.string.do_you_want_to_remove_from_the_whitelist),
                            paths[which]
                        ).parseAsHtml()
                    )
                    .setPositiveButton(R.string.remove_action) { _, _ ->
                        WhitelistStore.getInstance(App.getContext())
                            .removePath(File(paths[which]))
                        refreshWhitelistData()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .create()
                    .colorButtons()
                    .show()
            }
            .create().apply {
                setOnShowListener {
                    getButton(AlertDialog.BUTTON_POSITIVE).accentTextColor()
                    getButton(AlertDialog.BUTTON_NEGATIVE).accentTextColor()
                    getButton(AlertDialog.BUTTON_NEUTRAL).apply {
                        accentTextColor()
                        setOnClickListener {
                            WhitelistStore.getInstance(
                                requireContext()
                            ).clear()
                            dismiss()
                        }
                    }
                }
            }
    }

    private lateinit var paths: ArrayList<String>

    private fun refreshWhitelistData() {
        this.paths = WhitelistStore.getInstance(App.getContext()).paths
        val dialog = dialog as MaterialAlertDialogBuilder?
        dialog?.setItems(paths.toTypedArray(), null)
    }

    override fun onFolderSelection(dialog: BlacklistFolderChooserDialog, folder: File) {
        WhitelistStore.getInstance(App.getContext()).addPath(folder)
        refreshWhitelistData()
    }
}
