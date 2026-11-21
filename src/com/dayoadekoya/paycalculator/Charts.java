package com.dayoadekoya.paycalculator;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;

/** Hand-drawn charts (no external libraries): ring, sparkline, bars, donut, stacked bar. */
public final class Charts {
    private Charts() {}

    public interface Scrub { void on(int index); }

    private static Paint paint() {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        return p;
    }

    /** Animates a 0→1 "reveal" progress on the given view. */
    private static ValueAnimator reveal(final View v, final float[] holder) {
        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(900);
        a.setInterpolator(Ui.EMPHASIZED);
        a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator an) {
                holder[0] = (Float) an.getAnimatedValue();
                v.invalidate();
            }
        });
        return a;
    }

    // ---------------------------------------------------------------- Ring

    public static final class Ring extends View {
        private float progress, shown;
        private final float[] anim = {0};
        private final Paint p = paint();
        public int track = 0x40FFFFFF, fill = 0xFFFFFFFF;
        public float strokeDp = 9;

        public Ring(Context c) { super(c); }

        public void set(float value) {
            progress = Math.max(0, Math.min(1, value));
            ValueAnimator a = reveal(this, anim);
            a.setInterpolator(Ui.SPRING);
            a.setDuration(1100);
            a.start();
        }

        @Override protected void onDraw(Canvas cv) {
            float s = Ui.dp(strokeDp);
            float w = getWidth(), h = getHeight();
            RectF r = new RectF(s / 2, s / 2, w - s / 2, h - s / 2);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(s);
            p.setColor(track);
            cv.drawArc(r, 0, 360, false, p);
            shown = progress * anim[0];
            if (shown > 0.001f) {
                p.setColor(fill);
                cv.drawArc(r, -90, 360 * Math.min(1.04f, shown), false, p);
            }
        }
    }

    // ---------------------------------------------------------------- Sparkline

    public static final class Spark extends View {
        private double[] values = new double[0];
        private final Paint line = paint(), area = paint(), dot = paint(), rule = paint();
        private final float[] anim = {0};
        public int color = 0xFFFFFFFF;
        private int selected = -1;
        private Scrub scrub;

        public Spark(Context c) { super(c); }

        public void set(double[] v, Scrub s) {
            values = v;
            scrub = s;
            reveal(this, anim).start();
        }

        private float x(int i) {
            float pad = Ui.dp(6);
            return values.length <= 1 ? getWidth() / 2f : pad + (getWidth() - 2 * pad) * i / (float) (values.length - 1);
        }

        private float y(double v, double min, double max) {
            float pad = Ui.dp(6);
            double span = max - min;
            if (span <= 0) span = 1;
            return (float) (getHeight() - pad - (getHeight() - 2 * pad) * (v - min) / span);
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override public boolean onTouchEvent(MotionEvent e) {
            if (values.length < 2) return false;
            int act = e.getActionMasked();
            if (act == MotionEvent.ACTION_DOWN || act == MotionEvent.ACTION_MOVE) {
                getParent().requestDisallowInterceptTouchEvent(true);
                float fx = e.getX();
                int best = 0; float bd = Float.MAX_VALUE;
                for (int i = 0; i < values.length; i++) {
                    float d = Math.abs(x(i) - fx);
                    if (d < bd) { bd = d; best = i; }
                }
                if (best != selected) { selected = best; Ui.haptic(this); if (scrub != null) scrub.on(best); }
                invalidate();
                return true;
            }
            if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) {
                selected = -1;
                if (scrub != null) scrub.on(-1);
                invalidate();
                return true;
            }
            return true;
        }

        @Override protected void onDraw(Canvas cv) {
            if (values.length < 2) return;
            double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
            for (double v : values) { min = Math.min(min, v); max = Math.max(max, v); }
            double pad = (max - min) * 0.12;
            min -= pad; max += pad;
            Path p = new Path();
            for (int i = 0; i < values.length; i++) {
                float px = x(i), py = y(values[i], min, max);
                if (i == 0) p.moveTo(px, py);
                else {
                    float cx = (x(i - 1) + px) / 2;
                    p.cubicTo(cx, y(values[i - 1], min, max), cx, py, px, py);
                }
            }
            cv.save();
            cv.clipRect(0, 0, getWidth() * anim[0], getHeight());
            Path a = new Path(p);
            a.lineTo(x(values.length - 1), getHeight());
            a.lineTo(x(0), getHeight());
            a.close();
            area.setShader(new LinearGradient(0, 0, 0, getHeight(), Theme.withAlpha(color, 0.32f), Theme.withAlpha(color, 0.0f), Shader.TileMode.CLAMP));
            cv.drawPath(a, area);
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeWidth(Ui.dp(2.4f));
            line.setColor(color);
            cv.drawPath(p, line);
            cv.restore();
            if (selected >= 0) {
                float sx = x(selected), sy = y(values[selected], min, max);
                rule.setColor(Theme.withAlpha(color, 0.5f));
                rule.setStrokeWidth(Ui.dp(1));
                cv.drawLine(sx, 0, sx, getHeight(), rule);
                dot.setColor(color);
                cv.drawCircle(sx, sy, Ui.dp(6), dot);
            }
        }
    }

    // ---------------------------------------------------------------- Bars

    public static final class Bars extends View {
        private double[] values = new double[0];
        private String[] labels = new String[0];
        private final Paint bar = paint(), txt = paint(), avgP = paint();
        private final float[] anim = {0};
        public int color = 0xFF3E7BFA;
        public double average = -1;
        private int selected = -1;
        private Scrub scrub;

        public Bars(Context c) { super(c); }

        public void set(double[] v, String[] l, Scrub s) {
            values = v; labels = l; scrub = s; selected = -1;
            reveal(this, anim).start();
        }

        private float slot() { return values.length == 0 ? 0 : getWidth() / (float) values.length; }

        @SuppressLint("ClickableViewAccessibility")
        @Override public boolean onTouchEvent(MotionEvent e) {
            if (values.length == 0) return false;
            int act = e.getActionMasked();
            if (act == MotionEvent.ACTION_DOWN || act == MotionEvent.ACTION_MOVE) {
                getParent().requestDisallowInterceptTouchEvent(true);
                int i = (int) Math.max(0, Math.min(values.length - 1, e.getX() / slot()));
                if (i != selected) { selected = i; Ui.haptic(this); if (scrub != null) scrub.on(i); invalidate(); }
                return true;
            }
            return true;
        }

        @Override protected void onDraw(Canvas cv) {
            if (values.length == 0) return;
            double max = 0;
            for (double v : values) max = Math.max(max, v);
            if (max <= 0) max = 1;
            float labelH = Ui.dp(22);
            float h = getHeight() - labelH;
            float slot = slot();
            float bw = Math.min(Ui.dp(28), slot * 0.62f);
            txt.setTextSize(Ui.dp(10.5f));
            txt.setTextAlign(Paint.Align.CENTER);
            txt.setTypeface(Ui.font(600));
            for (int i = 0; i < values.length; i++) {
                float cx = slot * i + slot / 2;
                float bh = (float) (Math.max(0, values[i]) / max * (h - Ui.dp(8))) * anim[0];
                bh = Math.max(bh, Ui.dp(4));
                boolean sel = selected == -1 || selected == i;
                bar.setColor(sel ? color : Theme.withAlpha(color, 0.35f));
                RectF r = new RectF(cx - bw / 2, h - bh, cx + bw / 2, h);
                cv.drawRoundRect(r, bw / 2.4f, bw / 2.4f, bar);
                txt.setColor(selected == i ? Theme.text : Theme.text2);
                if (labels.length > i && (values.length <= 8 || i % 2 == (values.length - 1) % 2)) {
                    cv.drawText(labels[i], cx, getHeight() - Ui.dp(5), txt);
                }
            }
            if (average > 0) {
                float ay = (float) (h - average / max * (h - Ui.dp(8)) * anim[0]);
                avgP.setColor(Theme.withAlpha(Theme.text2, 0.7f));
                avgP.setStrokeWidth(Ui.dp(1.2f));
                avgP.setPathEffect(new android.graphics.DashPathEffect(new float[]{Ui.dp(4), Ui.dp(4)}, 0));
                cv.drawLine(0, ay, getWidth(), ay, avgP);
            }
        }
    }

    // ---------------------------------------------------------------- Donut

    public static final class Donut extends View {
        private double[] values = new double[0];
        private int[] colors = new int[0];
        private final Paint p = paint();
        private final float[] anim = {0};
        public float thicknessDp = 22;

        public Donut(Context c) { super(c); }

        public void set(double[] v, int[] cols) {
            values = v; colors = cols;
            reveal(this, anim).start();
        }

        @Override protected void onDraw(Canvas cv) {
            double total = 0;
            for (double v : values) total += Math.max(0, v);
            float s = Ui.dp(thicknessDp);
            float size = Math.min(getWidth(), getHeight());
            float l = (getWidth() - size) / 2 + s / 2, t = (getHeight() - size) / 2 + s / 2;
            RectF r = new RectF(l, t, l + size - s, t + size - s);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(s);
            p.setStrokeCap(Paint.Cap.BUTT);
            if (total <= 0) {
                p.setColor(Theme.field);
                cv.drawArc(r, 0, 360, false, p);
                return;
            }
            float start = -90;
            float gap = values.length > 1 ? 2.5f : 0;
            float sweepAll = 360 * anim[0];
            for (int i = 0; i < values.length; i++) {
                float sweep = (float) (Math.max(0, values[i]) / total * sweepAll);
                if (sweep <= 0) continue;
                p.setColor(colors[i % colors.length]);
                cv.drawArc(r, start + gap / 2, Math.max(0.5f, sweep - gap), false, p);
                start += sweep;
            }
        }
    }

    // ---------------------------------------------------------------- Stacked horizontal bar

    public static final class Stack extends View {
        private double[] values = new double[0];
        private int[] colors = new int[0];
        private final Paint p = paint();
        private final float[] anim = {0};

        public Stack(Context c) { super(c); }

        public void set(double[] v, int[] cols) {
            values = v; colors = cols;
            reveal(this, anim).start();
        }

        @Override protected void onDraw(Canvas cv) {
            double total = 0;
            for (double v : values) total += Math.max(0, v);
            float h = getHeight(), w = getWidth() * anim[0];
            float rad = h / 2;
            Path clip = new Path();
            clip.addRoundRect(new RectF(0, 0, getWidth(), h), rad, rad, Path.Direction.CW);
            cv.save();
            cv.clipPath(clip);
            p.setColor(Theme.field);
            cv.drawRect(0, 0, getWidth(), h, p);
            if (total > 0) {
                float x = 0;
                float gap = Ui.dp(2);
                for (int i = 0; i < values.length; i++) {
                    float seg = (float) (Math.max(0, values[i]) / total * w);
                    if (seg <= 0) continue;
                    p.setColor(colors[i % colors.length]);
                    cv.drawRect(x, 0, x + Math.max(0, seg - gap), h, p);
                    x += seg;
                }
            }
            cv.restore();
        }
    }

    // ---------------------------------------------------------------- Linear progress (pill)

    public static final class Progress extends View {
        private float value;
        private final float[] anim = {0};
        private final Paint p = paint();
        public int color = 0xFF3E7BFA, track = 0x22000000;

        public Progress(Context c) { super(c); }

        public void set(float v) { value = Math.max(0, Math.min(1, v)); reveal(this, anim).start(); }

        @Override protected void onDraw(Canvas cv) {
            float h = getHeight();
            p.setColor(track);
            cv.drawRoundRect(new RectF(0, 0, getWidth(), h), h / 2, h / 2, p);
            float w = Math.max(h, getWidth() * value * anim[0]);
            p.setColor(color);
            cv.drawRoundRect(new RectF(0, 0, w, h), h / 2, h / 2, p);
        }
    }
}
