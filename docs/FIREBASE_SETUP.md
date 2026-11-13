# Firebase Authentication & Cloud Firestore Setup Guide

Multi Calculator currently operates in **Offline-First Local-Only Mode** using Room Database and lossless JSON Backup/Restore. Follow these steps when you are ready to enable optional **Google Account Cloud Backup & Cross-Device Sync** with Firebase.

## 1. Create a Firebase Project
1. Open the [Firebase Console](https://console.firebase.google.com/) and create or select a project.
2. Register an Android app with package name:
   `com.aistudio.multicalc.kxpqzm` (or your final production `applicationId`).
3. Add both your **Debug SHA-1 / SHA-256** and **Play App Signing / Upload Key SHA-1 / SHA-256** fingerprints.
4. Download `google-services.json` and place it at `app/google-services.json` (**do not commit it to public repositories**).

## 2. Enable Products
1. **Authentication** → Sign-in method → Enable **Google** provider.
2. **Cloud Firestore** → Create database → Choose region → Deploy zero-trust security rules.

## 3. Recommended Zero-Trust Security Rules (`firestore.rules`)
```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /{document=**} { allow read, write: if false; }

    match /users/{userId}/notes/{noteId} {
      allow read, delete: if request.auth != null && request.auth.uid == userId;
      allow create, update: if request.auth != null
        && request.auth.uid == userId
        && request.resource.data.title is string
        && request.resource.data.title.size() <= 200
        && request.resource.data.content is string
        && request.resource.data.content.size() <= 100000
        && request.resource.data.isPinned is bool;
    }

    match /users/{userId}/history/{entryId} {
      allow read, delete: if request.auth != null && request.auth.uid == userId;
      allow create, update: if request.auth != null
        && request.auth.uid == userId
        && request.resource.data.expression is string
        && request.resource.data.result is string;
    }
  }
}
```

## 4. Enable Dependencies in `app/build.gradle.kts`
Uncomment:
- `implementation(platform(libs.firebase.bom))`
- `implementation(libs.firebase.firestore)`
- `implementation(libs.firebase.auth)`
- `implementation(libs.androidx.credentials)`
- `implementation(libs.androidx.credentials.play.services)`
- `implementation(libs.googleid)`

Implement Google Sign-In via Jetpack `CredentialManager` (`GetSignInWithGoogleOption`) using `R.string.default_web_client_id`, and remember to clearly disclose to users that cloud availability depends on Firebase service and account retention policies rather than permanent lifetime guarantees.
