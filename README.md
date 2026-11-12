# Shop Sales Tracker (Android POS)

Offline POS / daily sales tracker. Kotlin, Jetpack Compose, Material 3, Room, MVVM. Money is stored as whole centavos (Long). No login, no cloud, no internet permission.

## Getting the APK (choose one)

**A. Android Studio (easiest)**
1. Unzip, then File > Open the `ShopSalesTracker` folder. Let Gradle sync (needs internet once).
2. Build > Build Bundle(s) / APK(s) > Build APK(s).
3. APK: `app/build/outputs/apk/debug/app-debug.apk`

**B. GitHub Actions (no tools needed)**
1. Create a new GitHub repo and upload the contents of this folder (keep the `.github` folder).
2. Open the Actions tab > "Build debug APK" > Run workflow.
3. Download `ShopSalesTracker-debug-apk` from the finished run and unzip it to get `app-debug.apk`.

**C. Command line** (JDK 17, Android SDK platform 34, Gradle 8.10+): `gradle assembleDebug`

## Install on the phone
Copy `app-debug.apk` to the phone, open it, allow "Install unknown apps" for your file manager/browser when asked, then install. Requires Android 8.0+ (API 26).

## First use
1. Products > ADD PRODUCT for each item.
2. NEW DAY > enter opening floating money, opening cash, target > Start.
3. Tap product cards to record sales. UNDO reverses the latest sale (repeatable).
4. At closing: NEW DAY > enter final floating money and cash on hand > CLOSE DAY, then start the next day.

The app starts with a completely empty database.
