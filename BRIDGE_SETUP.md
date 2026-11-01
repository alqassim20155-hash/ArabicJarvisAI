# Arabic Jarvis AI 0.4.1 — Local Bridge

1. In Termux, start the already installed bridge: `~/.local/bin/jarvis-bridge start`.
2. In Termux, copy the secret locally using `cat ~/.config/jarvis-bridge/token`. Do not share the token or commit it to GitHub.
3. Open the Android app > Settings > **رمز Jarvis Bridge**. Paste the token. The app saves it on the device only.
4. Return home and tap **فحص الحالة**, then **تشغيل**.
5. `stop` currently returns a deliberate safety error until the process shutdown procedure is audited. Do not treat it as successful.

The app connects to `http://127.0.0.1:18766` only. Android network security configuration already allows loopback HTTP. No Termux RUN_COMMAND permission is requested.

Build: GitHub Actions workflow `Android Build`, or `gradle :app:assembleDebug` with Android SDK 35 and JDK 17.

Notes: App's microphone recognition is still Android speech recognition and may need network services; the local bridge does not alter that. Android may terminate Termux background processes; restart the bridge manually when needed.
