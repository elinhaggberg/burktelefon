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
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator

/**
 * Small reusable Animator builders reproducing the CSS @keyframes from the
 * Burk-stilreferens.dc.html design reference (bk-ring, bk-shake, bk-twinkle, bk-bob).
 * Callers own the returned Animator: start it, and cancel it in onPause/onDestroy.
 */
object BurkAnim {

    /** bk-ring: scale .7->1.7, opacity .85->0, restarting from scratch each cycle (not reversing). */
    fun ringPulse(view: View, durationMs: Long, startDelayMs: Long = 0): Animator {
        val holder = PropertyValuesHolder.ofFloat(View.SCALE_X, 0.7f, 1.7f)
        val holder2 = PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.7f, 1.7f)
        val holder3 = PropertyValuesHolder.ofFloat(View.ALPHA, 0.85f, 0f)
        return ObjectAnimator.ofPropertyValuesHolder(view, holder, holder2, holder3).apply {
            duration = durationMs
            startDelay = startDelayMs
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.RESTART
            interpolator = DecelerateInterpolator()
        }
    }

    /** bk-shake: rotate -6deg <-> 6deg, pivoting near the can's hanging point. */
    fun shake(view: View, durationMs: Long = 500): Animator {
        view.pivotX = view.width / 2f
        view.pivotY = view.height * 0.18f
        return ObjectAnimator.ofFloat(view, View.ROTATION, -6f, 6f).apply {
            duration = durationMs / 2
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }
    }

    /** bk-twinkle: opacity .25<->1, scale .85<->1. */
    fun twinkle(view: View, durationMs: Long = 3000, startDelayMs: Long = 0): Animator {
        val alpha = PropertyValuesHolder.ofFloat(View.ALPHA, 0.25f, 1f)
        val sx = PropertyValuesHolder.ofFloat(View.SCALE_X, 0.85f, 1f)
        val sy = PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.85f, 1f)
        return ObjectAnimator.ofPropertyValuesHolder(view, alpha, sx, sy).apply {
            duration = durationMs / 2
            startDelay = startDelayMs
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }
    }

    /** bk-bob: translateY 0 <-> -amplitudeDp. */
    fun bob(view: View, durationMs: Long, amplitudePx: Float, startDelayMs: Long = 0): Animator {
        return ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, 0f, -amplitudePx).apply {
            duration = durationMs / 2
            startDelay = startDelayMs
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }
    }

    /** bk-dots: opacity pulse used for the "Ringer..." trailing dots. */
    fun dotPulse(view: View, durationMs: Long = 1400, startDelayMs: Long = 0): Animator {
        return ObjectAnimator.ofFloat(view, View.ALPHA, 0.25f, 1f).apply {
            duration = durationMs / 2
            startDelay = startDelayMs
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }
    }
}
