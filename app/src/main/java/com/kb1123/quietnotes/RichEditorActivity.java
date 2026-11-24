package com.kb1123.quietnotes;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.provider.MediaStore;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Rich note (headings, lists, tables, links, images…). The editor is a local
 * HTML page in assets/editor.html. Network loads are blocked and the app has
 * no internet permission, so nothing in it can reach the web.
 */
public class RichEditorActivity extends EditorBase {
    private static final int MENU_SOURCE = 20;
    private static final int REQ_CAMERA = 200, REQ_PICK = 201;

    private WebView web;
    private volatile String latestHtml = "";
    private boolean pageReady;
    /** Images that arrived before the page finished loading: {id, dataUri}. */
    private final List<String[]> pendingInserts = new ArrayList<>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

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
                if (pageReady) return;
                pageReady = true;
                view.evaluateJavascript("setTheme(" + Theme.editorJson(RichEditorActivity.this) + ")", null);
                view.evaluateJavascript("setContent(" + JSONObject.quote(latestHtml) + ")", null);
                loadImagesInto(latestHtml);
                for (String[] p : pendingInserts) sendInsert(p[0], p[1]);
                pendingInserts.clear();
            }
        });
        web.loadUrl("file:///android_asset/editor.html");
        return web;
    }

    /** Called from the page's JavaScript. These run on a background thread. */
    private class Bridge {
        @JavascriptInterface
        public void onChange(String html) {
            latestHtml = html == null ? "" : html;
        }

        @JavascriptInterface
        public void pickImage() {
            runOnUiThread(RichEditorActivity.this::askImageSource);
        }
    }

    // ---------- images ----------

    private void askImageSource() {
        if (isFinishing()) return;
        new AlertDialog.Builder(this)
                .setTitle("Add image")
                .setItems(new CharSequence[]{"Take photo", "Choose from files"}, (d, which) -> {
                    if (which == 0) launchCamera();
                    else launchPicker();
                })
                .show();
    }

    private void launchCamera() {
        File photo = CaptureProvider.photoFile(this);
        photo.delete();
        Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        i.putExtra(MediaStore.EXTRA_OUTPUT, CaptureProvider.PHOTO_URI);
        i.setClipData(ClipData.newRawUri("photo", CaptureProvider.PHOTO_URI));
        i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(i, REQ_CAMERA);
        } catch (ActivityNotFoundException e) {
            Ui.toast(this, "No camera app found");
        }
    }

    private void launchPicker() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        try {
            startActivityForResult(i, REQ_PICK);
        } catch (ActivityNotFoundException e) {
            Ui.toast(this, "No file picker found");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) return;

        if (requestCode == REQ_CAMERA) {
            File photo = CaptureProvider.photoFile(this);
            Bitmap thumb = null;
            if ((!photo.isFile() || photo.length() == 0) && data != null && data.getExtras() != null) {
                // Some camera apps ignore our file and only return a small preview.
                Object o = data.getExtras().get("data");
                if (o instanceof Bitmap) thumb = (Bitmap) o;
            }
            final Bitmap fallback = thumb;
            processImages(() -> {
                List<String> ids = new ArrayList<>();
                if (photo.isFile() && photo.length() > 0) {
                    ids.add(ImageStore.save(this, ImageStore.decode(ImageDecoder.createSource(photo))));
                    photo.delete();
                } else if (fallback != null) {
                    ids.add(ImageStore.save(this, ImageStore.shrink(fallback)));
                }
                return ids;
            });
        } else if (requestCode == REQ_PICK && data != null) {
            List<Uri> uris = new ArrayList<>();
            ClipData clip = data.getClipData();
            if (clip != null) {
                for (int k = 0; k < clip.getItemCount(); k++) {
                    Uri u = clip.getItemAt(k).getUri();
                    if (u != null) uris.add(u);
                }
            } else if (data.getData() != null) {
                uris.add(data.getData());
            }
            if (uris.isEmpty()) return;
            processImages(() -> {
                List<String> ids = new ArrayList<>();
                for (Uri u : uris) {
                    ids.add(ImageStore.save(this,
                            ImageStore.decode(ImageDecoder.createSource(getContentResolver(), u))));
                }
                return ids;
            });
        }
    }

    private interface ImageJob {
        List<String> run() throws Exception;
    }

    /** Decodes/saves images off the main thread, then drops them into the note. */
    private void processImages(ImageJob job) {
        worker.execute(() -> {
            List<String[]> ready = new ArrayList<>();
            String error = null;
            try {
                for (String id : job.run()) {
                    String uri = ImageStore.dataUri(this, id);
                    if (uri != null) ready.add(new String[]{id, uri});
                }
            } catch (Exception | OutOfMemoryError e) {
                error = "Couldn't add that image";
            }
            final String err = error;
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                if (err != null) Ui.toast(this, err);
                for (String[] r : ready) {
                    if (pageReady) sendInsert(r[0], r[1]);
                    else pendingInserts.add(r);
                }
            });
        });
    }

    private void sendInsert(String id, String dataUri) {
        if (web == null) return;
        web.evaluateJavascript("insertImage(" + JSONObject.quote(id) + "," + JSONObject.quote(dataUri) + ")", null);
    }

    /** Loads the pictures a note refers to and hands them to the editor. */
    private void loadImagesInto(String html) {
        if (ImageStore.refs(html).isEmpty()) return;
        worker.execute(() -> {
            JSONObject map = new JSONObject();
            for (String id : ImageStore.refs(html)) {
                String uri = ImageStore.dataUri(this, id);
                if (uri == null) continue;
                try {
                    map.put(id, uri);
                } catch (JSONException ignored) {
                    // only thrown for non-finite numbers
                }
            }
            String js = "loadImages(" + map + ")";
            runOnUiThread(() -> {
                if (web != null) web.evaluateJavascript(js, null);
            });
        });
    }

    // ---------- EditorBase ----------

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

    @Override
    protected String exportContent() {
        return Ui.htmlDocument(currentTitle(), ImageStore.inline(this, latestHtml));
    }

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
        worker.shutdown();
        if (web != null) {
            ViewGroup parent = (ViewGroup) web.getParent();
            if (parent != null) parent.removeView(web);
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
