# WASLA — وصلة

**تواصل بلا حدود** — تطبيق مراسلة Android باللغة العربية، مبني بـ Kotlin وJetpack Compose.

## Implemented in the current codebase

- Email/password registration, email verification, sign-in and password reset through Firebase Authentication.
- Firestore-backed one-to-one conversations with live message updates.
- Firestore-backed group creation and live group messaging.
- Direct nearby device discovery and one-to-one offline text exchange through Google Nearby Connections, with permission prompts and connection acceptance.
- Firebase Cloud Messaging token registration, Android notification display service and Cloud Functions triggers for direct/group message notifications.
- Firestore security rules for profiles, email lookup, private chats, groups, messages and notification device tokens.
- GitHub Actions workflow that builds and uploads the Android debug APK.

## Required Firebase activation

The app source and APK build do not automatically create or deploy cloud resources. Complete the steps in [FIREBASE_SETUP.md](FIREBASE_SETUP.md): create the Firestore database, publish `firestore.rules`, and deploy Cloud Functions. Server push notifications remain inactive until functions are deployed. The repository uses the private GitHub Actions secret `GOOGLE_SERVICES_JSON`; do not commit the Firebase config file.

## Still required before calling this a production-ready final release

- Reviewed end-to-end encryption and key verification; current cloud message text is not end-to-end encrypted.
- Multi-hop mesh/store-and-forward routing; nearby messaging currently supports direct nearby connections only.
- Contact import/matching, media and file transfer, voice notes, location sharing, message reactions/replies/forwarding/pinning/search, read receipts and delivery states.
- Full runtime testing on multiple physical Android devices, offline/network transition tests, security review, and release signing.

## Build

Open the repository's **Actions** tab and download the `WASLA-debug-apk` artifact from a successful run. The debug APK is installable for evaluation; it is not a signed Play Store release.
