# Skivhyllan

Din skivsamling i telefonen. Fota en skiva så letar appen upp vilken det är, eller sök fram skivor du vill ha. Spela albumet, eller en enskild låt, i Spotify.

- **Läsa av foton:** Googles ML Kit läser streckkod och text direkt i telefonen. Det är gratis och kräver ingen nyckel. Appen söker sedan i Discogs på streckkod, katalognummer och den största texten (oftast artist och titel).
- **Skivdata:** Discogs API ger omslag, utgåvor och låtlistor.
- **Spela:** Spotify. Utan inloggning öppnar Spela en sökning i Spotify-appen. Med inloggning (kräver Premium och en egen Spotify-app) går den direkt till rätt album och låt.

Samlingen sparas på telefonen. Under ⚙︎ finns en backup som du kan kopiera och klistra in igen.

## Kom igång

1. Öppna appen, tryck på ⚙︎ och klistra in:
   - **Discogs personal token:** [discogs.com/settings/developers](https://www.discogs.com/settings/developers)
   - *(valfritt)* **Spotify Client ID:** skapa en app på [developer.spotify.com/dashboard](https://developer.spotify.com/dashboard), kryssa i Web API och lägg in Redirect URI `https://persgatan51.github.io/skivhyllan/`
2. Tryck på **+** och fota en skiva. Bäst träff får du om du fotar baksidan med streckkoden.

Nycklarna sparas bara i appen.

## Android-app

Varje push som ändrar `android/` bygger en ny APK via GitHub Actions. Ladda ner `skivhyllan.apk` under Releases → "Android-app" och installera den på telefonen. Appen visar webbappen från GitHub Pages och öppnar kameran och Spotify-appen.
