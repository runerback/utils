# Translator

Android reading aid: read EPUB/PDF/text, select text or OCR an area, translate with a local
provider — Ollama, ArgosTranslate, or an offline Yomitan dictionary bundled at build time.

## Bundled dictionaries

The app ships its dictionaries **prebuilt**: the Gradle task `:app:buildDictionaryDatabase`
imports every Yomitan format-3 zip found in the repo-root `dictionaries/` folder into a single
SQLite database (`dictionaries.db`) that goes into the APK assets. At runtime the app copies it
out on first launch — there is no in-app import step.

The zips are **not part of this repository** (`dictionaries/` is git-ignored). Whoever builds
the app drops in their own dictionary files and is responsible for honouring their licences
(e.g. Jitendex is CC BY-SA 4.0 and requires attribution when redistributed). With no zips in
the folder, the task is skipped and the app builds without the dictionary provider.

Where to get format-3 dictionaries:

- [Jitendex](https://github.com/stephenmk/Jitendex) — `jitendex-yomitan.zip` release asset.
- [yomitan-import](https://github.com/yomidevs/yomitan-import) — JMdict, KANJIDIC and more.
- Frequency and pitch-accent lists from the [Yomitan dictionaries collection](https://github.com/yomidevs/awesome-yomitan).

Notes:

- Kanji-only dictionaries (e.g. KANJIDIC) import nothing: the reader skips kanji banks, so a
  kanji zip adds zero terms. Include at least one term dictionary.
- Only Yomitan **format 3** is supported; other formats are skipped with a message at build time.
- The dictionary parsing code (`subread-dictionary-core/`) is a vendored copy of
  [SubRead Dictionary](https://github.com/equwal/subread-dictionary), AGPL-3.0.

## Build

```
./gradlew :app:assembleDebug
```
