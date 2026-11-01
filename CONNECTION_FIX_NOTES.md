# Arabic Jarvis v4.2 connection fix

- Android cleartext policy: allow HTTP for local Bridge 127.0.0.1 (Android network-security-config base fallback).
- Bridge token trimmed in Authorization header.
- Show actual exception class/message, without printing tokens.
- Version increment for upgrade.

IMPORTANT: The base cleartext policy is broader than localhost. Current app network targets should be audited before production distribution; remote endpoints should remain HTTPS. Bridge binds localhost only and uses bearer token.

To deploy, unzip into ~/ArabicJarvisAI, git add explicit files, commit, push, rebuild GitHub Actions.
