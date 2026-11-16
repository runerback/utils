# Plan: local Yomitan dictionary provider (via subread-dictionary)

Add a third, fully offline translation provider to Translator Server, backed by the
`subread-dictionary` project downloaded at `D:\Projects\subread-dictionary-main`.

## What subread-dictionary provides

- `:core` — plain Kotlin/JVM 17 module (only dependency: Gson), package `space.subread.dictionary.core`:
  - `YomitanZip` — streaming reader for Yomitan format-3 zips: `readIndex(InputStream)`,
    `readBanks(InputStream, DictionarySink)` (term / term_meta / tag banks; kanji banks skipped).
  - `Term`, `TermMeta`, `Tag`, `DictionaryIndex` — format-3 row models.
  - `Lookup.find(text, offset, TermSource)` — longest-prefix-first matching with kana
    normalization and deinflection (`Deinflector`, `Kana`), returns `Match(stored, length, reasons)`.
  - `TermSource` — interface I implement: `terms(texts: Collection<String>): List<StoredTerm>`.
  - `Glossary` — `toText(glossaryJson)` (plain text, fits `TranslationResult.text`),
    `toHtml(...)`, `pitchText(...)`, `frequencyText(...)` for meta rows.
- `:app` — Android app I do **not** reuse wholesale, but its `Dictionaries.kt` is a ready-made
  SQLite implementation of `TermSource` plus import/list/delete/enable/reorder/meta queries.
  I port this one class into the translator app.
- License: **AGPL-3.0** — fine for personal use; be aware if the app is ever distributed.

Existing translator architecture this plugs into:

- `translate/TranslationProvider.kt` — enum `OLLAMA`, `ARGOSTRANSLATE`.
- `translate/Translator.kt` — `translateToEnglish` / `simplifyEnglish` / `translateToChinese`,
  returning `TranslationResult(text)` (plain string).
- `ui/floating/TranslationPanelViewModel.kt` — `createTranslator()` per provider; `show()`
  chooses target language (only Argos; others default `"en"`).
- `data/SettingsRepository.kt` — per-provider settings keys already parameterized by enum name.
- `SettingsDialog.kt` — per-provider sections via `if (providerInput == ...)`.

## Steps

### 1. Build integration (done — vendored module)
- `:core` copied into this repo as `:subread-dictionary-core` (main sources + AGPL LICENSE),
  registered in `settings.gradle.kts`, consumed by the app via
  `implementation(project(":subread-dictionary-core"))`. Self-contained, builds anywhere;
  upstream sync is a manual re-copy.

### 2. Runtime dictionary store (done — `dictionary/DictionaryStore.kt`)
- Port subread's `Dictionaries.kt` read path: `SQLiteOpenHelper` implementing `TermSource` —
  `list()`, `setEnabled(id, enabled)`, `meta(expression)`, `anyEnabled()`, `terms(texts)`.
  No `import`/`delete`/`moveUp`: the bundled set is fixed at build time.
- On first launch, copy the `dictionaries.db` asset to `getDatabasePath("dictionaries.db")` when
  missing (or when an app update shipped a newer bundle), then open normally.
- The DB schema is created by `:dictionary-builder` at build time — keep both in sync.
- The `dictionaries` table persists title/revision/position/enabled, so **no DataStore
  JSON list is needed** — the DB is the single source of truth.
- Hold one instance as a singleton (created in `TranslatorApplication`, same style as
  `OcrEngineProvider`); all access on `Dispatchers.IO`.
- Handle "no bundled dictionary" (asset absent): the provider reports
  "No dictionary bundled in this build" instead of crashing.

### 3. Build-time dictionary bundling (done — `:dictionary-builder`)
- Repo-root `dictionaries/` folder (git-ignored) holds the Yomitan format-3 zips, supplied by
  whoever builds; never committed, licensing is the builder's responsibility (README documents it).
- `:dictionary-builder` (plain JVM, core + sqlite-jdbc) imports each zip into `dictionaries.db`
  with the store's schema; `:app:buildDictionaryDatabase` runs it into generated assets and is
  wired before `merge*Assets`. Skipped when the folder has no zips.
- Kanji-only zips (KANJIDIC) contribute zero terms — kanji banks are skipped by the reader.

### 4. `LocalDictionaryTranslator` (done — `dictionary/LocalDictionaryTranslator.kt` + `DictionaryFormatter.kt` + unit test)
- Implements `Translator`; `translateToEnglish(text)`:
  - `Lookup.find(text.trim(), 0, store)` inside `withContext(Dispatchers.IO)`.
  - Empty store / no enabled dictionary → error "No dictionary bundled in this build".
  - No match → "No dictionary entry found for …".
  - Format each `Match` as plain text: `expression (reading)`, optional deinflection reasons,
    pitch/frequency lines from `store.meta(expression)` + `Glossary.pitchText`/`frequencyText`,
    then `Glossary.toText(term.glossary)`; blank line between terms, dictionary title as a header
    when several dictionaries are enabled.
- `translateToChinese(text)`: same lookup (glossary language depends on the imported dictionary;
  a JA→ZH Yomitan dictionary just works).
- `simplifyEnglish(text)`: not meaningful for a dictionary — hide the Simplify button for this
  provider (preferred), or delegate to the lookup as a fallback. Decide in step 6.

### 5. Provider wiring (done)
- `DICTIONARY` added to `TranslationProvider` (per-provider settings keys then work automatically).
- `TranslationPanelViewModel.createTranslator()`: `DICTIONARY -> LocalDictionaryTranslator(store)`
  via `DictionaryStoreProvider`; null store → `DictionaryUnavailableException`. Factory and
  ViewModel now take a `Context` for this.
- `TranslationPanelViewModel.show()`: dictionary falls into the non-Argos `"en"` path, which maps
  to `translateToEnglish` → the lookup; no target-language branching needed.

### 6. Settings UI (done — `ProviderSettings.kt`, `SettingsDialog.kt`)
- Provider sections extracted into per-provider composables (`OllamaProviderSettings`,
  `ArgosTranslateProviderSettings`, `DictionaryProviderSettings`); the dialog picks one with
  `when` instead of if/else chains. State stays hoisted in the dialog, Save/Cancel unchanged.
- Dictionary section: bundled dictionary list (title, revision · entry count, enabled checkbox).
  Toggles write straight to the dictionary DB, so they apply immediately, outside Save/Cancel.
- URL/language/fake-server/Simplify controls live in the server-provider composables; the
  dictionary provider shows none of them. `showSimplifyButton` defaults to false for `DICTIONARY`.
- Attribution text from `index.json` is not stored in the DB schema — skipped in-app; licensing
  responsibility is documented in README instead.

### 7. Result panel check (done)
- `FloatingTranslationPanel` Success state already scrolls; added a 60%-of-screen `heightIn` cap
  so long dictionary entries can't push the panel off-screen. Plain-text formatter output wraps
  in the existing `Text`.

### 8. Tests
- Unit-test the formatter with a fake in-memory `TermSource` (a list) — no Android needed:
  single hit, multiple dictionaries ordering, no hit, deinflected hit with reasons.
- Optional device test for `DictionaryStore.import` + `terms()` with a small fixture zip
  (subread has `DictionariesTest.kt` as a reference).

## Suggested order

1 + 3 (build integration + build-time bundling, done) → 2 (runtime store + asset copy) →
4 (translator + formatter, unit-tested with fake source) →
5 (provider wiring, end-to-end lookup works) → 6 (minimal settings UI) → 7, 8 (polish).

## Open decisions

- ~~Composite build vs vendored copy of `:core`~~ — decided: vendored as `:subread-dictionary-core`.
- Simplify button for this provider: hidden (recommended) vs Ollama fallback.
- Include pitch/frequency meta in output (recommended yes — near-free via `store.meta`).
