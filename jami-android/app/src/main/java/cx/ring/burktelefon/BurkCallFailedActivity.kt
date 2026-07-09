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
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import cx.ring.databinding.ActivityBurkCallFailedBinding

/**
 * Shown when a call ends in a technical failure (Jami's underlying P2P
 * connection never establishing, or a busy line) rather than a plain
 * unanswered call. An unanswered call just silently returns to Hemskärm,
 * like a landline that stops ringing — this screen is only for "something's
 * actually wrong," since a kid has no way to diagnose a network problem and
 * needs a nudge to get an adult involved. There's nothing else to protect
 * here (the call already ended), so the back button behaves normally.
 */
class BurkCallFailedActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBurkCallFailedBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setTurnScreenOn(true)
            setShowWhenLocked(true)
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        binding = ActivityBurkCallFailedBinding.inflate(layoutInflater)
        setContentView(binding.root)
        BurkInsets.applySystemBarPadding(binding.root)

        binding.burkCallFailedBackButton.setOnClickListener { finish() }
    }

    companion object {
        /**
         * A real technical failure (e.g. the same-network NAT-hairpin issue
         * seen in testing) doesn't reliably surface as an explicit
         * [net.jami.model.Call.CallStatus.FAILURE]/[net.jami.model.Call.CallStatus.BUSY]
         * — it can just silently go HUNGUP/OVER after a stretch of failed ICE
         * negotiation, which can itself take a while. Rather than guess how
         * long that takes, this is pinned to what we actually know for
         * certain: between two Burktelefonen devices, the *only* legitimate
         * "isOver, never connected" case is a fully-timed-out unanswered call,
         * which can't happen before the callee's own
         * [BurkIncomingCallActivity.ANSWER_TIMEOUT_MS]. So anything that ends
         * meaningfully faster than that (with a safety margin) is treated as
         * a failure worth surfacing rather than a quiet "nobody answered."
         */
        const val QUICK_FAILURE_THRESHOLD_MS = BurkIncomingCallActivity.ANSWER_TIMEOUT_MS - 5_000L

        fun intent(context: Context) = Intent(context, BurkCallFailedActivity::class.java)
    }
}
