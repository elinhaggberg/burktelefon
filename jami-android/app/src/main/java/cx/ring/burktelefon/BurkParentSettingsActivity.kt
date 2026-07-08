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
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import cx.ring.R
import cx.ring.databinding.ActivityBurkParentSettingsBinding

/**
 * Reached from the *real* Jami app's settings (not from inside the kiosk UI).
 * This is where a parent turns kiosk mode on/off, sets the PIN required to
 * leave it, and can jump straight into the kiosk UI to preview it.
 */
class BurkParentSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBurkParentSettingsBinding
    private lateinit var prefs: BurkPrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = BurkPrefs(this)
        binding = ActivityBurkParentSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.burk_parent_settings_title)

        binding.burkParentKioskSwitch.isChecked = prefs.isKioskModeEnabled
        binding.burkParentKioskSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.isKioskModeEnabled = checked
            BurkLauncher.setEnabled(this, checked)
        }

        binding.burkParentPinSetButton.setOnClickListener {
            BurkPinDialogs.promptSetNewPin(this, prefs) { refreshPinUi() }
        }
        binding.burkParentPinRemoveButton.setOnClickListener {
            AlertDialog.Builder(this)
                .setMessage(R.string.burk_parent_pin_remove_confirm)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    prefs.clearPin()
                    refreshPinUi()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        binding.burkParentOpenRow.setOnClickListener {
            startActivity(BurkHomeActivity.intent(this))
        }

        binding.burkParentWindowStartButton.setOnClickListener { pickTime(isStart = true) }
        binding.burkParentWindowEndButton.setOnClickListener { pickTime(isStart = false) }

        refreshPinUi()
        refreshWindowUi()
    }

    private fun refreshWindowUi() {
        binding.burkParentWindowStartButton.text = BurkAvailability.minutesToLabel(prefs.windowStartMinutes)
        binding.burkParentWindowEndButton.text = BurkAvailability.minutesToLabel(prefs.windowEndMinutes)
    }

    private fun pickTime(isStart: Boolean) {
        val currentMinutes = if (isStart) prefs.windowStartMinutes else prefs.windowEndMinutes
        TimePickerDialog(
            this,
            { _, hour, minute ->
                val newMinutes = hour * 60 + minute
                val otherMinutes = if (isStart) prefs.windowEndMinutes else prefs.windowStartMinutes
                val valid = if (isStart) newMinutes < otherMinutes else newMinutes > otherMinutes
                if (!valid) {
                    Toast.makeText(this, R.string.burk_parent_window_invalid, Toast.LENGTH_SHORT).show()
                } else {
                    if (isStart) prefs.windowStartMinutes = newMinutes else prefs.windowEndMinutes = newMinutes
                    refreshWindowUi()
                }
            },
            currentMinutes / 60, currentMinutes % 60, true
        ).show()
    }

    private fun refreshPinUi() {
        val hasPin = prefs.hasPinSet()
        binding.burkParentPinStatus.text =
            getString(if (hasPin) R.string.burk_parent_pin_set else R.string.burk_parent_pin_not_set)
        binding.burkParentPinSetButton.setText(
            if (hasPin) R.string.burk_parent_pin_change_button else R.string.burk_parent_pin_set_button
        )
        binding.burkParentPinRemoveButton.visibility = if (hasPin) View.VISIBLE else View.GONE
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    companion object {
        fun intent(context: Context) = Intent(context, BurkParentSettingsActivity::class.java)
    }
}
