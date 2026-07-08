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
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import cx.ring.R
import cx.ring.databinding.ActivityBurkSleepBinding

/**
 * "Sovläge": shown outside the availability window (or while "offline idag" is
 * set). A child can't do anything here but wait, so this screen polls local
 * time every 30s and hands control back to [BurkHomeActivity] the moment the
 * window opens (or offline-today is over). The three-dot settings button is
 * still available (same PIN-gated leave-kiosk as Hemskärm) — a kiosk screen
 * must never be able to lock out the person administering the device just
 * because the can happens to be asleep when they enter kiosk mode.
 */
class BurkSleepActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var binding: ActivityBurkSleepBinding
    private lateinit var prefs: BurkPrefs
    private val animators = mutableListOf<Animator>()

    private val wakeCheck = object : Runnable {
        override fun run() {
            if (BurkAvailability.isAwake(prefs)) {
                startActivity(Intent(this@BurkSleepActivity, BurkHomeActivity::class.java))
                finish()
            } else {
                handler.postDelayed(this, WAKE_CHECK_INTERVAL_MS)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = BurkPrefs(this)
        binding = ActivityBurkSleepBinding.inflate(layoutInflater)
        setContentView(binding.root)
        BurkInsets.applySystemBarPadding(binding.root)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { /* no interaction possible while asleep */ }
        })

        binding.burkSleepChip.text = getString(R.string.burk_sleep_opens_at, BurkAvailability.windowStartLabel(prefs))
        binding.burkSettingsButton.setOnClickListener { BurkSettingsDialog.show(this, prefs) }

        animators += BurkAnim.twinkle(binding.burkStar1, 3000).also { it.start() }
        animators += BurkAnim.twinkle(binding.burkStar2, 3000, 1100).also { it.start() }
        animators += BurkAnim.twinkle(binding.burkStar3, 3000, 1900).also { it.start() }
        animators += BurkAnim.bob(binding.burkZ1, 2400, dp(7f)).also { it.start() }
        animators += BurkAnim.bob(binding.burkZ2, 2400, dp(7f), 300).also { it.start() }
        animators += BurkAnim.bob(binding.burkZ3, 2400, dp(7f), 600).also { it.start() }
        animators += BurkAnim.bob(binding.burkSleepingCan, 3400, dp(7f)).also { it.start() }
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    override fun onResume() {
        super.onResume()
        handler.post(wakeCheck)
    }

    override fun onPause() {
        handler.removeCallbacks(wakeCheck)
        super.onPause()
    }

    override fun onDestroy() {
        animators.forEach { it.cancel() }
        super.onDestroy()
    }

    companion object {
        private const val WAKE_CHECK_INTERVAL_MS = 30_000L

        fun intent(context: Context) = Intent(context, BurkSleepActivity::class.java)
    }
}
