package com.kb1123.quietnotes;

import android.text.Html;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** A single note. The body format depends on the type. */
public class Note {
    public static final int PLAIN = 0;   // body = plain text
    public static final int RICH = 1;    // body = HTML fragment
    public static final int LIST = 2;    // body = JSON array of {t: text, c: checked}

    public long id = -1;
    public int type = PLAIN;
    public String title = "";
    public String body = "";
    public long created;
    public long updated;

    public static class Item {
        public String text;
        public boolean checked;

        public Item(String text, boolean checked) {
            this.text = text;
            this.checked = checked;
        }
    }

    public String typeLabel() {
        switch (type) {
            case RICH: return "Rich";
            case LIST: return "List";
            default: return "Note";
        }
    }

    /** Body converted to readable plain text (for previews, search and sharing). */
    public static String plainOf(int type, String body) {
        if (body == null) return "";
        switch (type) {
            case RICH:
                return Html.fromHtml(body, Html.FROM_HTML_MODE_COMPACT).toString().trim();
            case LIST: {
                StringBuilder sb = new StringBuilder();
                for (Item it : parseItems(body)) {
                    if (sb.length() > 0) sb.append('\n');
                    sb.append(it.checked ? "[x] " : "[ ] ").append(it.text);
                }
                return sb.toString();
            }
            default:
                return body;
        }
    }

    public String plainText() {
        return plainOf(type, body);
    }

    /** One-line preview for the main list. */
    public String preview() {
        if (type == LIST) {
            List<Item> items = parseItems(body);
            int done = 0;
            StringBuilder open = new StringBuilder();
            for (Item it : items) {
                if (it.checked) {
                    done++;
                } else {
                    if (open.length() > 0) open.append(", ");
                    open.append(it.text);
                }
            }
            String head = done + "/" + items.size() + " done";
            return open.length() > 0 ? head + " · " + open : head;
        }
        String s = plainText().replaceAll("\\s+", " ").trim();
        return s.length() > 160 ? s.substring(0, 160) + "…" : s;
    }

    public static List<Item> parseItems(String body) {
        List<Item> out = new ArrayList<>();
        if (body == null || body.trim().isEmpty()) return out;
        try {
            JSONArray arr = new JSONArray(body);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                out.add(new Item(o.optString("t", ""), o.optBoolean("c", false)));
            }
        } catch (JSONException e) {
            // Not JSON (e.g. imported text): treat each line as an item.
            for (String line : body.split("\n")) {
                if (!line.trim().isEmpty()) out.add(new Item(line.trim(), false));
            }
        }
        return out;
    }

    public static String itemsToJson(List<Item> items) {
        JSONArray arr = new JSONArray();
        try {
            for (Item it : items) {
                JSONObject o = new JSONObject();
                o.put("t", it.text);
                o.put("c", it.checked);
                arr.put(o);
            }
        } catch (JSONException e) {
            // put() only throws for non-finite numbers; cannot happen here.
        }
        return arr.toString();
    }
}
