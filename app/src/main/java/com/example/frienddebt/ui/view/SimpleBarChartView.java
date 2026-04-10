package com.example.frienddebt.ui.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
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
    private Paint labelPaint;
    private Paint valuePaint;
    private Paint gridPaint;
    private Paint avgLinePaint;
    private Map<String, Double> data = new LinkedHashMap<>();
    private double maxValue = 0;
    private double avgValue = 0;

    private int barColorStart;
    private int barColorEnd;
    private int barColorZero;

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

        labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        labelPaint.setColor(getResources().getColor(R.color.text_secondary));
        labelPaint.setTextSize(28f);
        labelPaint.setTextAlign(Paint.Align.CENTER);

        valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        valuePaint.setColor(getResources().getColor(R.color.text_primary));
        valuePaint.setTextSize(26f);
        valuePaint.setTextAlign(Paint.Align.CENTER);
        valuePaint.setFakeBoldText(true);

        gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridPaint.setColor(getResources().getColor(R.color.divider));
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(1f);
        gridPaint.setPathEffect(new android.graphics.DashPathEffect(new float[]{8, 4}, 0));

        avgLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        avgLinePaint.setColor(getResources().getColor(R.color.accent_warning));
        avgLinePaint.setStyle(Paint.Style.STROKE);
        avgLinePaint.setStrokeWidth(3f);
        avgLinePaint.setPathEffect(new android.graphics.DashPathEffect(new float[]{12, 6}, 0));

        barColorStart = getResources().getColor(R.color.primary);
        barColorEnd = getResources().getColor(R.color.primary_dark);
        barColorZero = getResources().getColor(R.color.divider);
    }

    public void setData(Map<String, Double> data) {
        this.data = data != null ? data : new LinkedHashMap<>();
        this.maxValue = 0;
        double total = 0;
        int nonZeroCount = 0;
        for (Double value : this.data.values()) {
            if (value > maxValue) {
                maxValue = value;
            }
            total += value;
            if (value > 0) nonZeroCount++;
        }
        this.avgValue = this.data.size() > 0 ? total / this.data.size() : 0;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (data.isEmpty()) {
            labelPaint.setTextSize(36f);
            labelPaint.setColor(getResources().getColor(R.color.text_hint));
            canvas.drawText("No data to display", getWidth() / 2f, getHeight() / 2f, labelPaint);
            return;
        }

        int count = data.size();
        float leftMargin = 16f;
        float rightMargin = 16f;
        float topMargin = 32f;
        float bottomMargin = 56f;
        float labelAreaHeight = 44f;

        float chartLeft = leftMargin;
        float chartRight = getWidth() - rightMargin;
        float chartTop = topMargin;
        float chartBottom = getHeight() - bottomMargin - labelAreaHeight;
        float chartWidth = chartRight - chartLeft;
        float chartHeight = chartBottom - chartTop;

        // Draw horizontal grid lines (3 lines)
        for (int i = 1; i <= 3; i++) {
            float y = chartBottom - (chartHeight * i / 4f);
            canvas.drawLine(chartLeft, y, chartRight, y, gridPaint);
        }

        // Draw average line if there's data
        if (maxValue > 0 && avgValue > 0) {
            float avgY = chartBottom - (float) (avgValue / maxValue * chartHeight);
            canvas.drawLine(chartLeft, avgY, chartRight, avgY, avgLinePaint);

            // Draw "avg" label
            Paint avgLabelPaint = new Paint(labelPaint);
            avgLabelPaint.setColor(getResources().getColor(R.color.accent_warning));
            avgLabelPaint.setTextSize(22f);
            avgLabelPaint.setTextAlign(Paint.Align.LEFT);
            canvas.drawText("avg", chartRight - 40f, avgY - 6f, avgLabelPaint);
        }

        // Calculate bar geometry
        float totalBarArea = chartWidth / count;
        float barWidth = totalBarArea * 0.62f;
        float spacing = totalBarArea * 0.38f;

        List<String> keys = new ArrayList<>(data.keySet());

        for (int i = 0; i < count; i++) {
            String label = keys.get(i);
            double val = data.get(label);

            float centerX = chartLeft + (i * totalBarArea) + (totalBarArea / 2f);
            float left = centerX - (barWidth / 2f);
            float right = left + barWidth;

            float barHeight = maxValue > 0 ? (float) (val / maxValue * chartHeight) : 0;
            float top = chartBottom - barHeight;
            float bottom = chartBottom;

            // Draw bar with gradient
            if (val > 0) {
                LinearGradient gradient = new LinearGradient(
                        left, top, left, bottom,
                        barColorStart, barColorEnd,
                        Shader.TileMode.CLAMP
                );
                barPaint.setShader(gradient);
                barPaint.setAlpha(220);
            } else {
                barPaint.setShader(null);
                barPaint.setColor(barColorZero);
                barPaint.setAlpha(80);
                barHeight = 4f;
                top = bottom - barHeight;
            }

            float cornerRadius = Math.min(barWidth / 3f, 14f);
            RectF rect = new RectF(left, top, right, bottom);
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, barPaint);

            // Reset paint
            barPaint.setShader(null);
            barPaint.setAlpha(255);

            // Draw value on top (only if non-zero)
            if (val > 0) {
                valuePaint.setTextSize(count > 7 ? 22f : 26f);
                String valText;
                if (val >= 1000) {
                    valText = String.format(Locale.getDefault(), "₹%.0fk", val / 1000);
                } else {
                    valText = String.format(Locale.getDefault(), "₹%.0f", val);
                }
                canvas.drawText(valText, centerX, top - 8f, valuePaint);
            }

            // Draw label below
            labelPaint.setTextSize(count > 7 ? 22f : 28f);
            labelPaint.setColor(getResources().getColor(R.color.text_secondary));

            // For longer labels, truncate
            String displayLabel = label;
            if (displayLabel.length() > 10 && count > 5) {
                displayLabel = displayLabel.substring(0, 8) + "…";
            }

            canvas.drawText(displayLabel, centerX, chartBottom + labelAreaHeight - 8f, labelPaint);
        }
    }
}
