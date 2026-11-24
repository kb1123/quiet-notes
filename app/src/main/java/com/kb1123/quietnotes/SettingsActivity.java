package com.kb1123.quietnotes;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.function.IntConsumer;

/** Settings → Theme & fonts. Every change applies immediately. */
public class SettingsActivity extends Activity {
    private ScrollView scroll;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(Theme.wrap(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActionBar ab = getActionBar();
        if (ab != null) {
            ab.setDisplayHomeAsUpEnabled(true);
            ab.setTitle("Theme & fonts");
        }
        build();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void rebuild() {
        int y = scroll == null ? 0 : scroll.getScrollY();
        build();
        scroll.post(() -> scroll.scrollTo(0, y));
    }

    private void build() {
        Theme.Palette p = Theme.palette(this);
        int pad = Ui.dp(this, 16);

        scroll = new ScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(pad, pad / 2, pad, pad * 2);
        scroll.addView(col);

        col.addView(preview(p));

        col.addView(heading("Appearance", p));
        int modeIdx = 0;
        for (int i = 0; i < Theme.MODES.length; i++) if (Theme.MODES[i].equals(Theme.mode(this))) modeIdx = i;
        col.addView(segmented(Theme.MODE_LABELS, null, modeIdx, p, i -> {
            Theme.setMode(this, Theme.MODES[i]);
            recreate(); // light/dark changes need a fresh screen
        }));

        col.addView(heading("Accent color", p));
        col.addView(swatches(Theme.ACCENT_NAMES, Theme.accentSwatches(this), Theme.accent(this), p, i -> {
            Theme.setInt(this, "accent", i);
            rebuild();
        }));

        col.addView(heading("Table headers", p));
        col.addView(swatches(Theme.HEADER_NAMES, Theme.headerSwatches(this), Theme.header(this), p, i -> {
            Theme.setInt(this, "header", i);
            rebuild();
        }));

        col.addView(heading("Font", p));
        Typeface[] faces = new Typeface[Theme.FONT_NAMES.length];
        for (int i = 0; i < faces.length; i++) faces[i] = Theme.typefaceAt(i);
        col.addView(segmented(Theme.FONT_NAMES, faces, Theme.font(this), p, i -> {
            Theme.setInt(this, "font", i);
            rebuild();
        }));

        col.addView(heading("Text size", p));
        col.addView(segmented(Theme.SIZE_NAMES, null, Theme.size(this), p, i -> {
            Theme.setInt(this, "size", i);
            rebuild();
        }));

        TextView note = new TextView(this);
        note.setText("These settings change how notes look on this phone. "
                + "Colors you add inside a note (text color, highlights, shaded cells) "
                + "are saved with the note and kept when you export it.");
        note.setTextSize(13);
        note.setTextColor(p.muted);
        note.setPadding(0, pad, 0, pad);
        col.addView(note);

        Button reset = new Button(this);
        reset.setText("Reset to defaults");
        reset.setAllCaps(false);
        reset.setOnClickListener(v -> {
            Theme.reset(this);
            recreate();
        });
        col.addView(reset, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(scroll);
        Theme.style(this);
    }

    // ---------- pieces ----------

    private TextView heading(String text, Theme.Palette p) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(14);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(p.fg);
        t.setPadding(0, Ui.dp(this, 20), 0, Ui.dp(this, 8));
        return t;
    }

    /** A row of equal buttons where one is selected, like the image S / M / Full bar. */
    private View segmented(String[] labels, Typeface[] faces, int selected, Theme.Palette p, IntConsumer onPick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        int gap = Ui.dp(this, 3);
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            TextView b = new TextView(this);
            b.setText(labels[i]);
            b.setGravity(Gravity.CENTER);
            b.setTextSize(labels.length > 3 ? 14 : 16);
            b.setTypeface(faces != null ? Typeface.create(faces[i], Typeface.BOLD) : Typeface.DEFAULT_BOLD);
            b.setMinHeight(Ui.dp(this, 46));
            boolean on = i == selected;
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(Ui.dp(this, 10));
            bg.setColor(on ? p.accent : p.btn);
            bg.setStroke(Ui.dp(this, 1), on ? p.accent : p.line);
            b.setBackground(bg);
            b.setTextColor(on ? p.accentFg : p.fg);
            b.setContentDescription(labels[i] + (on ? ", selected" : ""));
            b.setOnClickListener(v -> onPick.accept(idx));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(gap, 0, gap, 0);
            row.addView(b, lp);
        }
        return row;
    }

    private View swatches(String[] names, int[] colors, int selected, Theme.Palette p, IntConsumer onPick) {
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        int size = Ui.dp(this, 40);
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            boolean on = i == selected;
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            cell.setPadding(Ui.dp(this, 6), Ui.dp(this, 4), Ui.dp(this, 6), Ui.dp(this, 4));
            cell.setMinimumWidth(Ui.dp(this, 64));

            View dot = new View(this);
            GradientDrawable d = new GradientDrawable();
            d.setShape(GradientDrawable.OVAL);
            d.setColor(colors[i]);
            d.setStroke(Ui.dp(this, on ? 3 : 1), on ? p.fg : p.line);
            dot.setBackground(d);
            cell.addView(dot, new LinearLayout.LayoutParams(size, size));

            TextView label = new TextView(this);
            label.setText(names[i]);
            label.setTextSize(12);
            label.setTextColor(on ? p.fg : p.muted);
            if (on) label.setTypeface(Typeface.DEFAULT_BOLD);
            label.setPadding(0, Ui.dp(this, 4), 0, 0);
            cell.addView(label);

            cell.setContentDescription(names[i] + (on ? ", selected" : ""));
            cell.setOnClickListener(v -> onPick.accept(idx));
            row.addView(cell);
        }
        hs.addView(row);
        return hs;
    }

    /** A small sample showing the current choices. */
    private View preview(Theme.Palette p) {
        Typeface face = Theme.typeface(this);
        int size = Theme.textSize(this);
        int pad = Ui.dp(this, 14);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(pad, pad, pad, pad);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(Ui.dp(this, 12));
        bg.setColor(p.bg);
        bg.setStroke(Ui.dp(this, 1), p.line);
        card.setBackground(bg);

        TextView title = new TextView(this);
        title.setText("Weekend plan");
        title.setTypeface(Typeface.create(face, Typeface.BOLD));
        title.setTextSize(size + 6);
        title.setTextColor(p.fg);
        card.addView(title);

        TextView body = new TextView(this);
        body.setText("This is how your notes will look.");
        body.setTypeface(face);
        body.setTextSize(size);
        body.setTextColor(p.fg);
        body.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 10));
        card.addView(body);

        String[][] rows = {{"Day", "Plan"}, {"Sat", "Market"}};
        for (int r = 0; r < rows.length; r++) {
            LinearLayout tr = new LinearLayout(this);
            tr.setOrientation(LinearLayout.HORIZONTAL);
            for (String cellText : rows[r]) {
                TextView c = new TextView(this);
                c.setText(cellText);
                c.setTypeface(r == 0 ? Typeface.create(face, Typeface.BOLD) : face);
                c.setTextSize(size - 1);
                c.setTextColor(p.fg);
                c.setPadding(Ui.dp(this, 10), Ui.dp(this, 6), Ui.dp(this, 10), Ui.dp(this, 6));
                GradientDrawable cb = new GradientDrawable();
                cb.setColor(r == 0 ? p.th : p.bg);
                cb.setStroke(Ui.dp(this, 1), p.line);
                c.setBackground(cb);
                tr.addView(c, new LinearLayout.LayoutParams(Ui.dp(this, 110), ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            card.addView(tr);
        }

        TextView chip = new TextView(this);
        chip.setText("Link · Button");
        chip.setTextColor(p.accentFg);
        chip.setTextSize(13);
        chip.setTypeface(Typeface.DEFAULT_BOLD);
        chip.setPadding(Ui.dp(this, 12), Ui.dp(this, 6), Ui.dp(this, 12), Ui.dp(this, 6));
        GradientDrawable chipBg = new GradientDrawable();
        chipBg.setCornerRadius(Ui.dp(this, 16));
        chipBg.setColor(p.accent);
        chip.setBackground(chipBg);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.topMargin = Ui.dp(this, 12);
        card.addView(chip, clp);
        return card;
    }
}
