package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * The switch at the end of an on/off settings row: a rounded track that takes the accent colour when on, and a
 * white thumb that slides across. It shows a state and is not focusable itself; the row is the control.
 */
public final class SwitchView extends View {

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG), thumb = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int off, on;
    private float at;   // 0 off, 1 on
    private ValueAnimator slide;

    public SwitchView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        off = 0x40FFFFFF;
        on = context.getColor(R.color.accent);
        thumb.setColor(0xFFFFFFFF);
    }

    /** Sets the state; animated, the thumb slides there from where it is. */
    void setChecked(boolean checked, boolean animated) {
        if (slide != null) slide.cancel();
        float to = checked ? 1f : 0f;
        if (!animated) {
            at = to;
            invalidate();
            return;
        }
        slide = ValueAnimator.ofFloat(at, to);
        slide.setDuration(Motion.STANDARD);
        slide.setInterpolator(Motion.MOVE);
        slide.addUpdateListener(a -> {
            at = (float) a.getAnimatedValue();
            invalidate();
        });
        slide.start();
    }

    /** Where the thumb starts from when the next setChecked is animated. */
    void startFrom(boolean checked) {
        at = checked ? 1f : 0f;
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), r = h / 2f, inset = h * 0.14f;
        track.setColor(blend(off, on, at));
        c.drawRoundRect(0, 0, w, h, r, r, track);
        float cx = r + (w - h) * at;
        c.drawCircle(cx, r, r - inset, thumb);
    }

    private static int blend(int a, int b, float t) {
        int out = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            int ca = (a >> shift) & 0xFF, cb = (b >> shift) & 0xFF;
            out |= (Math.round(ca + (cb - ca) * t) & 0xFF) << shift;
        }
        return out;
    }
}
