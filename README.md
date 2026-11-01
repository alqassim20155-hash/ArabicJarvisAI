# Arabic Jarvis AI — Android + upstream OpenJarvis bridge

This is a **working integration scaffold**, not a fork containing OpenJarvis source. Install the upstream project separately on Linux. Android sends Arabic text over HTTPS to a token-protected FastAPI bridge; the bridge executes the **real** `jarvis ask` CLI and returns its stdout. Voice input/output uses Android speech services and may require network/provider support. Upstream agents, tools and memory are controlled by your OpenJarvis configuration and are **not** independently implemented in this app.

## Server (Linux)

1. Follow upstream installation: https://github.com/open-jarvis/OpenJarvis (test `jarvis ask 'مرحبا'` in the same shell/user as the service).
2. Install Python dependencies in a separate environment: `python3 -m venv .venv && .venv/bin/pip install -r server/requirements.txt`.
3. Set `JARVIS_BRIDGE_TOKEN` to a long random token; optionally set `JARVIS_BIN` to the absolute executable path of the installed `jarvis` command.
4. Start from the repository root: `JARVIS_BRIDGE_TOKEN='your-random-token' .venv/bin/uvicorn server.bridge:app --host 127.0.0.1 --port 8765`.
5. Put Caddy or another trusted HTTPS reverse proxy in front of `127.0.0.1:8765`, or use a private VPN with valid HTTPS. Do **not** expose port 8765 directly to the Internet. Use a dedicated unprivileged service account; never run the bridge as root. Do not enable powerful OpenJarvis tools for untrusted users.
6. In Android enter the HTTPS base URL and token. The token is stored in app-private preferences for this prototype (not encrypted); avoid using this with production credentials until encrypted storage is implemented.

## Build on GitHub Actions

Upload the **contents of this archive** to the repository root (the `app` directory and `.github` must be at the top level). In Actions select Android Build; download `ArabicJarvisAI-debug` artifact and install APK.

## Tests

`PYTHONPATH=. .venv/bin/pytest server/tests -q` tests bridge auth, payload limits and subprocess command shape using mocks. An end-to-end test needs a working upstream OpenJarvis installation and model, and a reachable HTTPS URL. Android APK compilation is not verified locally; the GitHub workflow is the authoritative build test.

## Known limitations

- `jarvis ask` is invoked as a subprocess per request; conversation state persistence depends on upstream behavior and configuration.
- Not all upstream features are surfaced as Android controls. No background listening or arbitrary device control.
- HTTPS certificate must be trusted by Android. No certificate bypass.
- Verify compatibility of the upstream `jarvis ask` CLI version in your deployment before using the service.
- The app does not bundle or modify upstream OpenJarvis code; upstream Apache-2.0 license remains with its project.
