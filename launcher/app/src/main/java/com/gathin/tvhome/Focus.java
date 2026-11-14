package com.gathin.tvhome;

import android.view.KeyEvent;
import android.view.View;

/**
 * The one focus vocabulary (design/REVAMP-PLAN.md, 3.2) for controls that are not app tiles: buttons, cards, menu and
 * settings rows. Focus zooms the control a little (its glass background turns bright through glass_bg), OK dips it
 * and lets go, and the end of a row nudges it. Tiles do the same in AppAdapter, with the moving glass slab on top.
 * Only scale and translation animate: no layout, no layer the size of the screen.
 */
final class Focus {
    private Focus() {
    }

    /** Zoom on focus and the press dip under OK. {@code zoom} false keeps the size (rows inside a panel). */
    static void attach(View v, boolean zoom, boolean reducedMotion) {
        attach(v, zoom, reducedMotion, null);
    }

    /**
     * The same, in front of the control's own key handling ({@code inner}, may be null). When {@code inner} swallows
     * an arrow and focus stays here, the row has ended: the control nudges.
     */
    static void attach(View v, boolean zoom, boolean reducedMotion, View.OnKeyListener inner) {
        View.OnFocusChangeListener previous = v.getOnFocusChangeListener();
        v.setOnFocusChangeListener((view, has) -> {
            if (previous != null) previous.onFocusChange(view, has);
            if (zoom) scaleTo(view, has ? Motion.FOCUS_SCALE : 1f, Motion.STANDARD, reducedMotion);
        });
        v.setOnKeyListener((view, code, event) -> {
            if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) press(view, zoom, reducedMotion);
                else if (event.getAction() == KeyEvent.ACTION_UP) release(view, zoom, reducedMotion);
            }
            boolean handled = inner != null && inner.onKey(view, code, event);
            if (handled && event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0 && isArrow(code)
                    && view.isFocused()) nudge(view, code, reducedMotion);
            return handled;
        });
    }

    static boolean isArrow(int code) {
        return code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT
                || code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN;
    }

    /** OK went down: the control dips below its focused size. */
    static void press(View v, boolean zoom, boolean reducedMotion) {
        if (reducedMotion) return;
        float base = zoom && v.isFocused() ? Motion.FOCUS_SCALE : 1f;
        scaleTo(v, base * Motion.PRESS_SCALE, Motion.FAST, false);
    }

    /** OK came up: back to the focused size. */
    static void release(View v, boolean zoom, boolean reducedMotion) {
        scaleTo(v, zoom && v.isFocused() ? Motion.FOCUS_SCALE : 1f, Motion.FAST, reducedMotion);
    }

    /** Nowhere further to go: a short push in the key's direction and back. */
    static void nudge(View v, int keyCode, boolean reducedMotion) {
        if (reducedMotion || v == null) return;
        float d = Motion.NUDGE_DP * v.getResources().getDisplayMetrics().density;
        float dx = keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? -d : keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ? d : 0f;
        float dy = keyCode == KeyEvent.KEYCODE_DPAD_UP ? -d : keyCode == KeyEvent.KEYCODE_DPAD_DOWN ? d : 0f;
        v.animate().cancel();
        float s = v.getScaleX();
        v.animate().translationX(dx).translationY(dy).setDuration(Motion.FAST / 2).setInterpolator(Motion.MOVE)
                .withEndAction(() -> v.animate().translationX(0f).translationY(0f).scaleX(s).scaleY(s)
                        .setDuration(Motion.FAST).setInterpolator(Motion.ENTER).start())
                .start();
    }

    private static void scaleTo(View v, float s, int ms, boolean reducedMotion) {
        v.animate().scaleX(s).scaleY(s).setDuration(reducedMotion ? Motion.REDUCED_MS : ms).setInterpolator(Motion.ENTER).start();
    }
}
