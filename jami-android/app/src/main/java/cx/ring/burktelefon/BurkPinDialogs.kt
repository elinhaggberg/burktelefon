/*
 *  Copyright (C) 2004-2026 Savoir-faire Linux Inc.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package cx.ring.burktelefon

import android.app.Activity
import android.app.AlertDialog
import android.view.View
import android.widget.Toast
import cx.ring.R
import cx.ring.databinding.DialogBurkPinEntryBinding

/** Small 4-digit PIN entry dialogs shared between the parent settings screen
 *  (setting/changing the PIN) and the kid-facing "leave kiosk mode" flow
 *  (verifying it). */
object BurkPinDialogs {

    fun promptEnterPin(activity: Activity, message: String, onSubmit: (String) -> Unit) {
        val dialogBinding = DialogBurkPinEntryBinding.inflate(activity.layoutInflater)
        dialogBinding.burkPinMessage.text = message
        val dialog = AlertDialog.Builder(activity)
            .setView(dialogBinding.root)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val pin = dialogBinding.burkPinInput.text?.toString().orEmpty()
                if (pin.length != 4) {
                    dialogBinding.burkPinError.text = activity.getString(R.string.burk_pin_too_short)
                    dialogBinding.burkPinError.visibility = View.VISIBLE
                } else {
                    dialog.dismiss()
                    onSubmit(pin)
                }
            }
        }
        dialog.show()
    }

    /** Two-step "enter new PIN" then "confirm it"; calls [onDone] only once both match and are saved. */
    fun promptSetNewPin(activity: Activity, prefs: BurkPrefs, onDone: () -> Unit) {
        promptEnterPin(activity, activity.getString(R.string.burk_pin_enter_new)) { first ->
            promptEnterPin(activity, activity.getString(R.string.burk_pin_confirm_new)) { second ->
                if (first == second) {
                    prefs.setPin(first)
                    onDone()
                } else {
                    Toast.makeText(activity, R.string.burk_pin_mismatch, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
