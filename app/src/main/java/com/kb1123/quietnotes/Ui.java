package com.kb1123.quietnotes;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;
import android.util.TypedValue;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Small shared helpers. */
final class Ui {
    private Ui() {}

    static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics()));
    }

    static void toast(Context c, String msg) {
        Toast.makeText(c, msg, Toast.LENGTH_SHORT).show();
    }

    static void writeText(Context c, Uri uri, String text) throws IOException {
        try (OutputStream os = c.getContentResolver().openOutputStream(uri)) {
            if (os == null) throw new IOException("Could not open file");
            os.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    static String readText(Context c, Uri uri) throws IOException {
        try (InputStream is = c.getContentResolver().openInputStream(uri)) {
            if (is == null) throw new IOException("Could not open file");
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
            return new String(bos.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    static String fileName(String title, String fallback, String ext) {
        String base = title == null ? "" : title.trim().replaceAll("[\\\\/:*?\"<>|\\n\\r\\t]", "_");
        if (base.isEmpty()) base = fallback;
        if (base.length() > 60) base = base.substring(0, 60);
        return base + "." + ext;
    }

    /** Wraps a rich note's HTML into a standalone, nicely styled HTML file. */
    static String htmlDocument(String title, String bodyHtml) {
        String t = TextUtils.htmlEncode(title == null ? "" : title);
        return "<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n<meta charset=\"utf-8\">\n"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n"
                + "<title>" + (t.isEmpty() ? "Note" : t) + "</title>\n"
                + "<style>\n"
                + "body{font-family:system-ui,-apple-system,Segoe UI,Roboto,sans-serif;max-width:46em;margin:2em auto;padding:0 1em;line-height:1.55;color:#222}\n"
                + "table{border-collapse:collapse;margin:1em 0}\n"
                + "th,td{border:1px solid #999;padding:.35em .7em;text-align:left;vertical-align:top}\n"
                + "th{background:#f0ede6}\n"
                + "blockquote{border-left:3px solid #999;margin:1em 0;padding-left:1em;color:#555}\n"
                + "pre,code{font-family:ui-monospace,monospace;background:#f3f3f3}\n"
                + "pre{padding:.6em;overflow-x:auto}\n"
                + "img{max-width:100%}\n"
                + "</style>\n</head>\n<body>\n"
                + (t.isEmpty() ? "" : "<h1>" + t + "</h1>\n")
                + (bodyHtml == null ? "" : bodyHtml)
                + "\n</body>\n</html>\n";
    }
}
