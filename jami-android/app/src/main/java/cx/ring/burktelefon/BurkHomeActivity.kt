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
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import cx.ring.R
import cx.ring.application.JamiApplication
import cx.ring.client.HomeActivity
import cx.ring.databinding.ActivityBurkHomeBinding
import cx.ring.databinding.DialogBurkSettingsBinding
import dagger.hilt.android.AndroidEntryPoint
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.disposables.CompositeDisposable
import net.jami.services.ConversationFacade
import net.jami.smartlist.ConversationItemViewModel
import javax.inject.Inject
import javax.inject.Named

/**
 * "Hemskärm": the kiosk launcher root. A grid of real Jami contacts (photo +
 * name imported straight from the account); tapping one places an outgoing
 * call. The three-dot button opens settings: offline-today, and a PIN-gated
 * escape hatch back to the real Jami app for contact administration. Kiosk
 * mode itself is only turned on/off from BurkParentSettingsActivity, reached
 * from the real Jami app — not from in here.
 */
@AndroidEntryPoint
class BurkHomeActivity : AppCompatActivity() {

    @Inject lateinit var conversationFacade: ConversationFacade
    @Inject @Named("UiScheduler") lateinit var uiScheduler: Scheduler

    private val disposables = CompositeDisposable()
    private lateinit var binding: ActivityBurkHomeBinding
    private lateinit var prefs: BurkPrefs
    private lateinit var adapter: BurkContactAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        JamiApplication.instance?.startDaemon(this)
        prefs = BurkPrefs(this)
        binding = ActivityBurkHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        BurkInsets.applySystemBarPadding(binding.root)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!prefs.isKioskModeEnabled) {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
                // else: no-op, this is the kiosk root, nowhere to go back to.
            }
        })

        adapter = BurkContactAdapter(uiScheduler) { vm -> callContact(vm) }
        binding.burkContactGrid.layoutManager = GridLayoutManager(this, 2)
        binding.burkContactGrid.adapter = adapter

        binding.burkSettingsButton.setOnClickListener { showSettingsDialog() }

        disposables.add(conversationFacade.getConversationViewModelList()
            .observeOn(uiScheduler)
            .subscribe { list ->
                val sorted = list.sortedBy { it.title.lowercase() }
                adapter.submitList(sorted)
                binding.burkEmptyState.visibility = if (sorted.isEmpty()) View.VISIBLE else View.GONE
            })
    }

    override fun onResume() {
        super.onResume()
        if (!BurkAvailability.isAwake(prefs)) {
            startActivity(BurkSleepActivity.intent(this))
            finish()
            return
        }
        // Deliberately NOT using startLockTask()/screen pinning here: Android
        // treats a genuine incoming Telecom call as important enough to
        // interrupt a pinned screen, but only via its own "detach to answer"
        // system prompt — which pre-empts our blind Inkommande screen
        // entirely. Kiosk protection here is limited to the Home-launcher
        // registration and the back-button no-op below; a true unattended
        // lock (no exit gesture at all) would need Device Owner provisioning,
        // which is a bigger follow-up.
    }

    private fun callContact(vm: ConversationItemViewModel) {
        val contact = vm.getContact() ?: return
        startActivity(BurkCallActivity.placeCallIntent(
            this, vm.accountId, vm.uri.rawUriString, contact.contact.uri.rawUriString, vm.title
        ))
    }

    private fun showSettingsDialog() {
        val dialogBinding = DialogBurkSettingsBinding.inflate(layoutInflater)
        dialogBinding.burkOfflineSwitch.isChecked = prefs.isOfflineToday()

        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.burk_settings_close, null)
            .create()

        dialogBinding.burkOfflineSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.setOfflineToday(checked)
        }
        dialogBinding.burkLeaveKioskRow.setOnClickListener {
            if (!prefs.hasPinSet()) {
                leaveKioskMode()
                dialog.dismiss()
            } else {
                BurkPinDialogs.promptEnterPin(this, getString(R.string.burk_pin_enter_to_leave)) { pin ->
                    if (prefs.verifyPin(pin)) {
                        leaveKioskMode()
                        dialog.dismiss()
                    } else {
                        Toast.makeText(this, R.string.burk_pin_wrong, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        dialog.show()
    }

    private fun leaveKioskMode() {
        startActivity(Intent(this, HomeActivity::class.java))
    }

    override fun onDestroy() {
        disposables.clear()
        super.onDestroy()
    }

    companion object {
        fun intent(context: Context) = Intent(context, BurkHomeActivity::class.java)
    }
}
