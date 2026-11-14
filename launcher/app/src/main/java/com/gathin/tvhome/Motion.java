package com.gathin.tvhome;

import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

/** Reduced motion (config.xml reduced_motion): the one spring and the one time limit every animation uses then. */
final class Motion {

    /** Design tokens (res/values/tokens.xml, motion_*): press and colour changes, focus and menus, screens. */
    static final int FAST = 120, STANDARD = 200, SLOW = 320;
    /** Entering things decelerate into place; things that move between places ease both ways. */
    static final android.view.animation.Interpolator ENTER = new android.view.animation.DecelerateInterpolator(1.6f);
    static final android.view.animation.Interpolator MOVE = new android.view.animation.AccelerateDecelerateInterpolator();
    /** The focused control's zoom, the press dip under OK, and how far a control nudges at the end of its row. */
    static final float FOCUS_SCALE = 1.04f, PRESS_SCALE = 0.97f, NUDGE_DP = 6f;

    /** The longest any reduced-motion animation runs, in milliseconds. */
    static final int REDUCED_MS = 90;
    /** Critically damped (no bounce) and stiff enough that any move, even across the screen, settles in 5 frames. */
    static final float REDUCED_STIFFNESS = 15000f;
    /** The spring stops once it is within this many pixels of its target. */
    static final float REDUCED_MIN_CHANGE = 2f;

    private Motion() {
    }

    static void reduce(SpringAnimation spring) {
        spring.setSpring(new SpringForce().setStiffness(REDUCED_STIFFNESS).setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY));
        spring.setMinimumVisibleChange(REDUCED_MIN_CHANGE);
    }
}
