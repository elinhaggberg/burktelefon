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

/**
 * Per-device nickname overrides for how a contact's name shows up on
 * Burktelefonen (e.g. "Mamma" instead of the contact's real Jami profile
 * name). Purely a local display label — never touches the actual Jami
 * contact/profile, so it doesn't sync anywhere and doesn't affect what the
 * contact sees of themselves.
 *
 * Keyed by the contact's raw Jami URI string. That's unique enough for a
 * single-account kiosk device; it doesn't need to be scoped by account ID.
 */
class BurkNicknames(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun get(contactUriString: String): String? = prefs.getString(key(contactUriString), null)

    /** Setting a blank/null nickname clears the override, reverting to the real name. */
    fun set(contactUriString: String, nickname: String?) {
        val trimmed = nickname?.trim()
        if (trimmed.isNullOrEmpty()) {
            prefs.edit().remove(key(contactUriString)).apply()
        } else {
            prefs.edit().putString(key(contactUriString), trimmed).apply()
        }
    }

    fun resolve(contactUriString: String, realName: String): String = get(contactUriString) ?: realName

    private fun key(contactUriString: String) = "nickname_$contactUriString"

    companion object {
        private const val PREFS_NAME = "burktelefon_nicknames"
    }
}
