package com.kb1123.quietnotes;

import android.app.AlertDialog;
import android.graphics.Paint;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Checkable list. Tap an item to tick it (ticked items drop to the bottom),
 * long-press to edit or delete it.
 */
public class ChecklistActivity extends EditorBase {
    private static final int MENU_UNCHECK = 10, MENU_CLEAR = 11;

    private final List<Note.Item> items = new ArrayList<>();
    private LinearLayout listBox;
    private EditText addField;

    @Override protected int noteType() { return Note.LIST; }
    @Override protected String screenTitle() { return "List"; }

    @Override
    protected View buildBody() {
        int pad = Ui.dp(this, 12);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);

        LinearLayout addRow = new LinearLayout(this);
        addRow.setOrientation(LinearLayout.HORIZONTAL);
        addRow.setGravity(Gravity.CENTER_VERTICAL);
        addRow.setPadding(pad, 0, pad, 0);

        addField = new EditText(this);
        addField.setHint("Add item");
        addField.setSingleLine(true);
        addField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        addField.setImeOptions(EditorInfo.IME_ACTION_DONE);
        addField.setOnEditorActionListener((v, actionId, event) -> {
            boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN;
            if (actionId == EditorInfo.IME_ACTION_DONE || enter) {
                addFromField();
                return true; // keep the keyboard open for the next item
            }
            return false;
        });
        addRow.addView(addField, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button add = new Button(this);
        add.setText("Add");
        add.setAllCaps(false);
        add.setOnClickListener(v -> addFromField());
        addRow.addView(add);
        box.addView(addRow);

        ScrollView scroll = new ScrollView(this);
        listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        listBox.setPadding(0, 0, 0, pad * 4);
        scroll.addView(listBox);
        box.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        if (isNew()) {
            addField.requestFocus();
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                    | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        return box;
    }

    private void addFromField() {
        String text = addField.getText().toString();
        boolean any = false;
        // Pasting several lines adds several items.
        for (String line : text.split("\n")) {
            String t = line.trim();
            if (t.isEmpty()) continue;
            items.add(new Note.Item(t, false));
            any = true;
        }
        addField.setText("");
        if (any) render();
    }

    private void render() {
        listBox.removeAllViews();
        int checkedCount = 0;
        for (Note.Item it : items) {
            if (!it.checked) listBox.addView(row(it));
            else checkedCount++;
        }
        if (checkedCount > 0) {
            TextView header = new TextView(this);
            header.setText("Done (" + checkedCount + ")");
            header.setTextSize(13);
            header.setAlpha(0.6f);
            int p = Ui.dp(this, 16);
            header.setPadding(p, Ui.dp(this, 14), p, Ui.dp(this, 4));
            listBox.addView(header);
            for (Note.Item it : items) {
                if (it.checked) listBox.addView(row(it));
            }
        }
        if (items.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Type above and press Add.\nTap an item to tick it off; long-press to edit or delete.");
            empty.setAlpha(0.55f);
            int p = Ui.dp(this, 16);
            empty.setPadding(p, p, p, p);
            listBox.addView(empty);
        }
    }

    private View row(Note.Item it) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int p = Ui.dp(this, 8);
        row.setPadding(p, Ui.dp(this, 2), Ui.dp(this, 16), Ui.dp(this, 2));
        row.setMinimumHeight(Ui.dp(this, 48));

        CheckBox cb = new CheckBox(this);
        cb.setChecked(it.checked);
        cb.setOnClickListener(v -> {
            it.checked = cb.isChecked();
            render();
        });
        row.addView(cb);

        TextView tv = new TextView(this);
        tv.setText(it.text);
        tv.setTextSize(17);
        tv.setPadding(Ui.dp(this, 4), p, 0, p);
        if (it.checked) {
            tv.setPaintFlags(tv.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            tv.setAlpha(0.5f);
        }
        row.addView(tv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        row.setOnClickListener(v -> {
            it.checked = !it.checked;
            render();
        });
        row.setOnLongClickListener(v -> {
            editItem(it);
            return true;
        });
        return row;
    }

    private void editItem(Note.Item it) {
        EditText input = new EditText(this);
        input.setText(it.text);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setSelection(input.getText().length());
        LinearLayout wrap = new LinearLayout(this);
        int p = Ui.dp(this, 20);
        wrap.setPadding(p, Ui.dp(this, 8), p, 0);
        wrap.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(this)
                .setTitle("Edit item")
                .setView(wrap)
                .setPositiveButton("Save", (d, w) -> {
                    String t = input.getText().toString().trim();
                    if (t.isEmpty()) items.remove(it);
                    else it.text = t;
                    render();
                })
                .setNeutralButton("Delete", (d, w) -> {
                    items.remove(it);
                    render();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    protected void loadBody(String body) {
        items.clear();
        items.addAll(Note.parseItems(body));
        render();
    }

    @Override protected String currentBody() { return Note.itemsToJson(items); }
    @Override protected boolean bodyIsEmpty() { return items.isEmpty(); }
    @Override protected String exportMime() { return "text/plain"; }
    @Override protected String exportExtension() { return "txt"; }

    @Override
    protected String exportContent() {
        String t = currentTitle();
        String list = Note.plainOf(Note.LIST, currentBody());
        return t.isEmpty() ? list : t + "\n\n" + list;
    }

    @Override
    protected void addExtraMenu(Menu menu) {
        menu.add(0, MENU_UNCHECK, 10, "Untick all");
        menu.add(0, MENU_CLEAR, 11, "Remove ticked items");
    }

    @Override
    protected boolean onExtraMenu(int itemId) {
        if (itemId == MENU_UNCHECK) {
            for (Note.Item it : items) it.checked = false;
            render();
            return true;
        }
        if (itemId == MENU_CLEAR) {
            Iterator<Note.Item> iter = items.iterator();
            while (iter.hasNext()) {
                if (iter.next().checked) iter.remove();
            }
            render();
            return true;
        }
        return false;
    }
}
