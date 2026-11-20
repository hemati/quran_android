# Rattil — Quran & Tafsir (Android)

Fork von [quran/quran_android](https://github.com/quran/quran_android) (GPLv3) mit QuranGPT-Chat, Abo über Google Play Billing, AdMob-Werbung und Firebase. Paket `com.appcoholic.quran` (Flavor `madani`, Namespace `com.quran.labs.androidquran`). Lizenzhinweise und GPL-Pflichten nicht entfernen.

## Befehle

```bash
./gradlew assembleMadaniDebug                           # Debug-APK
./gradlew bundleMadaniRelease                           # Release-AAB, signiert über keystore.properties
./gradlew testMadaniDebugUnitTest testReleaseUnitTest   # Unit-Tests (:app bzw. Bibliotheksmodule)
./gradlew lintMadaniDebug
git fetch quran_android && git merge quran_android/main  # Upstream-Sync, Regeln siehe unten
# frischer Klon: git remote add quran_android https://github.com/quran/quran_android.git
```

- Gradle braucht ein JDK mit `javac` (17–21). Ein reines JRE scheitert mit `does not provide the required capabilities: [JAVA_COMPILER]`.
- Robolectric-Tests laufen mit einer Test-FirebaseApp aus `TestApplication` (Activities lesen Remote Config). `-PdisableFirebase` aus der CI wirkt im Fork nicht: `useFirebase` ist in `app/build.gradle.kts` fest `true`.
- Tests: Fakes statt Mocks, siehe `TESTING_STRATEGY.md`. Commits: Conventional Commits (`feat(gpt): …`, `fix(ads): …`, `chore: …`).

## Struktur

- `app` · `common/*` · `feature/*` · `pages/*` (Madani/Warsh-Daten) · `build-logic` (compileSdk/minSdk in `AndroidCommon.kt`, targetSdk in `AndroidApplicationConventionPlugin.kt`)
- Eigener Code liegt in `com.appcoholic.*`:
  - `feature/gpt` (`com.appcoholic.gpt`): QuranGPT-Chat (`DefaultMessagesActivity`), `BillingHelper`, `SubscriptionDialog`, `MessageQuotaManager`
  - `app/.../com/appcoholic/notifications`: Vers des Tages und Erinnerungen (WorkManager)
  - `app/.../com/appcoholic/ads`: `AdsInitializer`, `AdGatePolicy`, `AppForegroundGate`
- Der Rest ist Upstream-Code. Eigene Änderungen möglichst in `com.appcoholic.*` halten; nötige Eingriffe in Upstream-Dateien klein halten und mit `// Rattil:` kommentieren.

## QuranGPT, Billing, Werbung

- Abo-IDs `qurangpt_subscription`, `qurangpt_subscription_yearly` stehen auch in `PagerActivity.kt` (`PRO_SKUS`). Bei Änderungen beide Stellen anpassen. Nur `PurchaseState.PURCHASED` schaltet Pro frei.
- `BillingHelper` ist ein Singleton. Seine Listener-Callbacks laufen über den Main-Looper, weil Billing 9 teils auf Hintergrund-Threads zurückruft.
- Remote Config steuert den Chat: `openai_model_key`, `openai_reasoning_effort` (nur Reasoning-Modelle; leer = nicht gesendet), `openai_max_output_tokens`, `openai_max_message_chars`, `openai_max_context_chars`, `max_messages_per_day`, `vector_store_id`. Änderungen gelten sofort auch für alte App-Versionen; neue Modelle deshalb per Bedingung auf die Build-Nummer ausrollen.
- Werbung: AdMob mit Mediation (Meta, Unity; InMobi-Adapter ist eingebunden). `MobileAds.initialize` und jeder Ad-Load erst nach UMP-Einwilligung (`AdsInitializer`, `canRequestAds()`). Die `RequestConfiguration` begrenzt die Altersfreigabe auf T.

## Upstream-Merges

- Konflikte: `minSdk` 26, versionCode/-name, `useFirebase = true` und `:feature:gpt` behalten.
- `app_name` gibt es nur einmal (`values/strings.xml`, „Rattil“, nicht übersetzbar). Upstream-`app_name` in Sprachdateien entfernen.
- Upstream-Ankündigungen (`AnnouncementActivity`) bleiben aus: `SHOW_UPSTREAM_ANNOUNCEMENTS` in `QuranDataActivity`.
- Quran.com-Login/Sync (`feature/sync`) bleibt unsichtbar, solange keine `oauth.properties` mit Client-ID existiert.
- mobile-sync (`quranMobileSyncVersion`) ändert sein Schema ohne Migration, weil Upstream nie eine Version mit mobile-sync veröffentlicht hat (letzter Upstream-Release v3.6.3 ohne). Rattil 2.10.0 lief mit 0.1.7, 2.11.0 mit 0.1.21 (beide in `quran.db`); ab 0.1.5x liegen die Daten in `user_data.db`. `PreReleaseMobileSyncUpgrade` (`common/bookmark`) liest alte `quran.db`-Schemata und importiert Lesezeichen, Tags, Highlights, Lesepositionen und Leseverlauf. Nach jedem mobile-sync-Update prüfen, ob sich Dateiname oder Schema von `user_data.db` geändert haben (`.sq`-Dateien im Repo quran/mobile-sync zwischen den Tags vergleichen), und das Upgrade testen: alte Store-Version per `git worktree` bauen, Lesezeichen, Tags und Highlights anlegen, neue Version darüber installieren, Daten prüfen.
- mobile-sync kommt nur noch aus `https://quran.github.io/mobile-sync/maven`. `app/build.gradle.kts` und `feature/gpt/build.gradle.kts` haben eigene `repositories`-Blöcke, die die aus `settings.gradle.kts` ersetzen; neue Upstream-Repos dort nachtragen.
- Farben: Rattil-Akzent ist Grün (`accent_color` in `values/colors.xml`, `accent_navy_gold` zeigt darauf). Upstream-Selektoren `res/color/accent_color*.xml` und `res/color-night/accent_color_dark.xml` löschen, sonst doppelte Ressourcen.
- Launcher-Icons: Rattil nutzt eigene `.webp`-Icons in `main`. Upstream-PNGs, `mipmap-anydpi-v33` und das Debug-Icon/-Label (`ic_launcher_debug`, `app_debug_label`) nicht übernehmen.
- Danach auch `bundleMadaniRelease` bauen: Der Release prüft AAR-Metadaten strenger als Debug (z. B. compileSdk von `feature/gpt`).

## Release

- versionCode = Datum `YYMMDD` in `app/build.gradle.kts`, muss steigen. Commit `chore: bump version to X.Y.Z (code)`.
- Upload: `python3 -I play-store/tools/play_release.py --track internal` (Service-Account `rattil-play-publisher@quran-82a42`, nur Test-Tracks; Release-Notes in `play-store/release-notes/<versionName>.txt`).
- Signatur aus `keystore.properties` im Projekt-Root (Vorlage `keystore.properties.example`): `storeFile`, `keyAlias`, Passwort per `passwordCommand` aus dem Keyring, das nur bei Release-Tasks läuft. Play erwartet für Rattil den Upload-Key mit SHA1 `10:10:07:ED:97:27:7D:2E:FD:EF:BA:48:37:B9:68:F4:1C:6E:7B:6C`. Das ist der Alias `quran` im Keystore vom Laptop (`waheds-pc:~/Documents/appcoholic.keystore`, auf dem Desktop als `~/StudioProjects/appcoholic-laptop.keystore`). Der `appcoholic.keystore` auf dem Desktop und im Drive enthält ihn nicht (nur `bible`, `honeyblast`, `key0`). Fallbacks: die alten Keys `STORE_FILE`, `STORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` in `local.properties`, dann `-PRELEASE_STORE_FILE` usw. Ohne Keystore laufen Debug-Builds normal, Release-Builds bleiben unsigniert. `app/gradle.properties` enthält nur Platzhalter unter den alten Namen.
- `keystore.properties`, `local.properties` und `app/google-services.json` sind nicht getrackt; Werte nie committen oder ausgeben.
- Minify/R8 ist aus (in `app/build.gradle.kts` auskommentiert).
- Store-Texte pflegt die Play Console; `metadata/` stammt von Upstream und wird nicht genutzt. In Titel und Beschreibungen keine Fremdmarken (z. B. „GPT“) und keine Preiswörter („Free“).
- Neue SDKs, die Daten erheben (Werbung, Analytics, Mediation-Adapter), brauchen eine angepasste Datensicherheitserklärung in der Play Console, sonst lehnt Play das Update ab.

## Stolperfallen

- Debug und Release haben dieselbe applicationId (kein Suffix). Über eine Play-Installation lässt sich kein lokaler Build installieren.
- Der Debug-Build hat zwei Launcher (App und LeakCanary): direkt starten mit `adb shell am start -n com.appcoholic.quran/com.quran.labs.androidquran.QuranDataActivity`.
