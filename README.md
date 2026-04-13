<p align="center">
  <img src="https://img.shields.io/badge/Android-API_26+-3DDC84?style=for-the-badge&logo=android&logoColor=white"/>
  <img src="https://img.shields.io/badge/Kotlin-2.0-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white"/>
  <img src="https://img.shields.io/badge/NFC-IsoDep-00BCD4?style=for-the-badge"/>
  <img src="https://img.shields.io/badge/Security-Challenge--Response-FF5722?style=for-the-badge"/>
</p>

<h1 align="center">📟 Stelsuy Terminal</h1>
<h3 align="center">Android NFC Terminal · Android NFC Термінал</h3>

<p align="center">
  Part of the <strong>Stelsuy</strong> employee attendance system.<br/>
  Reads NFC badges (HCE phones), verifies identity via challenge–response, and registers attendance events.
</p>

<p align="center">
  <a href="#english">🇬🇧 English</a> &nbsp;|&nbsp;
  <a href="#ukrainian">🇺🇦 Українська</a>
</p>

---

## 🇬🇧 English <a name="english"></a>

### Overview

**Stelsuy Terminal** is an Android application that acts as a stationary NFC reader terminal. It is mounted at an office entrance and communicates with employee phones running the [Stelsuy Employee HCE](https://github.com/your-org/stelsuy-employee-hce) app.

The terminal does **not** use traditional NFC cards or tags. Instead, it reads data from employee Android phones via **ISO 14443-4 / IsoDep** and performs a cryptographic challenge–response handshake to guarantee authenticity before sending an attendance event to the backend server.

### How It Works

```
┌──────────────────────────────────────────────────────────────────┐
│                    Secure Scan Flow                              │
│                                                                  │
│  ┌─────────────┐   1. POST /challenge   ┌──────────────────┐    │
│  │   Terminal  │──────────────────────► │  Backend Server  │    │
│  │   Android   │◄────────────────────── │  (FastAPI)       │    │
│  │     App     │   2. challenge_b64     └──────────────────┘    │
│  └──────┬──────┘                                                 │
│         │                                                        │
│  3. NFC: SELECT AID + send challenge                            │
│         │                                                        │
│  ┌──────▼──────┐                                                 │
│  │  Employee   │  Signs challenge with RSA private key          │
│  │  HCE Phone  │  stored in Android Keystore (hardware-backed)  │
│  └──────┬──────┘                                                 │
│         │                                                        │
│  4. NFC: returns signature_b64                                  │
│         │                                                        │
│  ┌──────▼──────┐   5. POST /secure-scan  ┌──────────────────┐   │
│  │   Terminal  │──────────────────────► │  Backend Server  │    │
│  │             │   {employee_uid,        │  verifies RSA    │    │
│  │             │    challenge_b64,       │  signature &     │    │
│  │             │    signature_b64,       │  logs event      │    │
│  │             │    direction, ts}       └──────────────────┘    │
│  └─────────────┘                                                 │
└──────────────────────────────────────────────────────────────────┘
```

### Key Features

| Feature | Description |
|---|---|
| 🔐 **Challenge–Response** | Server issues a one-time nonce per scan; phone signs it — replay attacks are impossible |
| 📱 **HCE Protocol** | Communicates via ISO 14443-4 (IsoDep) using custom APDU commands over AID `F0010203040506` |
| 📷 **QR Setup** | First-launch configuration by scanning a QR code from the admin panel — zero manual typing |
| 🔄 **Register Mode** | Toggle switch to enroll new employee phones (reads public key and registers it on the server) |
| 🔊 **Voice Feedback** | Text-to-speech audio confirmation: "Access Granted", "Access Denied", "Please Wait" |
| 📳 **Haptic Feedback** | Distinct vibration patterns: single pulse = success, double pulse = error |
| 🖥️ **Always-On Display** | Screen stays on while the app is in foreground (`FLAG_KEEP_SCREEN_ON`) |
| ⏱️ **Scan Cooldown** | 1.5-second local cooldown prevents accidental double-scans |
| 🔧 **In-App Settings** | Long-press title to reconfigure server URL, API key, and terminal ID without reinstall |
| ↕️ **IN / OUT Direction** | Configurable per terminal — stored in SharedPreferences |

### APDU Command Protocol

The terminal communicates with the employee HCE app using custom APDU commands:

| Command | APDU Hex | Response | Purpose |
|---|---|---|---|
| SELECT AID | `00 A4 04 00 07 F0010203040506 00` | `90 00` | Open channel |
| GET\_EMP | `00 CA 00 00 00` | `EMP:<uid> 90 00` | Read employee UID |
| GET\_PUB | `00 CC 00 00 00` | `PUB:<base64> 90 00` | Read RSA public key |
| SIGN | `00 CB <challenge_bytes>` | `<signature_b64> 90 00` | Sign server challenge |

### Project Structure

```
stelsuy-terminal-android/
├── app/src/main/
│   ├── java/com/stelsuy/terminal/
│   │   ├── MainActivity.kt        # NFC dispatch, scan flow, UI log
│   │   ├── SetupActivity.kt       # QR-code / manual first-launch setup
│   │   ├── api/
│   │   │   ├── ApiClient.kt       # Retrofit instance (reconfigurable at runtime)
│   │   │   ├── TerminalApi.kt     # REST interface definition
│   │   │   └── dto/               # Request / response data classes
│   │   │       ├── ChallengeRequest.kt / ChallengeResponse.kt
│   │   │       ├── SecureScanRequest.kt / SecureScanResponse.kt
│   │   │       ├── FirstScanRequest.kt / FirstScanResponse.kt
│   │   │       └── ...
│   │   ├── nfc/
│   │   │   ├── NfcHceReader.kt    # IsoDep APDU transceiver
│   │   │   └── NfcReader.kt       # Legacy tag reader (passive NFC)
│   │   └── util/
│   │       ├── Prefs.kt           # SharedPreferences wrapper
│   │       ├── VoiceMessage.kt    # TTS message enum
│   │       └── VoicePlayer.kt     # TTS engine wrapper
│   ├── res/
│   │   ├── layout/
│   │   │   ├── activity_main.xml
│   │   │   └── activity_setup.xml
│   │   └── raw/                   # (optional audio files)
│   └── AndroidManifest.xml
├── build.gradle
└── settings.gradle
```

### Requirements

- Android **8.0 (API 26)** or higher
- Device with **NFC** hardware
- Network access to the Stelsuy backend server

### Build & Install

**From Android Studio:**
1. Open the project in Android Studio Hedgehog or newer
2. Sync Gradle
3. Run on a physical device (NFC does not work on emulators)

**From command line:**
```bash
./gradlew assembleRelease
# APK output: app/build/outputs/apk/release/app-release.apk
```

### First-Time Setup

1. Open the **Admin Panel** (`http://<server>/admin`)
2. Create a terminal → copy the generated QR code
3. Launch **Stelsuy Terminal** on the Android device
4. Point the camera at the QR code — configuration is applied automatically
5. The terminal is ready to scan

> Alternatively, tap **"Enter Manually"** and type the server URL, API key, and terminal ID.

### Configuration (SharedPreferences)

| Key | Description |
|---|---|
| `base_url` | Backend server address (e.g. `http://192.168.0.10:8000`) |
| `api_key` | Terminal API key from the admin panel |
| `terminal_id` | Numeric terminal ID |
| `direction` | Scan direction: `IN` or `OUT` |
| `setup_done` | `true` after successful first-time setup |

### Related Repositories

| Repository | Description |
|---|---|
| [stelsuy-backend](https://github.com/StelSuy/diplom_v2) | FastAPI backend — attendance server |
| [stelsuy-employee-hce](https://github.com/your-org/stelsuy-employee-hce) | Android HCE badge app (employee phone) |

---

## 🇺🇦 Українська <a name="ukrainian"></a>

### Огляд

**Stelsuy Terminal** — Android-додаток, що виконує роль стаціонарного NFC-терміналу для реєстрації відвідуваності. Встановлюється на вхід в офіс і зчитує дані з телефонів співробітників, які використовують [Stelsuy Employee HCE](https://github.com/your-org/stelsuy-employee-hce).

Термінал не використовує класичні NFC-картки — він зчитує дані з телефонів через **ISO 14443-4 (IsoDep)** і виконує криптографічне підтвердження особи перед записом події відвідуваності.

### Ключові можливості

- **🔐 Challenge–Response** — сервер видає одноразовий нонс на кожне сканування; телефон підписує його RSA-ключем — replay-атаки неможливі
- **📷 QR-налаштування** — при першому запуску достатньо відсканувати QR-код з адмін-панелі
- **🔄 Режим реєстрації** — перемикач для реєстрації нових телефонів-бейджів
- **🔊 Голосовий зворотній зв'язок** — TTS повідомлення: "Доступ дозволено", "Доступ заборонено"
- **📳 Вібрація** — однократна (успіх) і подвійна (помилка) вібрація
- **🖥️ Екран завжди увімкнений** — термінал не засинає
- **↕️ Напрямок IN/OUT** — налаштовується окремо для кожного терміналу

### Збірка та встановлення

```bash
# Клонувати репозиторій
git clone https://github.com/your-org/stelsuy-terminal-android.git

# Зібрати APK
./gradlew assembleRelease

# APK: app/build/outputs/apk/release/app-release.apk
```

### Перше налаштування

1. Відкрити **Адмін-панель** → Термінали → Створити термінал → QR-код
2. Запустити **Stelsuy Terminal** на Android-пристрої
3. Відсканувати QR-код — конфігурація застосовується автоматично
4. Термінал готовий до роботи

### Вимоги

- Android **8.0 (API 26)** або вище
- Пристрій з апаратним **NFC**
- Мережевий доступ до сервера Stelsuy

---

<p align="center">
  Розроблено як частина дипломного проєкту · 2026
</p>
