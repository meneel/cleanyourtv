package com.gathin.tvhome;

import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

/** Reduced motion (config.xml reduced_motion): the one spring and the one time limit every animation uses then. */
final class Motion {

    /** The longest any reduced-motion animation runs, in milliseconds. */
    static final int REDUCED_MS = 90;
    /** Critically damped (no bounce) and stiff enough that any move, even across the screen, settles in 5 frames. */
    static final float REDUCED_STIFFNESS = 15000f;
    /** The spring stops once it is within this many pixels of its target. */
    static final float REDUCED_MIN_CHANGE = 2f;

    /** For moves: a gentle start and a long soft landing, no overshoot (CSS "ease"). The earlier quint curve did 60% of
     *  the move in its first 15% and read as abrupt. */
    static final android.view.animation.Interpolator SMOOTH = new android.view.animation.PathInterpolator(0.25f, 0.1f, 0.25f, 1f);
    /** For fades: slow in, slow out, so a cross-fade has no visible start or end. */
    static final android.view.animation.Interpolator FADE = new android.view.animation.PathInterpolator(0.4f, 0f, 0.2f, 1f);

    private Motion() {
    }

    static void reduce(SpringAnimation spring) {
        spring.setSpring(new SpringForce().setStiffness(REDUCED_STIFFNESS).setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY));
        spring.setMinimumVisibleChange(REDUCED_MIN_CHANGE);
    }
}
