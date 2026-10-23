package com.kb1123.quietnotes;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;

import java.io.IOException;

/**
 * Shared behaviour for all three editors: title field, autosave when leaving,
 * share / export / delete menu.
 */
public abstract class EditorBase extends Activity {
    private static final int MENU_SHARE = 1, MENU_EXPORT = 2, MENU_DELETE = 3;
    private static final int REQ_EXPORT = 100;

    protected NoteStore store;
    protected Note note;
    protected EditText titleView;
    private String savedTitle = "", savedBody = "";
    private boolean deleted;

    protected abstract int noteType();
    protected abstract String screenTitle();
    protected abstract View buildBody();
    protected abstract void loadBody(String body);
    protected abstract String currentBody();
    protected abstract boolean bodyIsEmpty();
    protected abstract String exportMime();
    protected abstract String exportExtension();
    protected abstract String exportContent();

    protected void addExtraMenu(Menu menu) {}
    protected boolean onExtraMenu(int itemId) { return false; }

    protected boolean isNew() { return note.id < 0; }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = NoteStore.get(this);

        long id = savedInstanceState != null
                ? savedInstanceState.getLong("id", -1)
                : getIntent().getLongExtra("id", -1);
        note = id >= 0 ? store.get(id) : null;
        if (note == null) {
            note = new Note();
            note.type = noteType();
        }
        savedTitle = note.title;
        savedBody = note.body;

        ActionBar ab = getActionBar();
        if (ab != null) {
            ab.setDisplayHomeAsUpEnabled(true);
            ab.setTitle(screenTitle());
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        titleView = new EditText(this);
        titleView.setHint("Title");
        titleView.setTextSize(20);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setSingleLine(true);
        titleView.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        titleView.setText(note.title);
        int m = Ui.dp(this, 12);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.setMargins(m, m / 2, m, 0);
        root.addView(titleView, tp);

        root.addView(buildBody(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
        loadBody(note.body);
    }

    /** Saves if anything changed. Empty notes are not kept. */
    protected void save() {
        if (deleted) return;
        String t = titleView.getText().toString().trim();
        String b = currentBody();
        if (b == null) b = "";
        if (t.isEmpty() && bodyIsEmpty()) {
            if (note.id >= 0) {
                store.delete(note.id);
                note.id = -1;
            }
            savedTitle = "";
            savedBody = "";
            return;
        }
        if (note.id >= 0 && t.equals(savedTitle) && b.equals(savedBody)) return;
        long now = System.currentTimeMillis();
        note.title = t;
        note.body = b;
        if (note.id < 0) note.created = now;
        note.updated = now;
        store.save(note);
        savedTitle = t;
        savedBody = b;
    }

    @Override
    protected void onPause() {
        super.onPause();
        save();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong("id", note.id);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        addExtraMenu(menu);
        menu.add(0, MENU_SHARE, 50, "Share");
        menu.add(0, MENU_EXPORT, 51, "Export as ." + exportExtension());
        menu.add(0, MENU_DELETE, 52, "Delete");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
            return true;
        }
        if (id == MENU_SHARE) {
            String title = titleView.getText().toString().trim();
            String text = Note.plainOf(note.type, currentBody());
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            if (!title.isEmpty()) send.putExtra(Intent.EXTRA_SUBJECT, title);
            send.putExtra(Intent.EXTRA_TEXT, title.isEmpty() ? text : title + "\n\n" + text);
            startActivity(Intent.createChooser(send, "Share"));
            return true;
        }
        if (id == MENU_EXPORT) {
            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType(exportMime());
            i.putExtra(Intent.EXTRA_TITLE,
                    Ui.fileName(titleView.getText().toString(), "note", exportExtension()));
            startActivityForResult(i, REQ_EXPORT);
            return true;
        }
        if (id == MENU_DELETE) {
            new AlertDialog.Builder(this)
                    .setTitle("Delete this note?")
                    .setPositiveButton("Delete", (d, w) -> {
                        if (note.id >= 0) store.delete(note.id);
                        deleted = true;
                        finish();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            return true;
        }
        if (onExtraMenu(id)) return true;
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_EXPORT || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        try {
            Ui.writeText(this, data.getData(), exportContent());
            Ui.toast(this, "Exported");
        } catch (IOException e) {
            Ui.toast(this, "Export failed: " + e.getMessage());
        }
    }

    protected String currentTitle() {
        return titleView.getText().toString().trim();
    }
}
