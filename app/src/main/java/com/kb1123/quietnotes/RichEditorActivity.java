package com.kb1123.quietnotes;

import android.annotation.SuppressLint;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;

/**
 * Rich note (headings, lists, tables, links…). The editor is a local HTML page
 * in assets/editor.html. Network loads are blocked and the app has no
 * internet permission, so nothing in it can reach the web.
 */
public class RichEditorActivity extends EditorBase {
    private static final int MENU_SOURCE = 20;

    private WebView web;
    private volatile String latestHtml = "";

    @Override protected int noteType() { return Note.RICH; }
    @Override protected String screenTitle() { return "Rich note"; }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected View buildBody() {
        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setBlockNetworkLoads(true);
        s.setDomStorageEnabled(false);
        s.setSupportZoom(false);
        web.addJavascriptInterface(new Bridge(), "Android");
        web.setWebChromeClient(new WebChromeClient());   // enables the link prompt dialog
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return true; // never navigate away from the editor
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                view.evaluateJavascript("setContent(" + JSONObject.quote(latestHtml) + ")", null);
            }
        });
        web.loadUrl("file:///android_asset/editor.html");
        return web;
    }

    /** Called from the page's JavaScript every time the content changes. */
    private class Bridge {
        @JavascriptInterface
        public void onChange(String html) {
            latestHtml = html == null ? "" : html;
        }
    }

    @Override protected void loadBody(String body) { latestHtml = body == null ? "" : body; }
    @Override protected String currentBody() { return latestHtml; }

    @Override
    protected boolean bodyIsEmpty() {
        String h = latestHtml;
        if (h.contains("<img") || h.contains("<table") || h.contains("<hr")) return false;
        return Note.plainOf(Note.RICH, h).trim().isEmpty();
    }

    @Override protected String exportMime() { return "text/html"; }
    @Override protected String exportExtension() { return "html"; }
    @Override protected String exportContent() { return Ui.htmlDocument(currentTitle(), latestHtml); }

    @Override
    protected void addExtraMenu(Menu menu) {
        menu.add(0, MENU_SOURCE, 10, "Edit HTML source");
    }

    @Override
    protected boolean onExtraMenu(int itemId) {
        if (itemId == MENU_SOURCE) {
            web.evaluateJavascript("toggleSource()", null);
            return true;
        }
        return false;
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            ViewGroup parent = (ViewGroup) web.getParent();
            if (parent != null) parent.removeView(web);
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
