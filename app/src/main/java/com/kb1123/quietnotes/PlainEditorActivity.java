package com.kb1123.quietnotes;

import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;

/** Plain text note. */
public class PlainEditorActivity extends EditorBase {
    private EditText bodyView;

    @Override protected int noteType() { return Note.PLAIN; }
    @Override protected String screenTitle() { return "Note"; }

    @Override
    protected View buildBody() {
        bodyView = new EditText(this);
        bodyView.setGravity(Gravity.TOP | Gravity.START);
        bodyView.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        bodyView.setHorizontallyScrolling(false);
        bodyView.setHint("Start typing…");
        bodyView.setTextSize(16);
        bodyView.setLineSpacing(0, 1.15f);
        bodyView.setBackground(null);
        int p = Ui.dp(this, 16);
        bodyView.setPadding(p, Ui.dp(this, 8), p, p);
        if (isNew()) {
            bodyView.requestFocus();
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                    | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        return bodyView;
    }

    @Override protected void loadBody(String body) { bodyView.setText(body); }
    @Override protected String currentBody() { return bodyView.getText().toString(); }
    @Override protected boolean bodyIsEmpty() { return currentBody().trim().isEmpty(); }
    @Override protected String exportMime() { return "text/plain"; }
    @Override protected String exportExtension() { return "txt"; }

    @Override
    protected String exportContent() {
        String t = currentTitle();
        return t.isEmpty() ? currentBody() : t + "\n\n" + currentBody();
    }
}
