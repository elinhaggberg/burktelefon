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

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Toggles the HOME/LAUNCHER activity-alias that lets Burktelefonen offer
 * itself as the device's default launcher. Disabled by default so installing
 * this fork doesn't change anything about the phone until a parent opts in
 * from the settings dialog.
 */
object BurkLauncher {
    private const val ALIAS_NAME = "cx.ring.burktelefon.BurkLauncherAlias"

    fun setEnabled(context: Context, enabled: Boolean) {
        val pm = context.packageManager
        val component = ComponentName(context.packageName, ALIAS_NAME)
        pm.setComponentEnabledSetting(
            component,
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
    }
}
