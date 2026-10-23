# Quiet Notes

A small Android notes app with no ads, no tracking and no internet access.

## What it does

- **Notes**: plain text.
- **Rich notes**: headings, bold/italic/underline/strikethrough, bullet and numbered lists, quotes, tables (add/remove rows and columns), links and dividers. Export as a standalone `.html` file, or edit the HTML source directly.
- **Lists**: checkable lists for groceries and the like. Tap an item to tick it (ticked items move to the bottom), long-press to edit or delete. "Untick all" resets a list for next week's shop.
- Search across all notes, share any note as text, export plain notes and lists as `.txt`.
- **Backup**: export all notes to one JSON file and import it again (on a new phone, say). Importing skips notes you already have.
- Follows the phone's light/dark setting.

## Privacy

- The app requests **no permissions at all**, including no internet permission, so Android itself prevents it from going online.
- No third-party libraries: no ad SDKs, analytics or crash reporters.
- Android cloud backup is switched off for this app; your notes live only in the app's private storage until you export them.
- Files are written or read only through Android's own file picker, and only when you ask.

## Install

1. Open the [latest release](../../releases/latest) on your phone.
2. Download `QuietNotes-x.y.z.apk` and open it.
3. Allow your browser or file manager to "install unknown apps" if Android asks.

Every push to `main` builds a new APK with GitHub Actions and publishes it as a release. Newer versions install over older ones and keep your notes.

## Signing key

The APK is signed with the key in `signing/`, committed so every automated build uses the same key and updates install cleanly. Because the repo is public, anyone could sign an app with this key; that only matters if you install APKs claiming to be Quiet Notes from somewhere other than this repo. If you'd rather keep the key private, move it into GitHub Actions secrets.

## Building locally

Needs JDK 17 and the Android SDK:

```
./gradlew assembleRelease
```

The APK lands in `app/build/outputs/apk/release/`.

## Code layout

- `MainActivity` – the note list, search, backup/restore
- `EditorBase` – title, autosave, share/export/delete shared by all editors
- `PlainEditorActivity`, `RichEditorActivity`, `ChecklistActivity` – the three editors
- `assets/editor.html` – the rich text editor (runs locally in a WebView with network loads blocked)
- `NoteStore` – SQLite storage
