package com.example.chatapp;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayDeque;
import java.util.Iterator;

/**
 * Loudness bars, as in Messenger and WhatsApp. Two uses:
 *  - a recorded message: setBars() once, then setProgress() while it plays: the bars already played are drawn solid
 *  - the recorder: push() adds a bar on the right every few milliseconds and the older ones scroll to the left
 */
public class WaveformView extends View {
    private final Paint played = new Paint(Paint.ANTI_ALIAS_FLAG), idle = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bar = new RectF();
    private final float barWidth, gap, minHeight;
    private float[] bars = new float[0];
    private float progress = -1;   // -1: not played
    private boolean live;
    private final ArrayDeque<Float> recent = new ArrayDeque<>();

    public WaveformView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        float density = context.getResources().getDisplayMetrics().density;
        barWidth = 3 * density;
        gap = 2 * density;
        minHeight = 3 * density;
        int color = 0xFF808080;
        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.WaveformView);
            color = a.getColor(R.styleable.WaveformView_barColor, color);
            a.recycle();
        }
        setColor(color);
    }

    public void setColor(int color) {
        played.setColor(color);
        idle.setColor(color);
        idle.setAlpha(110);   // the part still to play: the same colour, faded
        invalidate();
    }

    /** A finished message: `bars` are heights from 0 to 1. */
    public void setBars(float[] bars) {
        this.bars = bars == null ? new float[0] : bars;
        live = false;
        invalidate();
    }

    /** How much has been played, 0..1; a negative value shows no progress. */
    public void setProgress(float progress) {
        if (this.progress == progress) return;
        this.progress = progress;
        invalidate();
    }

    /** Recorder mode: a new bar (0..1) enters on the right. */
    public void push(float level) {
        live = true;
        recent.addLast(Math.min(1f, Math.max(0f, level)));
        int capacity = Math.max(1, (int) (getWidth() / (barWidth + gap)));
        while (recent.size() > capacity) recent.removeFirst();
        invalidate();
    }

    public void clearLive() {
        recent.clear();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float height = getHeight(), centre = height / 2f, radius = barWidth / 2f;
        if (live) {
            float x = getWidth();
            Iterator<Float> it = recent.descendingIterator();   // newest first, drawn from the right edge
            while (it.hasNext() && x - barWidth >= 0) {
                x -= barWidth;
                drawBar(canvas, x, it.next(), centre, height, radius, played);
                x -= gap;
            }
            return;
        }
        int n = bars.length;
        if (n == 0) return;
        float slot = getWidth() / (float) n;
        float width = Math.max(1f, slot - gap);
        for (int i = 0; i < n; i++) {
            boolean done = progress >= 0 && (i + 0.5f) / n <= progress;
            drawBar(canvas, i * slot, bars[i], centre, height, Math.min(radius, width / 2f), done ? played : idle, width);
        }
    }

    private void drawBar(Canvas canvas, float left, float level, float centre, float height, float radius, Paint paint) {
        drawBar(canvas, left, level, centre, height, radius, paint, barWidth);
    }

    private void drawBar(Canvas canvas, float left, float level, float centre, float height, float radius, Paint paint, float width) {
        float h = Math.max(minHeight, level * height);
        bar.set(left, centre - h / 2f, left + width, centre + h / 2f);
        canvas.drawRoundRect(bar, radius, radius, paint);
    }
}
