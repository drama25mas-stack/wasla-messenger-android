# WASLA Firebase setup

The Android project uses Firebase Authentication, Cloud Firestore and Firebase Cloud Messaging.

## 1. Authentication

In Firebase Console, open **Authentication → Sign-in method** and enable **Email/Password**.

## 2. Create Firestore

Open **Firestore Database → Create database**. Choose Production mode and a region close to your users. The database must exist before rules can be deployed.

## 3. Publish security rules

Open the Firestore **Rules** tab, replace the editor contents with the repository's `firestore.rules`, then click **Publish**.

The rules protect user profiles, exact-email account lookup, private one-to-one conversations, group membership, messages and each user's notification-token records.

## 4. Deploy push-notification functions

Install Node.js 20 and Firebase CLI, then run from the repository root:

```bash
npm install --prefix functions
firebase login
firebase use wasla-e131f
firebase deploy --only firestore:rules,functions
```

Cloud Functions deployment may require the Firebase project to be on the Blaze billing plan. Review Firebase pricing and set budget alerts before enabling billing.

## 5. Android build configuration

The public GitHub repository must not contain `google-services.json`. The Android CI workflow restores it from the GitHub Actions secret named `GOOGLE_SERVICES_JSON`.

## Notes

- Email/password authentication and the Android APK build are separate from Firestore database creation and Cloud Functions deployment.
- Push notifications will not be delivered from the server until the Cloud Functions are deployed.
- Direct nearby messages use Nearby Connections and require the other device to run WASLA, grant nearby-device permissions and accept the connection.
- Nearby direct messaging is not multi-hop mesh routing.
