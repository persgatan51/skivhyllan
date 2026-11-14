# Skivbacken – anteckningar för Claude

- Ägare: Fredrik (persgatan51). Svara och skriv på svenska.
- Webbappen är en enda fil, `index.html`, som körs från GitHub Pages: https://persgatan51.github.io/skivbacken/
- `android/` är ett tunt WebView-skal (Kotlin). GitHub Actions bygger APK:n när något under `android/` ändras och lägger den i releasen `app`.
- Behåll `applicationId = se.persgatan.skivhyllan`, annars installeras inte uppdateringar ovanpå den gamla appen och användarens data försvinner.
- All data ligger i localStorage i appen. Backupen är en JSON-fil (ofta på Google Drive) via `App.saveBackup`/`App.loadBackup`.
- Bryggan `App.*` har ett versionsnummer (`App.version()`). Höj det när du lägger till en ny metod, och låt webbappen kontrollera `nativeVer()` så att gamla APK:er inte kraschar.
- **Uppdatera `CHANGELOG` i `index.html` (nyast överst) och `WEB_VERSION` vid varje ändring som användaren märker.**
- Tjänster: Discogs (token), Gemini gratisnivå (bara när streckkod saknas), Spotify (valfri PKCE-inloggning), ML Kit och Googles streckkodsskanner i telefonen.
