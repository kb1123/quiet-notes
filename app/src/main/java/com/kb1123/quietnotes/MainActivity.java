package com.kb1123.quietnotes;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {
    private static final int MENU_EXPORT = 1, MENU_IMPORT = 2, MENU_ABOUT = 3;
    private static final int REQ_EXPORT = 1, REQ_IMPORT = 2;

    private NoteStore store;
    private final List<Note> allNotes = new ArrayList<>();
    private final List<Note> shown = new ArrayList<>();
    private NoteAdapter adapter;
    private EditText search;
    private TextView emptyView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = NoteStore.get(this);
        int pad = Ui.dp(this, 12);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        search = new EditText(this);
        search.setHint("Search notes");
        search.setSingleLine(true);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { applyFilter(); }
        });
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.setMargins(pad, pad / 2, pad, 0);
        root.addView(search, sp);

        FrameLayout listFrame = new FrameLayout(this);
        ListView list = new ListView(this);
        adapter = new NoteAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> open(shown.get(position)));
        list.setOnItemLongClickListener((parent, view, position, id) -> {
            confirmDelete(shown.get(position));
            return true;
        });
        emptyView = new TextView(this);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setPadding(pad * 2, pad * 2, pad * 2, pad * 2);
        emptyView.setAlpha(0.6f);
        emptyView.setTextSize(16);
        listFrame.addView(list, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        listFrame.addView(emptyView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        list.setEmptyView(emptyView);
        root.addView(listFrame, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(pad / 2, pad / 2, pad / 2, pad / 2);
        bar.addView(newButton("+ Note", Note.PLAIN), barParams());
        bar.addView(newButton("+ Rich", Note.RICH), barParams());
        bar.addView(newButton("+ List", Note.LIST), barParams());
        root.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(root);
    }

    private LinearLayout.LayoutParams barParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        int m = Ui.dp(this, 4);
        p.setMargins(m, 0, m, 0);
        return p;
    }

    private Button newButton(String label, int type) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setOnClickListener(v -> {
            Intent i = new Intent(this, editorFor(type));
            startActivity(i);
        });
        return b;
    }

    private static Class<?> editorFor(int type) {
        switch (type) {
            case Note.RICH: return RichEditorActivity.class;
            case Note.LIST: return ChecklistActivity.class;
            default: return PlainEditorActivity.class;
        }
    }

    private void open(Note n) {
        Intent i = new Intent(this, editorFor(n.type));
        i.putExtra("id", n.id);
        startActivity(i);
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    private void reload() {
        allNotes.clear();
        allNotes.addAll(store.all());
        applyFilter();
    }

    private void applyFilter() {
        String q = search.getText().toString().trim().toLowerCase(Locale.ROOT);
        shown.clear();
        for (Note n : allNotes) {
            if (q.isEmpty()
                    || n.title.toLowerCase(Locale.ROOT).contains(q)
                    || n.plainText().toLowerCase(Locale.ROOT).contains(q)) {
                shown.add(n);
            }
        }
        emptyView.setText(allNotes.isEmpty()
                ? "No notes yet.\nUse the buttons below to start one."
                : "Nothing matches your search.");
        adapter.notifyDataSetChanged();
    }

    private void confirmDelete(Note n) {
        new AlertDialog.Builder(this)
                .setTitle("Delete this " + n.typeLabel().toLowerCase(Locale.ROOT) + "?")
                .setMessage(n.title.isEmpty() ? n.preview() : n.title)
                .setPositiveButton("Delete", (d, w) -> {
                    store.delete(n.id);
                    reload();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ---------- menu: backup / restore / about ----------

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, MENU_EXPORT, 0, "Export backup");
        menu.add(0, MENU_IMPORT, 0, "Import backup");
        menu.add(0, MENU_ABOUT, 0, "About & privacy");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_EXPORT: {
                Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("application/json");
                i.putExtra(Intent.EXTRA_TITLE, "quietnotes-backup-" + Ui.today() + ".json");
                startActivityForResult(i, REQ_EXPORT);
                return true;
            }
            case MENU_IMPORT: {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                startActivityForResult(i, REQ_IMPORT);
                return true;
            }
            case MENU_ABOUT:
                new AlertDialog.Builder(this)
                        .setTitle("Quiet Notes")
                        .setMessage("Everything stays on this phone.\n\n"
                                + "• No internet permission (the app cannot go online)\n"
                                + "• No ads, analytics or accounts\n"
                                + "• Android cloud backup is off for this app\n\n"
                                + "Use \"Export backup\" now and then to keep your own copy.\n\n"
                                + "Tip: long-press a note in the list to delete it.")
                        .setPositiveButton("OK", null)
                        .show();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == REQ_EXPORT) {
            try {
                Ui.writeText(this, uri, backupJson());
                Ui.toast(this, "Backup saved (" + allNotes.size() + " notes)");
            } catch (IOException | JSONException e) {
                Ui.toast(this, "Backup failed: " + e.getMessage());
            }
        } else if (requestCode == REQ_IMPORT) {
            try {
                int added = importBackup(Ui.readText(this, uri));
                Ui.toast(this, "Imported " + added + " note" + (added == 1 ? "" : "s"));
                reload();
            } catch (IOException | JSONException e) {
                Ui.toast(this, "That doesn't look like a Quiet Notes backup");
            }
        }
    }

    private String backupJson() throws JSONException {
        JSONArray arr = new JSONArray();
        for (Note n : store.all()) {
            JSONObject o = new JSONObject();
            o.put("type", n.type);
            o.put("title", n.title);
            o.put("body", n.body);
            o.put("created", n.created);
            o.put("updated", n.updated);
            arr.put(o);
        }
        JSONObject root = new JSONObject();
        root.put("app", "QuietNotes");
        root.put("version", 1);
        root.put("notes", arr);
        return root.toString(2);
    }

    /** Adds notes from a backup, skipping ones that are already here. */
    private int importBackup(String json) throws JSONException {
        JSONArray arr = new JSONObject(json).getJSONArray("notes");
        Set<String> existing = new HashSet<>();
        for (Note n : store.all()) existing.add(key(n));
        int added = 0;
        long now = System.currentTimeMillis();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            Note n = new Note();
            n.type = o.optInt("type", Note.PLAIN);
            if (n.type < Note.PLAIN || n.type > Note.LIST) n.type = Note.PLAIN;
            n.title = o.optString("title", "");
            n.body = o.optString("body", "");
            n.created = o.optLong("created", now);
            n.updated = o.optLong("updated", now);
            if (existing.add(key(n))) {
                store.save(n);
                added++;
            }
        }
        return added;
    }

    private static String key(Note n) {
        return n.type + "\u0000" + n.created + "\u0000" + n.title + "\u0000" + n.body;
    }

    // ---------- list rows ----------

    private class NoteAdapter extends BaseAdapter {
        @Override public int getCount() { return shown.size(); }
        @Override public Object getItem(int position) { return shown.get(position); }
        @Override public long getItemId(int position) { return shown.get(position).id; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Row row = convertView instanceof Row ? (Row) convertView : new Row(parent.getContext());
            row.bind(shown.get(position));
            return row;
        }
    }

    private static class Row extends LinearLayout {
        final TextView title, meta, preview;

        Row(Context c) {
            super(c);
            setOrientation(VERTICAL);
            int p = Ui.dp(c, 14);
            setPadding(p, Ui.dp(c, 10), p, Ui.dp(c, 10));
            title = new TextView(c);
            title.setTextSize(17);
            title.setTypeface(Typeface.DEFAULT_BOLD);
            title.setSingleLine(true);
            title.setEllipsize(TextUtils.TruncateAt.END);
            meta = new TextView(c);
            meta.setTextSize(12);
            meta.setAlpha(0.6f);
            preview = new TextView(c);
            preview.setTextSize(14);
            preview.setAlpha(0.8f);
            preview.setMaxLines(2);
            preview.setEllipsize(TextUtils.TruncateAt.END);
            addView(title);
            addView(meta);
            addView(preview);
        }

        void bind(Note n) {
            String p = n.preview();
            title.setText(n.title.isEmpty() ? (p.isEmpty() ? "(empty)" : "Untitled") : n.title);
            int flags = DateUtils.isToday(n.updated)
                    ? DateUtils.FORMAT_SHOW_TIME
                    : DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_ABBREV_MONTH;
            meta.setText(n.typeLabel() + " · " + DateUtils.formatDateTime(getContext(), n.updated, flags));
            preview.setText(p);
            preview.setVisibility(p.isEmpty() ? GONE : VISIBLE);
        }
    }
}
