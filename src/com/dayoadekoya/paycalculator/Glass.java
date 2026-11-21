package com.dayoadekoya.paycalculator;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;

import java.util.Locale;

/**
 * The liquid-glass language shared by One UI 9, ColorOS 17 and HyperOS 3:
 * soft "aurora" colour fields behind content, frosted-glass panels with a light rim,
 * luminous gradient hero cards and big confident numerals.
 */
public final class Glass {
    private Glass() {}

    /** Hue-rotate a colour (degrees), keeping saturation/value. */
    public static int hueShift(int color, float deg) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[0] = (hsv[0] + deg + 360f) % 360f;
        return Color.HSVToColor(Color.alpha(color), hsv);
    }

    public static int secondary() { return Theme.oled ? 0xFF6B6B78 : hueShift(Theme.accent, 38); }

    // ------------------------------------------------------------------ Aurora background

    public static int tertiary() { return Theme.oled ? 0xFF55555F : hueShift(Theme.accent, -46); }

    /** Page background: neutral canvas with a soft multi-hue light field (ColorOS 17 / HyperOS 3 style). */
    public static final class Aurora extends Drawable {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        @Override public void draw(Canvas c) {
            // iOS: a plain grouped background, no decorative light field
            c.drawColor(Theme.bg);
        }

        private void glow(Canvas c, float x, float y, float r, int color) {
            p.setShader(new RadialGradient(x, y, Math.max(1, r), color, color & 0x00FFFFFF, Shader.TileMode.CLAMP));
            c.drawCircle(x, y, r, p);
        }

        @Override public void setAlpha(int a) {}
        @Override public void setColorFilter(ColorFilter f) {}
        @Override public int getOpacity() { return PixelFormat.OPAQUE; }
    }

    // ------------------------------------------------------------------ Liquid glass surfaces

    public static int glassFill() {
        if (Theme.oled) return 0xFF141417;
        return Theme.dark ? 0xE6121215 : 0xC4FFFFFF;
    }

    public static int glassRim() { return Theme.dark ? 0x33FFFFFF : 0xFFFFFFFF; }

    /**
     * A glass surface: vertical fill, a specular sheen across the top, and a light-catching rim
     * that is bright on the top-left edge and fades toward the bottom-right (liquid glass edge).
     */
    public static class Shape extends Drawable {
        int top, bottom, rimA, rimB, hairline;
        float radius, sheen;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final RectF r = new RectF();

        public Shape(int top, int bottom, float radiusDp, float sheen, int rimA, int rimB, int hairline) {
            this.top = top; this.bottom = bottom; this.radius = Ui.dp(radiusDp); this.sheen = sheen;
            this.rimA = rimA; this.rimB = rimB; this.hairline = hairline;
        }

        float rad() { Rect b = getBounds(); return Math.min(radius, Math.min(b.width(), b.height()) / 2f); }

        @Override public void draw(Canvas c) {
            Rect b = getBounds();
            if (b.isEmpty()) return;
            r.set(b);
            float rd = rad();
            p.setStyle(Paint.Style.FILL);
            p.setShader(new LinearGradient(0, b.top, 0, b.bottom, top, bottom, Shader.TileMode.CLAMP));
            c.drawRoundRect(r, rd, rd, p);
            drawSheen(c, b, rd);
            drawRim(c, b, rd);
        }

        void drawSheen(Canvas c, Rect b, float rd) {
            if (sheen <= 0) return;
            p.setStyle(Paint.Style.FILL);
            int hi = Theme.withAlpha(0xFFFFFFFF, sheen);
            p.setShader(new LinearGradient(0, b.top, 0, b.top + Math.max(1, b.height() * 0.55f), hi, 0x00FFFFFF, Shader.TileMode.CLAMP));
            c.drawRoundRect(r, rd, rd, p);
        }

        void drawRim(Canvas c, Rect b, float rd) {
            float sw = Math.max(1f, Ui.dp(1f));
            p.setStyle(Paint.Style.STROKE);
            if ((hairline >>> 24) != 0) {
                p.setShader(null);
                p.setColor(hairline);
                p.setStrokeWidth(sw);
                RectF o = new RectF(r);
                o.inset(sw / 2, sw / 2);
                c.drawRoundRect(o, rd, rd, p);
            }
            if ((rimA >>> 24) != 0 || (rimB >>> 24) != 0) {
                p.setStrokeWidth(sw * 1.1f);
                p.setShader(new LinearGradient(b.left, b.top, b.left + b.width() * 0.6f, b.bottom, new int[]{rimA, rimB, Theme.withAlpha(rimA, (rimA >>> 24) / 255f * 0.55f)},
                        new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP));
                RectF in = new RectF(r);
                float ins = (hairline >>> 24) != 0 ? sw * 1.5f : sw * 0.6f;
                in.inset(ins, ins);
                c.drawRoundRect(in, Math.max(0, rd - ins), Math.max(0, rd - ins), p);
            }
            p.setShader(null);
        }

        @Override public void getOutline(Outline o) { o.setRoundRect(getBounds(), rad()); }
        @Override public void setAlpha(int a) {}
        @Override public void setColorFilter(ColorFilter f) {}
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    private static int hair() { return Theme.dark ? 0x00000000 : 0x12000000; }

    /** iOS corner radii: inset-grouped cells ~16dp; pills stay pills. */
    static float ios(float radiusDp) { return radiusDp >= 40 ? radiusDp : Math.min(radiusDp, 16); }

    /** iOS inset-grouped cell: solid secondarySystemGroupedBackground, no border. */
    public static Drawable panel(float radiusDp) {
        return new Shape(Theme.surface, Theme.surface, ios(radiusDp), 0f, 0, 0, 0);
    }

    /** Raised control (segmented thumb, header buttons). */
    public static Drawable lens(float radiusDp) {
        int c = Theme.dark ? 0xFF636366 : 0xFFFFFFFF;
        return new Shape(c, c, radiusDp, 0f, 0, 0, Theme.dark ? 0 : 0x0F000000);
    }

    /** Flat tinted fill (chips, tonal/"gray" buttons). */
    public static Drawable tinted(int tint, float radiusDp) {
        int c = Theme.withAlpha(tint, Theme.dark ? 0.20f : 0.12f);
        return new Shape(c, c, ios(radiusDp), 0f, 0, 0, 0);
    }

    /** Filled iOS button. */
    public static Drawable gloss(int color, float radiusDp) {
        return new Shape(color, color, ios(radiusDp), 0f, 0, 0, 0);
    }

    // ------------------------------------------------------------------ Live backdrop blur

    /** Set while a Frost is capturing what's behind it, so frosted views don't paint into each other. */
    static boolean capturing;

    /**
     * A container whose background is a live, blurred and colour-boosted copy of whatever is
     * behind it — the liquid-glass material for the tab bar and collapsing headers.
     */
    public static class Frost extends android.widget.FrameLayout {
        private View target;
        private float radiusDp;
        private boolean failed, force = true;
        private int[] prev, blurred;
        private android.graphics.Bitmap bmp;
        private Canvas bc;
        private int[] px;
        private final int[] loc = new int[2], tloc = new int[2];
        private final Paint bp = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        private Shape tint;
        private float bgAlpha = 1f;
        private static final int SCALE = 8;
        private final Path clip = new Path();

        private final android.view.ViewTreeObserver.OnPreDrawListener pre = new android.view.ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                // Re-capture every frame, but only repaint when the backdrop actually changed —
                // so an idle screen settles instead of redrawing forever.
                if (capture()) invalidate();
                return true;
            }
        };

        public Frost(android.content.Context c, float radiusDp, boolean strong) {
            super(c);
            this.radiusDp = radiusDp;
            setWillNotDraw(false);
            android.graphics.ColorMatrix cm = new android.graphics.ColorMatrix();
            cm.setSaturation(1.6f);
            bp.setColorFilter(new android.graphics.ColorMatrixColorFilter(cm));
            int a = Theme.dark ? 0xD1 : 0xD9;
            int base = Theme.dark ? 0x161618 : 0xF9F9F9;
            if (Theme.oled) { a = 0xD9; base = 0x101012; }
            if (strong) a = Math.min(0xE6, a + 0x18);
            tint = new Shape((a << 24) | base, ((a - 0x10) << 24) | base, radiusDp, Theme.dark ? 0.05f : 0.3f,
                    Theme.dark ? 0x4DFFFFFF : 0xFFFFFFFF, Theme.dark ? 0x0FFFFFFF : 0x80FFFFFF, Theme.dark ? 0 : 0x14000000);
            setClipToOutline(true);
            setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override public void getOutline(View v, Outline o) {
                    o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), Math.min(Ui.dp(Frost.this.radiusDp), Math.min(v.getWidth(), v.getHeight()) / 2f));
                }
            });
        }

        public void setTarget(View t) { target = t; }

        /** Fades only the glass material, leaving child views opaque. */
        public void setBackdropAlpha(float a) {
            a = Math.max(0f, Math.min(1f, a));
            if (Math.abs(a - bgAlpha) < 0.001f) return;
            boolean wake = bgAlpha < 0.01f && a >= 0.01f;
            bgAlpha = a;
            if (wake) { force = true; capture(); }
            invalidate();
        }

        /** Edge-to-edge material: no rim, just a hairline along the bottom. */
        private boolean topEdge;

        /** Bottom bar variant: hairline along the top instead of the bottom. */
        public Frost plainTop() { plain(); topEdge = true; return this; }

        public Frost plain() {
            int a = Theme.dark ? 0xD1 : 0xD9, base = Theme.dark ? 0x161618 : 0xF9F9F9;
            if (Theme.oled) { a = 0xE6; base = 0x000000; }
            tint = new Shape((a << 24) | base, (a << 24) | base, 0, 0f, 0, 0, 0);
            radiusDp = 0;
            plainEdge = true;
            return this;
        }
        private boolean plainEdge;

        public void setRadius(float dp) { radiusDp = dp; tint.radius = Ui.dp(dp); invalidateOutline(); invalidate(); }

        /** Square corners when it spans an edge (collapsed header). */
        public void setEdges(boolean square) { setRadius(square ? 0 : radiusDp); }

        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            getViewTreeObserver().addOnPreDrawListener(pre);
        }

        @Override protected void onDetachedFromWindow() {
            getViewTreeObserver().removeOnPreDrawListener(pre);
            super.onDetachedFromWindow();
        }

        private boolean capture() {
            if (failed || target == null || capturing || getWidth() <= 0 || getHeight() <= 0 || !isShown() || getAlpha() < 0.01f || bgAlpha < 0.01f) return false;
            try {
                int w = Math.max(1, getWidth() / SCALE), h = Math.max(1, getHeight() / SCALE);
                if (bmp == null || bmp.getWidth() != w || bmp.getHeight() != h) {
                    bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888);
                    bc = new Canvas(bmp);
                    px = new int[w * h];
                    prev = new int[w * h];
                    force = true;
                }
                bmp.eraseColor(Theme.bg);
                getLocationInWindow(loc);
                target.getLocationInWindow(tloc);
                bc.save();
                bc.scale(1f / SCALE, 1f / SCALE);
                bc.translate(tloc[0] - loc[0], tloc[1] - loc[1]);
                capturing = true;
                try { target.draw(bc); } finally { capturing = false; bc.restore(); }
                bmp.getPixels(px, 0, w, 0, 0, w, h);
                if (!force && java.util.Arrays.equals(px, prev)) {
                    bmp.setPixels(blurred, 0, w, 0, 0, w, h);
                    return false;
                }
                force = false;
                System.arraycopy(px, 0, prev, 0, px.length);
                boxBlur(px, w, h, 3);
                boxBlur(px, w, h, 3);
                if (blurred == null || blurred.length != px.length) blurred = new int[px.length];
                System.arraycopy(px, 0, blurred, 0, px.length);
                bmp.setPixels(px, 0, w, 0, 0, w, h);
                return true;
            } catch (Throwable t) {
                failed = true;
                bmp = null;
                return false;
            }
        }

        @Override public void draw(Canvas c) {
            if (capturing) return;   // don't paint ourselves into another glass capture
            super.draw(c);
        }

        @Override protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            if (bgAlpha < 0.01f) return;
            int layer = bgAlpha < 0.999f ? c.saveLayerAlpha(0, 0, w, h, Math.round(bgAlpha * 255)) : -1;
            if (bmp != null && !failed) {
                c.save();
                float rd = Math.min(Ui.dp(radiusDp), Math.min(w, h) / 2f);
                clip.reset();
                clip.addRoundRect(new RectF(0, 0, w, h), rd, rd, Path.Direction.CW);
                c.clipPath(clip);
                c.drawBitmap(bmp, null, new Rect(0, 0, w, h), bp);
                c.restore();
            }
            tint.setBounds(0, 0, w, h);
            tint.draw(c);
            if (plainEdge) {
                bp.setColorFilter(null);
                Paint d = new Paint();
                d.setColor(Theme.divider);
                int hl = Math.max(1, Ui.dp(0.5f));
                if (topEdge) c.drawRect(0, 0, w, hl, d); else c.drawRect(0, h - hl, w, h, d);
                android.graphics.ColorMatrix cm = new android.graphics.ColorMatrix();
                cm.setSaturation(1.6f);
                bp.setColorFilter(new android.graphics.ColorMatrixColorFilter(cm));
            }
            if (layer >= 0) c.restoreToCount(layer);
        }
    }

    /** Separable box blur on ARGB pixels (two passes approximate a Gaussian). */
    static void boxBlur(int[] px, int w, int h, int r) {
        int[] tmp = new int[Math.max(w, h)];
        int div = 2 * r + 1;
        for (int y = 0; y < h; y++) {
            int off = y * w, sr = 0, sg = 0, sb = 0;
            for (int i = -r; i <= r; i++) { int c = px[off + Math.max(0, Math.min(w - 1, i))]; sr += (c >> 16) & 255; sg += (c >> 8) & 255; sb += c & 255; }
            for (int x = 0; x < w; x++) {
                tmp[x] = 0xFF000000 | ((sr / div) << 16) | ((sg / div) << 8) | (sb / div);
                int cOut = px[off + Math.max(0, x - r)], cIn = px[off + Math.min(w - 1, x + r + 1)];
                sr += ((cIn >> 16) & 255) - ((cOut >> 16) & 255);
                sg += ((cIn >> 8) & 255) - ((cOut >> 8) & 255);
                sb += (cIn & 255) - (cOut & 255);
            }
            System.arraycopy(tmp, 0, px, off, w);
        }
        for (int x = 0; x < w; x++) {
            int sr = 0, sg = 0, sb = 0;
            for (int i = -r; i <= r; i++) { int c = px[Math.max(0, Math.min(h - 1, i)) * w + x]; sr += (c >> 16) & 255; sg += (c >> 8) & 255; sb += c & 255; }
            for (int y = 0; y < h; y++) {
                tmp[y] = 0xFF000000 | ((sr / div) << 16) | ((sg / div) << 8) | (sb / div);
                int cOut = px[Math.max(0, y - r) * w + x], cIn = px[Math.min(h - 1, y + r + 1) * w + x];
                sr += ((cIn >> 16) & 255) - ((cOut >> 16) & 255);
                sg += ((cIn >> 8) & 255) - ((cOut >> 8) & 255);
                sb += (cIn & 255) - (cOut & 255);
            }
            for (int y = 0; y < h; y++) px[y * w + x] = tmp[y];
        }
    }

    // ------------------------------------------------------------------ Luminous hero

    /** Hero card: accent → secondary gradient with luminous highlights. */
    public static final class Hero extends Drawable {
        private final float radius;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        public Hero(float radiusDp) { radius = Ui.dp(Math.min(radiusDp, 22)); }

        @Override public void draw(Canvas c) {
            Rect b = getBounds();
            RectF r = new RectF(b);
            Path clip = new Path();
            clip.addRoundRect(r, radius, radius, Path.Direction.CW);
            c.save();
            c.clipPath(clip);
            int start = Theme.heroStart(), end = Theme.heroEnd();
            int mid = Theme.oled ? end : Theme.blend(start, Theme.heroSecondary(), 0.45f);
            p.setShader(new LinearGradient(b.left, b.top, b.right, b.bottom, new int[]{start, mid, end}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
            c.drawRect(r, p);
            float w = b.width();
            // one soft top-left light, like iOS Wallet / Health summary cards
            p.setShader(new RadialGradient(b.left + w * 0.15f, b.top, Math.max(1, w * 0.8f), 0x24FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
            c.drawRect(r, p);
            c.restore();
        }

        @Override public void getOutline(Outline o) { o.setRoundRect(getBounds(), radius); }
        @Override public void setAlpha(int a) {}
        @Override public void setColorFilter(ColorFilter f) {}
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    // ------------------------------------------------------------------ Numerals

    /** "£1,234.56" with a smaller currency sign and softer decimals, like HyperOS/ColorOS big numbers. */
    public static CharSequence bigMoney(double v, int textColor) {
        String sym = Store.currency();
        String num = String.format(Locale.UK, "%,.2f", Math.abs(v));
        if (Ui.masked()) num = "••••.••";
        int dot = num.lastIndexOf('.');
        SpannableStringBuilder sb = new SpannableStringBuilder();
        if (v < -0.004) sb.append("-");
        int s0 = sb.length();
        sb.append(sym);
        sb.setSpan(new RelativeSizeSpan(0.62f), s0, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new ForegroundColorSpan(Theme.withAlpha(textColor, 0.8f)), s0, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.append(num.substring(0, dot));
        int d0 = sb.length();
        sb.append(num.substring(dot));
        sb.setSpan(new RelativeSizeSpan(0.6f), d0, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new ForegroundColorSpan(Theme.withAlpha(textColor, 0.72f)), d0, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return sb;
    }
}
