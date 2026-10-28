# About this fork

This repository is a **modified fork** of [Gathin23/cleanyourtv](https://github.com/Gathin23/cleanyourtv), a launcher for Android TV / Fire TV (Home screen, apps, dock) and a TV-cleaning pipeline.

- **Original author / owner:** Gathin. The original copyright and the MIT licence are kept unchanged in [LICENSE](LICENSE).
  Their code remains theirs; the changes made here are offered under the same MIT licence.
- **Forked from upstream commit** `fa1a2f0`. The work lives on the branch `continue-watching` (the default branch is the untouched upstream).
- **What was changed:** the launcher under `launcher/` (new Home screens, features and look; see the list below). The rest of the repository is unchanged upstream content.
- **Not affiliated:** this fork is made by a different person for their own Fire TV Stick Lite setup. It is not the official project and is not endorsed
  by the original authors. Product names (Fire TV, Amazon, Apple TV, Netflix and others) belong to their owners and are used only to describe what the
  software works with.
- **Fonts and data:** the launcher bundles the Inter font (SIL OFL 1.1, `launcher/fonts/Inter-OFL.txt`); film data and images come from TMDB at run time
  ("This product uses the TMDB API but is not endorsed or certified by TMDB.").
- **No warranty:** see the MIT licence. You change your own device at your own risk.

## Commits added on top of upstream

- feat: ContinueRules filters and orders Continue-watching rows
- feat: PosterRules (URL rules, cache key, eviction) and a guard that only PosterCache opens connections
- feat: PosterCache, the only code that opens a connection (https posters, bounded, cached)
- feat: continue_* settings, READ_TV_LISTINGS and INTERNET permissions, version name 3.1-continue
- feat: WatchNextReader reads Continue-watching rows from the TV provider
- feat: Continue-watching tiles in the adapter, placed right after the dock
- feat: Home reads Continue-watching rows on resume and ignores long-press on them
- feat: Continue-watching as its own labelled section: heading, caption under the poster, filler slots
- fix: Continue-watching cards leave the grid in reorder mode and return after it
- harden: poster decode capped at 2 M pixels, Watch Next read newest first, card opens only its publisher, stale refresh ignored
- feat: weather line under the date (Open-Meteo, place from IP via ipwho.is with a coordinate fallback), Net: the one allowlisted https text GET
- feat: weather emoji + F/C, Home-screen switches in Launcher settings, Now Playing card (needs the listener grant), redesigned settings screen
- fix: Now Playing card is compact and the header's date/weather lines stop short of it
- feat: Recommended for you group (TMDB: similar films, then popular on this TV's streaming apps), Net bearer for TMDB only, one-franchise cap
- feat: Aurora screensaver (drifting colour fields by time of day, large clock, date, weather), shared WeatherStore
- feat: Featured carousel above the dock (new, top-rated and recommended films and shows on this TV's apps) with a title page and Watch-on buttons
- feat: full-screen Featured screen with dock peek; baked backdrops, RenderScript Gaussian blur at three levels (sharp / soft / heavy), slide transition, bitmap release when Home leaves the screen, header focus
- fix: long featured titles, dock peek after a fresh start, stale blur after a slide; add ambient tint over the blur and close-apps-on-leave (music apps kept)
- feat: hourly refresh with rotating pages/seeds, sequenced featured transition, texture warm-up, progressive edge blur baked into the featured picture
- feat: Home page setting (recommendations / landscape / classic), generated landscape Home, settings redesign (menu + glass panel over the blurred landscape), 1080p backdrops, sequenced transitions both ways
- fix: weather is also checked by the 10-minute refresh tick, so the header stays current when Home is left on screen
- feat: Apple-style type (Inter, sentence case), glass dock rim, half-tile peek, wider top blur, smoother sequenced transitions
- docs: include the Inter font licence (SIL OFL 1.1) next to the bundled Inter files
