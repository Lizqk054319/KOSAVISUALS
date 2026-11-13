# KosaVisuals (Fabric 1.21.4, client-side)

Prawy Shift otwiera menu z kolumnami modułów (przeciągaj panele za nagłówek, PPM / strzałka = ustawienia, pole wyszukiwania).
**L** w menu otwiera edytor HUD (przeciągasz Watermark, Spotify i TargetHUD). Pole **Config** jest pod wyszukiwarką.

## Moduły
- **PVP:** Hitbox, **TargetHUD** (kolor + gradient), **Kill Effect** (tryb, czas ducha, wysokość pioruna, kolor ducha, kolor pioruna).
- **Dźwięki:** Hit Sound, Kill Sound, Click Sound. Własne .wav: przeciągnij na okno gry przy otwartym menu
  albo wrzuć do `config/holovis/sounds`.
- **Świat:** Custom Sky – gotowe nieba (Sakura, Zachód słońca, Neon, Galaxy, Fiolet, Lodowe, Toxic, Krwawe, Mięta,
  Złota godzina) albo własne kolory nieba i horyzontu.
- **HUD:** Watermark (nazwa, FPS, koordynaty – każde do wyłączenia, kolor + gradient), Spotify (kolor + gradient).
- **Klient:** Akcent GUI (kolor, gradient, tło, kolor napisu, efekt hologramu).

Wybór koloru: kółko (odcień + nasycenie), paleta, suwaki nasycenia i jasności, tęcza.

## Komendy
- `/holovis` albo `/kosa` – otwiera menu
- `/holovis ghost` – duch gracza „TestBot” widoczny tylko dla Ciebie (można go bić; nic nie idzie na serwer)
- `/holovis ghost remove` – usuwa go
- `/holovis killtest` – podgląd Kill Effectu

## Spotify
Windows 10/11: odczyt i sterowanie przez systemową sesję multimediów (bez logowania).
Otwórz czat i najedź na element Spotify: tekst piosenki (lrclib.net, długie linie się zawijają), pasek przewijania i przyciski.
Skróty (Sterowanie → KosaVisuals): `[` poprzedni, `]` następny, `\` pauza/play.

## Configi
Pole Config pod wyszukiwarką: **Kopiuj kod** (kod `HV1:...` trafia do schowka) i **Load config** (wczytuje kod ze schowka).

## Uwaga przy aktualizacji
Plik moda nazywa się teraz `KosaVisuals-1.1.0.jar`. Usuń ze `mods` stary `holovis-*.jar`.
