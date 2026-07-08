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

import android.app.AlertDialog
import android.content.Intent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import cx.ring.R
import cx.ring.client.HomeActivity
import cx.ring.databinding.DialogBurkSettingsBinding

/**
 * The three-dot settings menu (offline-today + PIN-gated leave-kiosk), shared
 * by every kiosk screen. A kiosk screen must never be able to permanently
 * lock out the person administering the device, so this is reachable from
 * both [BurkHomeActivity] and [BurkSleepActivity] — not just the home grid.
 */
object BurkSettingsDialog {

    fun show(activity: AppCompatActivity, prefs: BurkPrefs) {
        val dialogBinding = DialogBurkSettingsBinding.inflate(activity.layoutInflater)
        dialogBinding.burkOfflineSwitch.isChecked = prefs.isOfflineToday()

        val dialog = AlertDialog.Builder(activity)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.burk_settings_close, null)
            .create()

        dialogBinding.burkOfflineSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.setOfflineToday(checked)
        }
        dialogBinding.burkLeaveKioskRow.setOnClickListener {
            if (!prefs.hasPinSet()) {
                leaveKioskMode(activity)
                dialog.dismiss()
            } else {
                BurkPinDialogs.promptEnterPin(activity, activity.getString(R.string.burk_pin_enter_to_leave)) { pin ->
                    if (prefs.verifyPin(pin)) {
                        leaveKioskMode(activity)
                        dialog.dismiss()
                    } else {
                        Toast.makeText(activity, R.string.burk_pin_wrong, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        dialog.show()
    }

    private fun leaveKioskMode(activity: AppCompatActivity) {
        activity.startActivity(Intent(activity, HomeActivity::class.java))
    }
}
