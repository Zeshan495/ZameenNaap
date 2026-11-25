<div align="center">


# Zameen Naap (زمین ناپ): Land Measurement App for Pakistan

**Free, offline land area calculator for Android. Measure land in Marla, Kanal, Acre, Murabba, Sarsai and Karam with GPS or by hand.**

**زمین کی پیمائش کی مفت ایپ: مرلہ، کنال، ایکڑ، مربع اور سرسائی میں رقبہ معلوم کریں۔ بغیر انٹرنیٹ کے۔**

[![Build APK](https://github.com/Zeshan495/ZameenNaap/actions/workflows/build-apk.yml/badge.svg)](https://github.com/Zeshan495/ZameenNaap/actions/workflows/build-apk.yml)
[![Latest release](https://img.shields.io/github/v/release/Zeshan495/ZameenNaap?label=Download%20APK&color=01411C)](https://github.com/Zeshan495/ZameenNaap/releases/latest)
![Android 7+](https://img.shields.io/badge/Android-7%2B-3DDC84?logo=android&logoColor=white)
![Offline](https://img.shields.io/badge/works-100%25%20offline-01411C)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

### [⬇️ Download the latest APK](https://github.com/Zeshan495/ZameenNaap/releases/latest)

</div>

---

## What is Zameen Naap?

**Zameen Naap** (Roman Urdu: *zameen naap*, "measure the land") is a **land measurement app made for Pakistan**. Farmers, landowners, property dealers, patwaris and surveyors can use it to find the **area of a field, plot or property** in local units. It works in villages with **no internet and no mobile signal**, because it uses only the phone's **GPS satellites**.

Use it as a **marla calculator**, **kanal calculator**, **acre to kanal converter**, **GPS area calculator** or **field area measure app**, in **Urdu or English**.

**زمین ناپ** پاکستان کے کسانوں، زمینداروں، پراپرٹی ڈیلرز اور پٹواریوں کے لیے زمین ناپنے کی ایپ ہے۔ کھیت کے کونوں پر کھڑے ہو کر GPS سے رقبہ معلوم کریں، یا فیتے سے ناپی ہوئی اطراف کرم یا فٹ میں لکھیں۔ نتیجہ مربع، ایکڑ، کنال، مرلہ اور سرسائی میں ملتا ہے، بالکل پٹواری کے انداز میں۔ انٹرنیٹ کی ضرورت نہیں۔

## Features

| | |
|---|---|
| 📍 **GPS walk-around** | Stand on each corner of the field and tap *Mark corner*. The app averages 8 seconds of GPS readings per corner for better accuracy, then beeps and vibrates. |
| 🚶 **Auto walk mode** | For curved or irregular boundaries: walk along the edge and a point is added every 3 m. |
| 📏 **Measure by hand (patwari method)** | Enter sides in **karam, feet, gaz or meters**. Choose a rectangle, triangle or 4-sided field. Add the diagonal for the **exact area** (Heron's formula). |
| 🇵🇰 **Pakistani land units** | Murabba, Acre / Killa, Kanal, Marla, Sarsai, square karam, Ghunta (Sindh), square gaz, square feet, square meter and hectare. |
| 🏘️ **Two marla sizes** | **272.25 sq ft** (patwari / revenue record, villages) or **225 sq ft** (housing societies such as DHA and LDA). |
| 📐 **Accuracy shown honestly** | Shows the likely error (e.g. "± 6 marla"), calculated from GPS accuracy. Warns if corners were marked out of order. |
| 💾 **Save and share** | Save fields on the phone and share results on **WhatsApp** in Urdu and English, with a map link. |
| 🔤 **Urdu and English** | Full right-to-left Urdu interface. Switch with one tap. |
| ✈️ **100% offline** | No internet permission, no account, no ads, no tracking. |

## Pakistani land units (زمین کی اکائیاں)

| Unit | Urdu | Equals | Square feet |
|---|---|---|---|
| Karam (length) | کرم | 5.5 feet | n/a |
| Sarsai | سرسائی | 1 square karam | 30.25 |
| Marla (revenue) | مرلہ | 9 sarsai | 272.25 |
| Marla (housing society) | مرلہ | n/a | 225 |
| Kanal | کنال | 20 marla | 5,445 |
| Acre / Killa | ایکڑ / کلہ | 8 kanal | 43,560 |
| Murabba | مربع | 25 acre | 1,089,000 |
| Ghunta (Sindh) | گھنٹا | 1/40 acre | 1,089 |

**Quick conversions:** 1 acre = 8 kanal = 160 marla. 1 kanal = 20 marla. 1 marla = 9 sarsai = 272.25 sq ft. 1 murabba = 25 acre = 200 kanal.

## How to measure land with GPS

1. Go to the field and stand in open sky (away from trees and walls).
2. Wait until GPS accuracy turns **green (±5 m or better)**.
3. Stand on corner 1, tap **Mark corner** and stay still for 8 seconds.
4. Walk to each next corner **in order** around the field and repeat.
5. The area appears live as murabba, acre, kanal, marla and sarsai. Tap **Save**.

> **Accuracy:** Phone GPS is accurate to about ±2–5 m. For a 1-acre field that is roughly ±5–7 marla. GPS is great for big fields and quick estimates. For small plots or exact figures, use the **By hand** tab with a measuring tape.

## Install

1. Open **[Releases](https://github.com/Zeshan495/ZameenNaap/releases/latest)** on your phone and download `ZameenNaap-v1.x.apk`.
2. Tap the file. If asked, allow **Install unknown apps**.
3. Open **Zameen Naap** and allow **Location**.

Works on **Android 7.0 and newer**, including low-cost phones.

## Build from source

```bash
git clone https://github.com/Zeshan495/ZameenNaap.git
cd ZameenNaap
./gradlew assembleDebug     # APK: app/build/outputs/apk/debug/app-debug.apk
```

Needs JDK 17+ and the Android SDK (Android Studio includes both). Every push to `main` is built automatically by GitHub Actions and published on the Releases page.

**Tech:** Kotlin, Android SDK, LocationManager (GPS only), no Google Play Services, no internet. The area is calculated with the shoelace formula on a local tangent plane. Error is estimated by propagating each corner's GPS error. The manual mode uses Heron's formula.

## FAQ

**Does it need internet?** No. GPS works from satellites, even with no SIM and no signal.

**How many marla are in a kanal?** 20 marla = 1 kanal. **How many kanal are in an acre?** 8 kanal = 1 acre.

**Why are there two marla sizes?** Government land records (patwari, *fard*, *jamabandi*) use 272.25 sq ft. Many housing societies use 225 sq ft. Choose yours from the top bar.

**Is it accurate enough for legal use?** No. It's for estimates and checking. For legal records, use an official survey by the revenue department (patwari / tehsildar) or a licensed surveyor.

**Is it free?** Yes. It's free and open source (MIT), with no ads.

## Keywords

land measurement app Pakistan, zameen ki paimaish, zameen naapne wali app, marla calculator, kanal calculator, acre to kanal, kanal to marla, marla to square feet, sarsai, karam, murabba, killa, ghunta Sindh, patwari app, GPS area calculator offline, field area measure app, farm land area calculator, plot size calculator Pakistan, Punjab land units, Urdu land app, زمین کی پیمائش، مرلہ کنال ایکڑ کیلکولیٹر، پٹواری

## License

[MIT](LICENSE) © 2026 Zeshan Haider. Free to use, change and share.
