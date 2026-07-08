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

import android.animation.Animator
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import cx.ring.application.JamiApplication
import cx.ring.databinding.ActivityBurkOutgoingCallBinding
import cx.ring.views.AvatarFactory
import dagger.hilt.android.AndroidEntryPoint
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.disposables.CompositeDisposable
import net.jami.model.Call.CallStatus
import net.jami.model.Conference
import net.jami.model.Contact
import net.jami.model.Uri
import net.jami.services.AccountService
import net.jami.services.CallService
import net.jami.services.ContactService
import net.jami.services.NotificationService
import javax.inject.Inject
import javax.inject.Named

/**
 * "Ring upp": places a single 1:1 audio call the child initiated, and shows
 * the recipient's real photo/name for its whole lifetime — this one is never
 * blind, because the child already chose who to call before dialing.
 *
 * A call that arrived *incoming* and got answered never comes through here —
 * that flow hands off to [BurkConnectedCallActivity] instead, which shows no
 * identity at all, so the blind principle holds regardless of which screen
 * a given call is on.
 *
 * Deliberately does *not* implement net.jami.call.CallView/CallPresenter: that
 * contract is built for multi-party video conferencing, screen share, PIP, and
 * a dial pad. This is a single audio-only line with one button, so it talks to
 * CallService/ContactService directly instead of adapting 45 mostly-irrelevant
 * methods.
 *
 * Also doubles as the "resume an in-progress outgoing call" screen: it
 * understands a bare [Intent.ACTION_VIEW] + [NotificationService.KEY_CALL_ID]
 * intent (call IDs are globally unique, so no account ID is needed for that),
 * matching NotificationServiceImpl's existing call-notification content/
 * full-screen intent for non-incoming conference states.
 */
@AndroidEntryPoint
class BurkCallActivity : AppCompatActivity() {

    @Inject lateinit var callService: CallService
    @Inject lateinit var contactService: ContactService
    @Inject lateinit var accountService: AccountService
    @Inject @Named("UiScheduler") lateinit var uiScheduler: Scheduler

    private val disposables = CompositeDisposable()
    private lateinit var binding: ActivityBurkOutgoingCallBinding
    private var conference: Conference? = null
    private var contactInfoLoaded = false
    private var hasConnected = false
    private val callStartElapsedMs = SystemClock.elapsedRealtime()
    private var ring1: Animator? = null
    private var ring2: Animator? = null
    private var dotAnimators: List<Animator>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        JamiApplication.instance?.startDaemon(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setTurnScreenOn(true)
            setShowWhenLocked(true)
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        binding = ActivityBurkOutgoingCallBinding.inflate(layoutInflater)
        setContentView(binding.root)
        BurkInsets.applySystemBarPadding(binding.root)

        if (BurkPrefs(this).isKioskModeEnabled) {
            onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() { /* no-op: kid-proofed */ }
            })
        }

        binding.burkHangupButton.setOnClickListener { hangUp() }

        val callId = intent.getStringExtra(NotificationService.KEY_CALL_ID)
        val accountId = intent.getStringExtra(EXTRA_ACCOUNT_ID)
        val contactUriStr = intent.getStringExtra(EXTRA_CONTACT_URI)

        if (callId != null && accountId == null) {
            // Bare resume/attach intent: just watch the (already placed or
            // already answered) call. Account ID comes from the Conference itself.
            attachToCall(callId)
        } else if (accountId != null && contactUriStr != null) {
            val conversationUriStr = intent.getStringExtra(EXTRA_CONVERSATION_URI)
            binding.burkContactName.text = intent.getStringExtra(EXTRA_CONTACT_NAME) ?: ""
            val contactUri = Uri.fromString(contactUriStr)
            loadContactInfoByUri(accountId, contactUri)
            showRinging()
            disposables.add(callService
                .placeCallIfAllowed(accountId, conversationUriStr?.let { Uri.fromString(it) }, contactUri, false)
                .flatMapObservable { call -> callService.getConfUpdates(call) }
                .observeOn(uiScheduler)
                .subscribe({ conf -> onConferenceUpdate(conf) }, { showCallFailed() }))
        } else {
            finish()
        }
    }

    private fun attachToCall(callId: String) {
        disposables.add(callService.getConfUpdates(callId)
            .observeOn(uiScheduler)
            .subscribe({ conf -> onConferenceUpdate(conf) }, { showCallFailed() }))
    }

    private fun onConferenceUpdate(conf: Conference) {
        conference = conf
        if (!contactInfoLoaded) {
            conf.call?.contact?.let { contact ->
                loadContactInfo(conf.accountId, contact)
                contactInfoLoaded = true
            }
        }
        val state = conf.state
        when {
            state == CallStatus.FAILURE || state == CallStatus.BUSY -> showCallFailed()
            state == null || state.isOver -> {
                val failedQuickly = !hasConnected &&
                    SystemClock.elapsedRealtime() - callStartElapsedMs < BurkCallFailedActivity.QUICK_FAILURE_THRESHOLD_MS
                if (failedQuickly) showCallFailed() else finish()
            }
            state.isRinging -> showRinging()
            else -> {
                hasConnected = true
                showConnected()
            }
        }
    }

    private fun showCallFailed() {
        startActivity(BurkCallFailedActivity.intent(this))
        finish()
    }

    private fun loadContactInfoByUri(accountId: String, contactUri: Uri) {
        val account = accountService.getAccount(accountId) ?: return
        loadContactInfo(accountId, account.getContactFromCache(contactUri))
        contactInfoLoaded = true
    }

    private fun loadContactInfo(accountId: String, contact: Contact) {
        disposables.add(contactService.observeContact(accountId, contact, false)
            .observeOn(uiScheduler)
            .subscribe { vm ->
                binding.burkContactName.text = BurkNicknames(this).resolve(contact.uri.rawUriString, vm.displayName)
                disposables.add(AvatarFactory.getAvatar(this, vm, false)
                    .observeOn(uiScheduler)
                    .subscribe { drawable -> binding.burkContactPhoto.setImageDrawable(drawable) })
            })
    }

    private fun showRinging() {
        binding.burkStatusRow.visibility = View.VISIBLE
        startRingAnimation()
        if (dotAnimators == null) {
            dotAnimators = listOf(
                BurkAnim.dotPulse(binding.burkDot1),
                BurkAnim.dotPulse(binding.burkDot2, startDelayMs = 200),
                BurkAnim.dotPulse(binding.burkDot3, startDelayMs = 400)
            ).onEach { it.start() }
        }
    }

    private fun showConnected() {
        binding.burkStatusRow.visibility = View.INVISIBLE
        stopRingAnimation()
        dotAnimators?.forEach { it.cancel() }
        dotAnimators = null
    }

    private fun startRingAnimation() {
        if (ring1 != null) return
        ring1 = BurkAnim.ringPulse(binding.burkRing1, RING_DURATION_MS).also { it.start() }
        ring2 = BurkAnim.ringPulse(binding.burkRing2, RING_DURATION_MS, RING_DURATION_MS / 2).also { it.start() }
    }

    private fun stopRingAnimation() {
        ring1?.cancel(); ring2?.cancel()
        ring1 = null; ring2 = null
    }

    private fun hangUp() {
        conference?.let { conf ->
            if (conf.isSimpleCall) callService.hangUp(conf.accountId, conf.id)
            else callService.hangUpConference(conf.accountId, conf.id)
        }
        finish()
    }

    override fun onDestroy() {
        stopRingAnimation()
        dotAnimators?.forEach { it.cancel() }
        disposables.clear()
        super.onDestroy()
    }

    companion object {
        private const val RING_DURATION_MS = 1900L
        private const val EXTRA_ACCOUNT_ID = "burk.accountId"
        private const val EXTRA_CONVERSATION_URI = "burk.conversationUri"
        private const val EXTRA_CONTACT_URI = "burk.contactUri"
        private const val EXTRA_CONTACT_NAME = "burk.contactName"

        fun placeCallIntent(
            context: Context, accountId: String, conversationUri: String?, contactUri: String, contactName: String
        ) = Intent(context, BurkCallActivity::class.java).apply {
            putExtra(EXTRA_ACCOUNT_ID, accountId)
            putExtra(EXTRA_CONVERSATION_URI, conversationUri)
            putExtra(EXTRA_CONTACT_URI, contactUri)
            putExtra(EXTRA_CONTACT_NAME, contactName)
        }
    }
}
