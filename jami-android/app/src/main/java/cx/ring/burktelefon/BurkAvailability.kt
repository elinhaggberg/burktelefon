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

import java.util.Calendar

/**
 * Client-side "is the can open right now" check: local time against the
 * locally-configured availability window, plus the "offline idag" override.
 *
 * There is deliberately no network/sync concept here yet — distributing one
 * shared window across a family's devices is a separate, paused problem.
 */
object BurkAvailability {

    fun isAwake(prefs: BurkPrefs, now: Calendar = Calendar.getInstance()): Boolean {
        if (prefs.isOfflineToday()) return false
        val minutesNow = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        return minutesNow in prefs.windowStartMinutes until prefs.windowEndMinutes
    }

    /** "09:00"-style label for the window's start, e.g. for the sleep screen. */
    fun windowStartLabel(prefs: BurkPrefs): String = minutesToLabel(prefs.windowStartMinutes)

    private fun minutesToLabel(minutesAfterMidnight: Int): String {
        val h = minutesAfterMidnight / 60
        val m = minutesAfterMidnight % 60
        return String.format("%02d:%02d", h, m)
    }
}
