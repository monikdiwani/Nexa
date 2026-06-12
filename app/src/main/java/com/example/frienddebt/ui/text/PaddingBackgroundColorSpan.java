package com.example.frienddebt.ui.text;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.Spanned;
import android.text.style.LineBackgroundSpan;

public class PaddingBackgroundColorSpan implements LineBackgroundSpan {
    private final int backgroundColor;
    private final int padding;
    private final float radius;

    public PaddingBackgroundColorSpan(int backgroundColor, int padding, float radius) {
        this.backgroundColor = backgroundColor;
        this.padding = padding;
        this.radius = radius;
    }

    public int getBackgroundColor() {
        return backgroundColor;
    }

    @Override
    public void drawBackground(Canvas canvas, Paint paint, int left, int right, int top, int baseline, int bottom,
                               CharSequence text, int start, int end, int lineNumber) {
        if (!(text instanceof Spanned)) return;
        Spanned spanned = (Spanned) text;
        int spanStart = spanned.getSpanStart(this);
        int spanEnd = spanned.getSpanEnd(this);

        if (spanStart >= end || spanEnd <= start) return;

        int drawStart = Math.max(spanStart, start);
        int drawEnd = Math.min(spanEnd, end);

        // Measure text before our span on this line to find the X offset
        float startX = left + paint.measureText(text, start, drawStart);
        // Measure the width of the spanned text on this line
        float width = paint.measureText(text, drawStart, drawEnd);

        RectF rect = new RectF(startX - padding, top, startX + width + padding, bottom);
        
        int oldColor = paint.getColor();
        paint.setColor(backgroundColor);
        canvas.drawRoundRect(rect, radius, radius, paint);
        paint.setColor(oldColor);
    }
}
