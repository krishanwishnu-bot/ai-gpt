# Crypto Research AI v1.1 (quality and CI update)

Android (Kotlin + Jetpack Compose) cryptocurrency **research** app: Top 50 coins, eight on-device research agents,
technical/volume analysis, news & sentiment, backtesting, a virtual portfolio, agent monitor, research history and settings.

> Educational research only. No buy/sell signals, no trading, no exchange connection, no real money.

## Build the APK with GitHub (no Android Studio needed)

1. Create an empty repository on GitHub (e.g. `CryptoResearchAI`).
2. Upload / push **all files in this folder** (including the hidden `.github` folder) to the `main` branch:
   ```
   git init
   git add .
   git commit -m "Crypto Research AI v1.0"
   git branch -M main
   git remote add origin https://github.com/<your-user>/CryptoResearchAI.git
   git push -u origin main
   ```
3. Open the repo → **Actions** tab → the **Build APK** workflow runs automatically (or press *Run workflow*).
4. When it is green, open the run → **Artifacts** → download **CryptoResearchAI-debug-apk** → unzip → install `app-debug.apk` on your phone
   (allow "Install unknown apps" for your file manager/browser).

If the build fails, open the failed step in the log and send me the error text; I will fix it.

### Optional: signed release APK
Add these repository secrets (Settings → Secrets and variables → Actions): `KEYSTORE_BASE64` (base64 of your .jks),
`KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. The workflow then also publishes **CryptoResearchAI-release-apk**.

## Build with Android Studio
1. Install Android Studio (Koala or newer) with JDK 17.
2. *File → Open* → select this folder → wait for Gradle sync.
3. *Build → Build Bundle(s) / APK(s) → Build APK(s)*. Output: `app/build/outputs/apk/debug/app-debug.apk`.
4. Command line: `./gradlew assembleDebug`.

## Tech
Kotlin 1.9, Jetpack Compose (Material 3), MVVM + Repository, Room, WorkManager, OkHttp, Coil, EncryptedSharedPreferences. minSdk 26, targetSdk 34.
Charts are drawn with Compose Canvas (no charting library).

## Data sources (all free, HTTPS, no key required)
| Data | Provider |
|---|---|
| Top 50, prices, market cap, volume, global stats | CoinGecko public API (fallback: CoinLore) |
| OHLCV candles (15m/1h/4h/1d) | Binance public market-data endpoint; fallback: derived candles from CoinGecko (marked *approximate*, no volume) |
| News | CryptoCompare news API (headlines, short snippet, link) |
| Fear & Greed Index | alternative.me |

An optional CoinGecko Demo API key can be entered in Settings → API configuration (stored encrypted on the device, never in source code).
Public APIs can rate-limit or be blocked in some regions; the app retries, caches, and labels data as Live / Cached / Stale.

## Quality and data-safety update
- CI now runs JVM unit tests and Android lint before producing the debug APK.
- Added focused unit tests for SMA, EMA, RSI, Bollinger Bands and ATR, including invalid ATR inputs.
- Removed Room's destructive-migration fallback so a future schema mismatch cannot silently wipe local watchlists, reports or virtual-portfolio data. Add and test an explicit Room migration whenever the schema changes.
- ATR now rejects mismatched high/low/close array lengths instead of risking an indexing crash.

## Architecture
```
data/        models, Room DB (coins, watchlist, cache, reports, virtual portfolio), API client, repositories, settings
analysis/    indicators (SMA/EMA/RSI/MACD/Bollinger/ATR), patterns (swings, S/R, structure, FVG, order blocks, Elliott),
             volume analysis (spikes, profile, delta), technical analyzer, backtester, keyword sentiment
agents/      8 agents + orchestrator + monitor state
notify/      notifications, WorkManager periodic worker (scheduled only if a notification type is enabled)
ui/          theme, components, charts, 10 screens + report/compare detail, ViewModel
```

### The eight agents (run sequentially, cancellable)
1 Market · 2 Technical · 3 Volume · 4 News · 5 Sentiment · 6 Bullish Evidence · 7 Bearish Evidence · 8 Risk & Final Report.
They are rule-based calculation modules, **not** large language models; no AI model is pretended to run.

## Honest limitations
* Fair Value Gaps, Order Blocks, Elliott waves and volume profile are heuristic approximations from public candle data.
* Volume delta uses Binance taker-buy volume (single exchange); unavailable on fallback data and then labelled so.
* No order-book, liquidation, institutional-flow or social-media data is used or invented.
* News sentiment is a transparent keyword heuristic.
* The source package is prepared for CI validation. A successful GitHub Actions run is still required to confirm Android compilation and produce the APK; local build validation may be unavailable when Gradle dependencies cannot be reached.
