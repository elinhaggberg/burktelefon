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
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import cx.ring.databinding.ActivityBurkNoInternetBinding

/**
 * Shown in place of Hemskärm whenever the device has no usable internet
 * connection while otherwise awake (checked on every Hemskärm resume).
 * Polls connectivity and hands control back to [BurkHomeActivity] as soon
 * as it's restored — Hemskärm re-checks everything itself, including
 * whether the availability window closed in the meantime.
 *
 * Keeps the same three-dot settings button as every other "waiting" kiosk
 * screen: a screen a kid can get stuck looking at must never be the only
 * thing standing between a parent and leaving kiosk mode (see the Sovläge
 * lockout fix).
 */
class BurkNoInternetActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var binding: ActivityBurkNoInternetBinding
    private lateinit var prefs: BurkPrefs

    private val connectivityCheck = object : Runnable {
        override fun run() {
            if (BurkConnectivity.hasInternet(this@BurkNoInternetActivity)) {
                startActivity(BurkHomeActivity.intent(this@BurkNoInternetActivity))
                finish()
            } else {
                handler.postDelayed(this, CHECK_INTERVAL_MS)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = BurkPrefs(this)
        binding = ActivityBurkNoInternetBinding.inflate(layoutInflater)
        setContentView(binding.root)
        BurkInsets.applySystemBarPadding(binding.root)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { /* no-op: nothing to do but wait for the connection */ }
        })

        binding.burkSettingsButton.setOnClickListener { BurkSettingsDialog.show(this, prefs) }
    }

    override fun onResume() {
        super.onResume()
        handler.post(connectivityCheck)
    }

    override fun onPause() {
        handler.removeCallbacks(connectivityCheck)
        super.onPause()
    }

    companion object {
        private const val CHECK_INTERVAL_MS = 5_000L

        fun intent(context: Context) = Intent(context, BurkNoInternetActivity::class.java)
    }
}
