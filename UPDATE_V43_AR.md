# Arabic Jarvis AI v4.3 — Bridge pairing and Samsung navigation fixes

- Bridge token is saved synchronously (`commit`) and the request reads the persisted value.
- Settings includes paste-from-clipboard and test-Bridge controls, plus token length (never prints token).
- HTTP failure diagnostics include exception and HTTP status without exposing the secret.
- Stronger light-on-dark contrast for all Material components, including driving mode.
- Safe drawing insets keep content away from Android status and navigation bars; additional bottom spacing.
- Cleartext is restricted to loopback, preserving HTTPS LAN trust configuration.

## Installation
From `~/ArabicJarvisAI` unzip this patch, then `git diff --check`, commit, push and build with existing GitHub Actions. Enter token in settings (or copy in Termux and paste), tap **اختبار Bridge**.

**Limits:** Server stop remains disabled by Bridge; wake-word service is not implemented. APK not built or tested on the phone in this environment.
