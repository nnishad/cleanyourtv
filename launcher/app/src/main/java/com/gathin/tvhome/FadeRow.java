package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

/**
 * A row whose cards dissolve into whatever is behind them where the row is cut: a card that crosses the row's
 * left or right edge fades to nothing over the last stretch, so it reads as continuing and no shade is laid over
 * the wallpaper. An edge with a whole card at it, or with nothing, is left alone, so a focused card standing at
 * the edge is never faded. The fade costs one offscreen layer the size of the row, and only while a card is cut.
 */
public final class FadeRow extends RecyclerView {

    private final Paint left = new Paint(), right = new Paint();
    private final float length;
    private int madeFor = -1;

    public FadeRow(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        length = 56 * getResources().getDisplayMetrics().density;
        PorterDuffXfermode erase = new PorterDuffXfermode(PorterDuff.Mode.DST_OUT);
        left.setXfermode(erase);
        right.setXfermode(erase);
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas c) {
        int w = getWidth(), h = getHeight();
        boolean cutLeft = false, cutRight = false;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getLeft() < getPaddingLeft() - 1 && child.getRight() > 0) cutLeft = true;
            if (child.getRight() > w - getPaddingRight() + 1 && child.getLeft() < w) cutRight = true;
        }
        if (w == 0 || (!cutLeft && !cutRight)) {
            super.dispatchDraw(c);
            return;
        }
        if (madeFor != w) {
            int[] colors = {0xFF000000, 0x00000000};
            left.setShader(new LinearGradient(0, 0, length, 0, colors, null, Shader.TileMode.CLAMP));
            right.setShader(new LinearGradient(w, 0, w - length, 0, colors, null, Shader.TileMode.CLAMP));
            madeFor = w;
        }
        int layer = c.saveLayer(0, 0, w, h, null);
        super.dispatchDraw(c);
        if (cutLeft) c.drawRect(0, 0, length, h, left);
        if (cutRight) c.drawRect(w - length, 0, w, h, right);
        c.restoreToCount(layer);
    }
}
