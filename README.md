# Skivbacken

Din skivsamling i telefonen. Fota en skiva så letar appen upp vilken det är, eller sök fram skivor du vill ha. Spela albumet, eller en enskild låt, i Spotify.

- **Läsa av foton:** först letar appen efter en streckkod med Googles ML Kit, direkt i telefonen och gratis. Saknas streckkod frågar den Gemini (gratisnivån) vilket omslag det är. Om Gemini inte har några anrop kvar för dagen söker appen på texten på omslaget.
- **Bläddra:** som i en skivback. Dra nedåt för att fälla fram nästa skiva, eller hoppa med bokstavslisten. Sorteringen är efternamn för soloartister (Dylan, Bob), bandnamn utan "The" för grupper, och sedan årtal. Fliken Alla visar både Har och Vill ha. ▦ byter till rutnät.
- **Skivdata:** Discogs API ger omslag, utgåvor och låtlistor.
- **Spela:** Spotify. Utan inloggning öppnar Spela en sökning i Spotify-appen. Med inloggning (kräver Premium och en egen Spotify-app) går den direkt till rätt album och låt.

Samlingen sparas på telefonen. Under ⚙︎ → Backup till Google Drive sparar du skivor och inställningar som en fil i Drive, och hämtar den igen på en ny telefon. Det går också att slå på automatisk backup.

**Värde:** varje skiva visar Discogs prisförslag för valt skick (standard VG+) och lägsta pris just nu, och samlingens totala värde visas under flikarna. Prisförslagen kräver att säljarinställningarna är ifyllda på Discogs (välj SEK). Du kan välja exakt utgåva per skiva.

**Många skivor:** tryck på + och välj "Snabbskanna många skivor". Skanna streckkod efter streckkod. Säkra träffar läggs in direkt, och osäkra hamnar i "Att granska".

## Kom igång

1. Öppna appen, tryck på ⚙︎ och klistra in:
   - **Gemini API-nyckel** (gratis): [aistudio.google.com/apikey](https://aistudio.google.com/apikey)
   - **Discogs personal token:** [discogs.com/settings/developers](https://www.discogs.com/settings/developers)
   - *(valfritt)* **Spotify Client ID:** skapa en app på [developer.spotify.com/dashboard](https://developer.spotify.com/dashboard), kryssa i Web API och lägg in Redirect URI `https://persgatan51.github.io/skivbacken/`
2. Tryck på **+** och fota en skiva. Bäst träff får du om du fotar baksidan med streckkoden.

Nycklarna sparas bara i appen.

## Android-app

Varje push som ändrar `android/` bygger en ny APK via GitHub Actions. Ladda ner `skivbacken.apk` under Releases → "Android-app" och installera den på telefonen. Appen visar webbappen från GitHub Pages och öppnar kameran och Spotify-appen.
