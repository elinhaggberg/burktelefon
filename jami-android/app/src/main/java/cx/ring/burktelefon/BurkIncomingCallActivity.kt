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
import android.os.CountDownTimer
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import cx.ring.application.JamiApplication
import cx.ring.databinding.ActivityBurkIncomingCallBinding
import dagger.hilt.android.AndroidEntryPoint
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.disposables.CompositeDisposable
import net.jami.model.Conference
import net.jami.services.CallService
import net.jami.services.NotificationService
import javax.inject.Inject
import javax.inject.Named

/**
 * "Inkommande": blind by design — never shows the caller's name or photo, only
 * that *someone* is calling. There is no decline button, only answer-or-timeout,
 * so the child never has to make a "reject a person" decision.
 *
 * On answer, hands off to [BurkConnectedCallActivity], which stays just as
 * blind — the screen never reveals identity, on this call or any other;
 * finding out who it is happens by talking, not by looking at the phone.
 */
@AndroidEntryPoint
class BurkIncomingCallActivity : AppCompatActivity() {

    @Inject lateinit var callService: CallService
    @Inject @Named("UiScheduler") lateinit var uiScheduler: Scheduler

    private val disposables = CompositeDisposable()
    private lateinit var binding: ActivityBurkIncomingCallBinding
    private var accountId: String? = null
    private var callId: String? = null
    private var answered = false
    private var timeoutTimer: CountDownTimer? = null
    private var ring1: Animator? = null
    private var ring2: Animator? = null
    private var shake: Animator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        JamiApplication.instance?.startDaemon(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setTurnScreenOn(true)
            setShowWhenLocked(true)
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        binding = ActivityBurkIncomingCallBinding.inflate(layoutInflater)
        setContentView(binding.root)
        BurkInsets.applySystemBarPadding(binding.root)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { /* no-op: answer or let it time out */ }
        })

        val accountId = intent.getStringExtra(EXTRA_ACCOUNT_ID)
        val callId = intent.getStringExtra(NotificationService.KEY_CALL_ID)
        if (accountId == null || callId == null) { finish(); return }
        this.accountId = accountId
        this.callId = callId

        // Defensive re-check: NotificationServiceImpl should already have gated this
        // before ever launching this screen, but a sleeping/offline device must
        // never show or ring for a call, no matter how it got here.
        if (!BurkAvailability.isAwake(BurkPrefs(this))) {
            callService.refuse(accountId, callId)
            finish()
            return
        }

        binding.burkAnswerButton.setOnClickListener { answer() }

        binding.burkCan.post {
            shake = BurkAnim.shake(binding.burkCan).also { it.start() }
        }
        ring1 = BurkAnim.ringPulse(binding.burkRing1, RING_DURATION_MS).also { it.start() }
        ring2 = BurkAnim.ringPulse(binding.burkRing2, RING_DURATION_MS, RING_DURATION_MS / 2).also { it.start() }

        disposables.add(callService.getConfUpdates(callId)
            .observeOn(uiScheduler)
            .subscribe({ conf -> onConferenceUpdate(conf) }, { finish() }))

        timeoutTimer = object : CountDownTimer(ANSWER_TIMEOUT_MS, ANSWER_TIMEOUT_MS) {
            override fun onTick(millisUntilFinished: Long) {}
            override fun onFinish() {
                if (!answered) {
                    callService.refuse(accountId, callId)
                    finish()
                }
            }
        }.start()
    }

    private fun onConferenceUpdate(conf: Conference) {
        val state = conf.state ?: return finish()
        if (state.isOver && !answered) finish()
    }

    private fun answer() {
        val accountId = accountId ?: return
        val callId = callId ?: return
        if (answered) return
        answered = true
        timeoutTimer?.cancel()
        callService.accept(accountId, callId, false)
        startActivity(BurkConnectedCallActivity.intent(this, callId))
        finish()
    }

    override fun onDestroy() {
        timeoutTimer?.cancel()
        ring1?.cancel(); ring2?.cancel(); shake?.cancel()
        disposables.clear()
        super.onDestroy()
    }

    companion object {
        private const val RING_DURATION_MS = 1700L
        private const val ANSWER_TIMEOUT_MS = 25_000L
        private const val EXTRA_ACCOUNT_ID = "burk.accountId"

        fun intent(context: Context, accountId: String, callId: String) =
            Intent(context, BurkIncomingCallActivity::class.java).apply {
                putExtra(EXTRA_ACCOUNT_ID, accountId)
                putExtra(NotificationService.KEY_CALL_ID, callId)
            }
    }
}
