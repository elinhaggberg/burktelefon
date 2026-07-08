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
import android.content.SharedPreferences
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Local-only settings for the Burktelefonen kiosk launcher: today's offline flag,
 * kiosk (default-launcher) mode, and the daily availability window.
 *
 * Everything here is per-device. There is no cross-device sync yet (see the
 * "global availability window" follow-up) — each device only ever checks its
 * own local time against its own locally-configured window.
 */
class BurkPrefs(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** True if this device was explicitly marked offline for the remainder of today. */
    fun isOfflineToday(): Boolean {
        val storedDate = prefs.getString(KEY_OFFLINE_DATE, null)
        if (storedDate != todayKey()) {
            // A new day rolled over: the flag auto-resets, per spec.
            if (prefs.getBoolean(KEY_OFFLINE_TODAY, false)) {
                prefs.edit().putBoolean(KEY_OFFLINE_TODAY, false).remove(KEY_OFFLINE_DATE).apply()
            }
            return false
        }
        return prefs.getBoolean(KEY_OFFLINE_TODAY, false)
    }

    fun setOfflineToday(offline: Boolean) {
        prefs.edit()
            .putBoolean(KEY_OFFLINE_TODAY, offline)
            .putString(KEY_OFFLINE_DATE, todayKey())
            .apply()
    }

    var isKioskModeEnabled: Boolean
        get() = prefs.getBoolean(KEY_KIOSK_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_KIOSK_MODE, value).apply()

    /** Minutes after midnight, local time. Defaults to 09:00. */
    var windowStartMinutes: Int
        get() = prefs.getInt(KEY_WINDOW_START, DEFAULT_WINDOW_START_MINUTES)
        set(value) = prefs.edit().putInt(KEY_WINDOW_START, value).apply()

    /** Minutes after midnight, local time. Defaults to 19:00. */
    var windowEndMinutes: Int
        get() = prefs.getInt(KEY_WINDOW_END, DEFAULT_WINDOW_END_MINUTES)
        set(value) = prefs.edit().putInt(KEY_WINDOW_END, value).apply()

    /** True once a parent has set an unlock PIN (before that, leaving kiosk mode needs no PIN). */
    fun hasPinSet(): Boolean = prefs.contains(KEY_PIN_HASH)

    fun setPin(pin: String) {
        prefs.edit().putString(KEY_PIN_HASH, hashPin(pin)).apply()
    }

    fun clearPin() {
        prefs.edit().remove(KEY_PIN_HASH).apply()
    }

    fun verifyPin(pin: String): Boolean = prefs.getString(KEY_PIN_HASH, null) == hashPin(pin)

    private fun hashPin(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun todayKey(): String = dateFormat.format(java.util.Date())

    companion object {
        private const val PREFS_NAME = "burktelefon_prefs"
        private const val KEY_OFFLINE_TODAY = "offline_today"
        private const val KEY_OFFLINE_DATE = "offline_today_date"
        private const val KEY_KIOSK_MODE = "kiosk_mode_enabled"
        private const val KEY_WINDOW_START = "window_start_minutes"
        private const val KEY_WINDOW_END = "window_end_minutes"
        private const val KEY_PIN_HASH = "kiosk_exit_pin_hash"
        val DEFAULT_WINDOW_START_MINUTES = TimeUnit.HOURS.toMinutes(9).toInt()
        val DEFAULT_WINDOW_END_MINUTES = TimeUnit.HOURS.toMinutes(18).toInt()
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    }
}
