package com.kb1123.quietnotes;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.CheckBox;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * The user's look-and-feel choices (Settings → Theme & fonts) and the
 * colours derived from them. Stored on the phone only.
 */
final class Theme {
    private Theme() {}

    // ----- choices -----
    static final String[] MODES = {"system", "light", "sepia", "dark", "black"};
    static final String[] MODE_LABELS = {"Auto", "Light", "Sepia", "Dark", "Black"};

    static final String[] ACCENT_NAMES = {"Forest", "Ocean", "Plum", "Ember", "Rose", "Graphite"};
    private static final int[] ACCENT_LIGHT = {0xFF2F5D50, 0xFF2B6CB0, 0xFF7B4FA8, 0xFFB8561E, 0xFFB83266, 0xFF4A5560};
    private static final int[] ACCENT_DARK = {0xFF6FAE9B, 0xFF6FA8E8, 0xFFB490DD, 0xFFF0A060, 0xFFF080A8, 0xFFA8B2BC};

    /** Index 0 = neutral, 1 = tint of the accent colour, then fixed colours. */
    static final String[] HEADER_NAMES = {"Neutral", "Accent", "Sand", "Mint", "Sky", "Lavender", "Peach", "Blush"};
    private static final int[] HEADER_LIGHT = {0, 0, 0xFFF3E6C4, 0xFFD7EEDF, 0xFFD6E6F7, 0xFFE6DDF3, 0xFFF8DFCF, 0xFFF6D9E3};
    private static final int[] HEADER_DARK = {0, 0, 0xFF3A3220, 0xFF1F3328, 0xFF1E2C3D, 0xFF2D2640, 0xFF3D2A1F, 0xFF3D2430};

    static final String[] FONT_NAMES = {"Sans", "Serif", "Mono"};
    private static final Typeface[] FONT_FACES = {Typeface.SANS_SERIF, Typeface.SERIF, Typeface.MONOSPACE};
    private static final String[] FONT_CSS = {
            "system-ui, Roboto, sans-serif",
            "'Noto Serif', Georgia, serif",
            "'Roboto Mono', 'Droid Sans Mono', ui-monospace, monospace"};

    static final String[] SIZE_NAMES = {"S", "M", "L"};
    private static final int[] SIZES = {14, 16, 19};

    // ----- storage -----
    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("theme", Context.MODE_PRIVATE);
    }

    static String mode(Context c) { return prefs(c).getString("mode", "system"); }
    static int accent(Context c) { return clamp(prefs(c).getInt("accent", 0), ACCENT_NAMES.length); }
    static int header(Context c) { return clamp(prefs(c).getInt("header", 0), HEADER_NAMES.length); }
    static int font(Context c) { return clamp(prefs(c).getInt("font", 0), FONT_NAMES.length); }
    static int size(Context c) { return clamp(prefs(c).getInt("size", 1), SIZE_NAMES.length); }
    /** Changes every time a setting changes, so open screens know to refresh. */
    static int stamp(Context c) { return prefs(c).getInt("stamp", 0); }

    static void setMode(Context c, String m) { prefs(c).edit().putString("mode", m).putInt("stamp", stamp(c) + 1).apply(); }
    static void setInt(Context c, String key, int v) { prefs(c).edit().putInt(key, v).putInt("stamp", stamp(c) + 1).apply(); }

    static void reset(Context c) {
        int s = stamp(c);
        prefs(c).edit().clear().putInt("stamp", s + 1).apply();
    }

    private static int clamp(int v, int n) { return v < 0 || v >= n ? 0 : v; }

    // ----- derived colours -----
    static final class Palette {
        boolean dark;
        int bg, fg, muted, line, bar, btn, accent, accentFg, th;
    }

    static Palette palette(Context c) {
        String mode = mode(c);
        Palette p = new Palette();
        switch (mode) {
            case "light": case "sepia": p.dark = false; break;
            case "dark": case "black": p.dark = true; break;
            default:
                p.dark = (c.getResources().getConfiguration().uiMode
                        & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        }
        int neutralTh;
        if ("sepia".equals(mode)) {
            p.bg = 0xFFF4ECD8; p.fg = 0xFF3B3226; p.muted = 0xFF7A6A55; p.line = 0xFFCDBF9F;
            p.bar = 0xFFEBE0C6; p.btn = 0xFFF9F3E3; neutralTh = 0xFFE9DCC0;
        } else if ("black".equals(mode)) {
            p.bg = 0xFF000000; p.fg = 0xFFE6E6E6; p.muted = 0xFF8F8F8F; p.line = 0xFF3A3A3A;
            p.bar = 0xFF111111; p.btn = 0xFF1C1C1C; neutralTh = 0xFF1E1E1E;
        } else if (p.dark) {
            p.bg = 0xFF121212; p.fg = 0xFFE9E9E9; p.muted = 0xFF9A9A9A; p.line = 0xFF4A4A4A;
            p.bar = 0xFF1E1E1E; p.btn = 0xFF2A2A2A; neutralTh = 0xFF262626;
        } else {
            p.bg = 0xFFFFFFFF; p.fg = 0xFF1D1D1D; p.muted = 0xFF6B6B6B; p.line = 0xFFC9C9C9;
            p.bar = 0xFFF3F1EC; p.btn = 0xFFFFFFFF; neutralTh = 0xFFF0EDE6;
        }
        int a = accent(c);
        p.accent = p.dark ? ACCENT_DARK[a] : ACCENT_LIGHT[a];
        p.accentFg = p.dark ? 0xFF101010 : 0xFFFFFFFF;
        p.th = headerColor(header(c), p, neutralTh);
        return p;
    }

    private static int headerColor(int h, Palette p, int neutral) {
        if (h == 0) return neutral;
        if (h == 1) return blend(p.accent, p.bg, 0.20f);
        return p.dark ? HEADER_DARK[h] : HEADER_LIGHT[h];
    }

    /** Header colour swatches as they'd look with the current palette. */
    static int[] headerSwatches(Context c) {
        Palette p = palette(c);
        int[] out = new int[HEADER_NAMES.length];
        out[0] = neutralFor(c, p);
        for (int i = 1; i < out.length; i++) out[i] = headerColor(i, p, out[0]);
        return out;
    }

    private static int neutralFor(Context c, Palette p) {
        String mode = mode(c);
        if ("sepia".equals(mode)) return 0xFFE9DCC0;
        if ("black".equals(mode)) return 0xFF1E1E1E;
        return p.dark ? 0xFF262626 : 0xFFF0EDE6;
    }

    static int[] accentSwatches(Context c) {
        return palette(c).dark ? ACCENT_DARK.clone() : ACCENT_LIGHT.clone();
    }

    static int blend(int fg, int bg, float amount) {
        int r = Math.round(((fg >> 16) & 0xFF) * amount + ((bg >> 16) & 0xFF) * (1 - amount));
        int g = Math.round(((fg >> 8) & 0xFF) * amount + ((bg >> 8) & 0xFF) * (1 - amount));
        int b = Math.round((fg & 0xFF) * amount + (bg & 0xFF) * (1 - amount));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    static Typeface typeface(Context c) { return FONT_FACES[font(c)]; }
    static Typeface typefaceAt(int i) { return FONT_FACES[clamp(i, FONT_FACES.length)]; }
    static int textSize(Context c) { return SIZES[size(c)]; }

    private static String css(int color) {
        return String.format("#%06X", color & 0xFFFFFF);
    }

    /** Everything the rich editor page needs, as CSS custom properties. */
    static String editorJson(Context c) {
        Palette p = palette(c);
        JSONObject o = new JSONObject();
        try {
            o.put("bg", css(p.bg));
            o.put("fg", css(p.fg));
            o.put("muted", css(p.muted));
            o.put("line", css(p.line));
            o.put("bar", css(p.bar));
            o.put("btn", css(p.btn));
            o.put("btn-on", css(p.accent));
            o.put("btn-on-fg", css(p.accentFg));
            o.put("th", css(p.th));
            o.put("font", FONT_CSS[font(c)]);
            o.put("size", textSize(c) + "px");
            o.put("scheme", p.dark ? "dark" : "light");
        } catch (JSONException ignored) {
            // only thrown for non-finite numbers
        }
        return o.toString();
    }

    // ----- applying to screens -----

    /** Use in attachBaseContext so Light/Dark choices override the phone's setting. */
    static Context wrap(Context base) {
        String mode = mode(base);
        int night;
        switch (mode) {
            case "light": case "sepia": night = Configuration.UI_MODE_NIGHT_NO; break;
            case "dark": case "black": night = Configuration.UI_MODE_NIGHT_YES; break;
            default: return base;
        }
        Configuration conf = new Configuration(base.getResources().getConfiguration());
        conf.uiMode = (conf.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | night;
        return base.createConfigurationContext(conf);
    }

    /** Colours the window, action bar and system bars. Call after setContentView. */
    @SuppressWarnings("deprecation")
    static void style(Activity a) {
        Palette p = palette(a);
        Window w = a.getWindow();
        w.setBackgroundDrawable(new ColorDrawable(p.bg));
        w.setStatusBarColor(p.bar);
        w.setNavigationBarColor(p.bar);
        ActionBar ab = a.getActionBar();
        if (ab != null) ab.setBackgroundDrawable(new ColorDrawable(p.bar));
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController ic = w.getInsetsController();
            if (ic != null) {
                int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                ic.setSystemBarsAppearance(p.dark ? 0 : mask, mask);
            }
        } else {
            View d = w.getDecorView();
            int flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            int v = d.getSystemUiVisibility();
            d.setSystemUiVisibility(p.dark ? v & ~flags : v | flags);
        }
    }

    static void button(Button b, Palette p) {
        b.setBackgroundTintList(ColorStateList.valueOf(p.accent));
        b.setTextColor(p.accentFg);
    }

    static void checkbox(CheckBox cb, Palette p) {
        cb.setButtonTintList(ColorStateList.valueOf(p.accent));
    }
}
