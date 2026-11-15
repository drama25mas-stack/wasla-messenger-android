# WASLA — وصلة

**Connect beyond internet.** Android-first messenger combining a Telegram-inspired glass interface with direct offline messaging and optional internet messaging.

## Current status

Phase 1 foundation:
- Native Android app using Kotlin and Jetpack Compose.
- Arabic RTL glass-inspired navigation shell.
- App module, manifest and build configuration.
- GitHub Actions workflow to build and upload a debug APK.

**Important:** this is an early UI and build foundation. Real accounts, contact discovery/matching, message delivery, end-to-end encryption, Nearby Connections, multi-hop mesh routing, media transfer, groups and backend sync are not implemented yet. UI previews are not a working messaging service.

## Build

GitHub Actions runs the Android debug build on pushes and manual workflow dispatch. Download the WASLA-debug-apk artifact from a successful workflow run.

## Technical direction

- Kotlin, Jetpack Compose, Material 3
- Room for durable local message storage (planned)
- Nearby Connections and Android wireless transports (planned)
- Store-and-forward multi-hop relay with message IDs, TTL and deduplication (planned)
- Optional authenticated backend and WebSocket sync (planned)

## Security and privacy

Contact access will be requested at runtime and used only for transparent contact matching. The app will not upload raw address books. A production contact-discovery design and reviewed end-to-end encryption protocol are required before launch.
