package com.kb1123.quietnotes;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Images for rich notes. Each image is a file in the app's private storage
 * (files/images/ID.jpg). A note's HTML only holds a reference to it:
 * {@code <img data-qn="ID.jpg">}. That keeps the database small and fast;
 * the editor and HTML export swap the reference for the real picture.
 */
final class ImageStore {
    private ImageStore() {}

    /** Longest edge in pixels. Phone photos are shrunk to this to save space. */
    private static final int MAX_EDGE = 1600;

    static final Pattern ID = Pattern.compile("[0-9a-f]{32}\\.(?:jpg|png)");
    private static final Pattern REF = Pattern.compile("data-qn=\"([0-9a-f]{32}\\.(?:jpg|png))\"");
    private static final Pattern IMG_TAG = Pattern.compile(
            "<img\\b([^>]*?)\\s*data-qn=\"([0-9a-f]{32}\\.(?:jpg|png))\"([^>]*)>");

    static File dir(Context c) {
        File d = new File(c.getFilesDir(), "images");
        if (!d.isDirectory()) d.mkdirs();
        return d;
    }

    /** Returns the file for an ID, or null if the ID isn't one of ours. */
    static File file(Context c, String id) {
        if (id == null || !ID.matcher(id).matches()) return null;
        return new File(dir(c), id);
    }

    /** Decodes any image Android understands (JPEG, PNG, HEIC, WebP…), upright and shrunk. */
    static Bitmap decode(ImageDecoder.Source source) throws IOException {
        return ImageDecoder.decodeBitmap(source, (decoder, info, src) -> {
            decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
            int w = info.getSize().getWidth();
            int h = info.getSize().getHeight();
            int max = Math.max(w, h);
            if (max > MAX_EDGE) {
                float k = (float) MAX_EDGE / max;
                decoder.setTargetSize(Math.max(1, Math.round(w * k)), Math.max(1, Math.round(h * k)));
            }
        });
    }

    /** Shrinks a bitmap that came from somewhere other than {@link #decode}. */
    static Bitmap shrink(Bitmap b) {
        int max = Math.max(b.getWidth(), b.getHeight());
        if (max <= MAX_EDGE) return b;
        float k = (float) MAX_EDGE / max;
        return Bitmap.createScaledBitmap(b,
                Math.max(1, Math.round(b.getWidth() * k)),
                Math.max(1, Math.round(b.getHeight() * k)), true);
    }

    /** Saves a bitmap and returns its new ID. Transparent images stay PNG, the rest become JPEG. */
    static String save(Context c, Bitmap b) throws IOException {
        boolean png = b.hasAlpha();
        String id = UUID.randomUUID().toString().replace("-", "") + (png ? ".png" : ".jpg");
        File f = new File(dir(c), id);
        try (FileOutputStream os = new FileOutputStream(f)) {
            boolean ok = b.compress(png ? Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG,
                    png ? 100 : 82, os);
            if (!ok) throw new IOException("Could not encode image");
        }
        return id;
    }

    static String mime(String id) {
        return id.endsWith(".png") ? "image/png" : "image/jpeg";
    }

    static byte[] readBytes(File f) throws IOException {
        try (InputStream is = new FileInputStream(f)) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream((int) Math.max(1024, f.length()));
            byte[] buf = new byte[16384];
            int n;
            while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
            return bos.toByteArray();
        }
    }

    static String base64(Context c, String id) {
        File f = file(c, id);
        if (f == null || !f.isFile()) return null;
        try {
            return Base64.encodeToString(readBytes(f), Base64.NO_WRAP);
        } catch (IOException e) {
            return null;
        }
    }

    static String dataUri(Context c, String id) {
        String b64 = base64(c, id);
        return b64 == null ? null : "data:" + mime(id) + ";base64," + b64;
    }

    /** Writes an image from a backup. Existing files are left alone. */
    static void restore(Context c, String id, String b64) {
        File f = file(c, id);
        if (f == null || f.exists() || b64 == null) return;
        try (FileOutputStream os = new FileOutputStream(f)) {
            os.write(Base64.decode(b64, Base64.DEFAULT));
        } catch (IOException | IllegalArgumentException e) {
            f.delete();
        }
    }

    /** IDs referenced by a note's HTML. */
    static Set<String> refs(String html) {
        Set<String> out = new HashSet<>();
        if (html == null) return out;
        Matcher m = REF.matcher(html);
        while (m.find()) out.add(m.group(1));
        return out;
    }

    static Set<String> refs(List<Note> notes) {
        Set<String> out = new HashSet<>();
        for (Note n : notes) {
            if (n.type == Note.RICH) out.addAll(refs(n.body));
        }
        return out;
    }

    /** Replaces image references with embedded pictures, for a standalone HTML export. */
    static String inline(Context c, String html) {
        if (html == null || !html.contains("data-qn=")) return html;
        Matcher m = IMG_TAG.matcher(html);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String uri = dataUri(c, m.group(2));
            String tag = uri == null
                    ? "<img alt=\"(missing image)\"" + m.group(1) + m.group(3) + ">"
                    : "<img src=\"" + uri + "\"" + m.group(1) + m.group(3) + ">";
            m.appendReplacement(sb, Matcher.quoteReplacement(tag));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * Deletes image files no note uses any more (removed from a note, or the
     * note was deleted). Files from the last hour are kept, in case an undo
     * brings them back.
     */
    static void sweep(Context c, List<Note> notes) {
        Set<String> used = refs(notes);
        File[] files = dir(c).listFiles();
        if (files == null) return;
        long cutoff = System.currentTimeMillis() - 60L * 60L * 1000L;
        for (File f : files) {
            if (!used.contains(f.getName()) && f.lastModified() < cutoff) f.delete();
        }
    }
}
