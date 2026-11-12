# Shop Sales Tracker (Android POS) - v1.1

Offline POS / daily sales tracker. Kotlin, Jetpack Compose, Material 3, Room, MVVM. Money is stored as whole centavos (Long). No login, no cloud, no internet permission.

## Getting the APK

**GitHub Actions (no tools needed)**
1. Upload the contents of this folder to your GitHub repo (replace existing files; keep the `.github` folder and `app/shop-debug.keystore`).
2. Actions tab > "Build debug APK" runs automatically (or Run workflow).
3. Download `ShopSalesTracker-debug-apk` from the finished run, unzip, install `app-debug.apk`.

**Android Studio**: open this folder, Build > Build APK(s). Output: `app/build/outputs/apk/debug/app-debug.apk`.

## Updating from v1.0
The project is now signed with a fixed key (`app/shop-debug.keystore`). v1.0 builds were signed with a random key, so the FIRST time you must uninstall v1.0 (export to Excel first if you need its data). After that, every new build installs over the previous one and keeps your data. The database migrates automatically.

## What's new in 1.1
- Products: profit per unit and stocks; remaining stock shown on each card (red when out).
- Main cards: + and - buttons (tap the card or + to add, - to remove one); UNDO stays.
- Confirm taps: every add/remove asks first (switch "Confirm taps" on the main screen).
- NEW ORDER: bulk/pickup/delivery orders with quantities; delivery asks for the fee.
- Orders screen with cancel; DF Rates (delivery fee reference, editable); Expenses (popup form) tallied on closing.
- CHART: Today / Last Week / Last Month / Custom (calendar), delivery fees included in sales.
- Excel export now has Orders and Expenses sheets plus profit and delivery fee columns.
