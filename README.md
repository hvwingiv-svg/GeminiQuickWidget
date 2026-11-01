# Gemini Quick Pill — Native Android Home Screen Widget

This native Android widget (`AppWidgetProvider`) places the dark Gemini pill bar (`+`, `"What's next?"`, Microphone, and Purple Gemini Live Waveform button) directly into your phone's **Hold Home Screen → Widgets** menu.

Unlike the official Gemini widget (which forces the full Gemini app open), tapping any part of this pill opens a **translucent floating bar right over your wallpaper** so you can type, speak, hear spoken replies, and see inline answers without leaving your home screen.

---

## How to Install on Your Android Phone

Because Android only allows compiled `.apk` packages to register in the system **Hold Home Screen → Widgets** picker, choose either option below to compile the `.apk` in ~60 seconds:

### Option 1: Build on Your Phone via GitHub Actions (No Computer Needed)
1. Create a free repository on [github.com](https://github.com) and upload the contents of this `.zip`.
2. Open the **Actions** tab in your GitHub repository — the included workflow (`.github/workflows/build-apk.yml`) automatically compiles the APK.
3. Tap the completed workflow run and download **`GeminiQuickWidget-APK`** (`app-debug.apk`), then tap to install it on your phone.

### Option 2: Build in Android Studio (PC / Mac)
1. Unzip this folder and open it in **Android Studio**.
2. Click **Build → Build Bundle(s) / APK(s) → Build APK(s)** (or click **Run** with your phone plugged in).
3. Install `app-debug.apk` on your phone.

---

## How to Add to Your Home Screen
1. Unlock your phone and **touch and hold an empty space** on your home screen.
2. Tap **Widgets**.
3. Scroll to **Gemini Quick Widget** (`Gemini Pill (Inline)`) and drag it onto your home screen.
4. Tap **`+`** or **`What's next?`**, tap **⚙️ API Key** once to paste your Gemini API key, and interact directly on your home screen!
