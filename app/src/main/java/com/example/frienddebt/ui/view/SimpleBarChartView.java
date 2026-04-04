package com.example.frienddebt.ui.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.example.frienddebt.R;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SimpleBarChartView extends View {

    private Paint barPaint;
    private Paint textPaint;
    private Map<String, Double> data = new LinkedHashMap<>();
    private double maxValue = 0;

    public SimpleBarChartView(Context context) {
        super(context);
        init();
    }

    public SimpleBarChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        barPaint.setStyle(Paint.Style.FILL);
        barPaint.setColor(getResources().getColor(R.color.primary));

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(getResources().getColor(R.color.text_secondary));
        textPaint.setTextSize(32f);
        textPaint.setTextAlign(Paint.Align.CENTER);
    }

    public void setData(Map<String, Double> data) {
        this.data = data != null ? data : new LinkedHashMap<>();
        this.maxValue = 0;
        for (Double value : this.data.values()) {
            if (value > maxValue) {
                maxValue = value;
            }
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (data.isEmpty()) {
            textPaint.setTextSize(40f);
            canvas.drawText("No data to display", getWidth() / 2f, getHeight() / 2f, textPaint);
            return;
        }

        int count = data.size();
        float margin = 40f;
        float chartWidth = getWidth() - (margin * 2);
        float chartHeight = getHeight() - (margin * 4);

        float barWidth = (chartWidth / count) * 0.6f;
        float spacing = (chartWidth / count) * 0.4f;

        List<String> keys = new ArrayList<>(data.keySet());

        for (int i = 0; i < count; i++) {
            String label = keys.get(i);
            double val = data.get(label);

            float left = margin + (i * (barWidth + spacing)) + (spacing / 2);
            float right = left + barWidth;

            float barHeight = maxValue > 0 ? (float) (val / maxValue * chartHeight) : 0;
            float top = getHeight() - margin - 40f - barHeight;
            float bottom = getHeight() - margin - 40f;

            RectF rect = new RectF(left, top, right, bottom);
            canvas.drawRoundRect(rect, 12f, 12f, barPaint);

            textPaint.setTextSize(30f);
            textPaint.setColor(getResources().getColor(R.color.text_secondary));
            canvas.drawText(label, left + (barWidth / 2f), bottom + 40f, textPaint);

            textPaint.setColor(getResources().getColor(R.color.text_primary));
            canvas.drawText(String.format(Locale.getDefault(), "₹%.0f", val), left + (barWidth / 2f), top - 15f, textPaint);
        }
    }
}
