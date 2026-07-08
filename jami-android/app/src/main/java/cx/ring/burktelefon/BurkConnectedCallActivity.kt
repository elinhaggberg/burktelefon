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

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import cx.ring.application.JamiApplication
import cx.ring.databinding.ActivityBurkConnectedCallBinding
import dagger.hilt.android.AndroidEntryPoint
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.disposables.CompositeDisposable
import net.jami.model.Call.CallStatus
import net.jami.model.Conference
import net.jami.services.CallService
import net.jami.services.NotificationService
import javax.inject.Inject
import javax.inject.Named

/**
 * Shown right after a blind incoming call is answered. Deliberately never
 * looks up or shows the contact's name/photo — that would defeat the whole
 * point of "you find out who it is by talking, not by looking at a screen".
 * Only needs [CallService] (to hang up); no ContactService/AccountService
 * dependency at all, so there's no accidental way to leak identity here.
 */
@AndroidEntryPoint
class BurkConnectedCallActivity : AppCompatActivity() {

    @Inject lateinit var callService: CallService
    @Inject @Named("UiScheduler") lateinit var uiScheduler: Scheduler

    private val disposables = CompositeDisposable()
    private lateinit var binding: ActivityBurkConnectedCallBinding
    private var conference: Conference? = null
    private val screenStartElapsedMs = SystemClock.elapsedRealtime()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        JamiApplication.instance?.startDaemon(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setTurnScreenOn(true)
            setShowWhenLocked(true)
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        binding = ActivityBurkConnectedCallBinding.inflate(layoutInflater)
        setContentView(binding.root)
        BurkInsets.applySystemBarPadding(binding.root)

        if (BurkPrefs(this).isKioskModeEnabled) {
            onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() { /* no-op: kid-proofed */ }
            })
        }

        binding.burkHangupButton.setOnClickListener { hangUp() }

        val callId = intent.getStringExtra(NotificationService.KEY_CALL_ID)
        if (callId == null) { finish(); return }
        disposables.add(callService.getConfUpdates(callId)
            .observeOn(uiScheduler)
            .subscribe({ conf -> onConferenceUpdate(conf) }, { showCallFailed() }))
    }

    private fun onConferenceUpdate(conf: Conference) {
        conference = conf
        val state = conf.state
        when {
            state == CallStatus.FAILURE || state == CallStatus.BUSY -> showCallFailed()
            state == null || state.isOver -> {
                val failedQuickly = SystemClock.elapsedRealtime() - screenStartElapsedMs <
                    BurkCallFailedActivity.QUICK_FAILURE_THRESHOLD_MS
                if (failedQuickly) showCallFailed() else finish()
            }
        }
    }

    private fun showCallFailed() {
        startActivity(BurkCallFailedActivity.intent(this))
        finish()
    }

    private fun hangUp() {
        conference?.let { conf ->
            if (conf.isSimpleCall) callService.hangUp(conf.accountId, conf.id)
            else callService.hangUpConference(conf.accountId, conf.id)
        }
        finish()
    }

    override fun onDestroy() {
        disposables.clear()
        super.onDestroy()
    }

    companion object {
        fun intent(context: Context, callId: String) =
            Intent(context, BurkConnectedCallActivity::class.java).apply {
                putExtra(NotificationService.KEY_CALL_ID, callId)
            }
    }
}
