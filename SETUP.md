# GoPreach — Setup

## Current status: live

This project is wired to a real Firebase project — **`gopreach-957a6`**
(console: https://console.firebase.google.com/project/gopreach-957a6/overview).

- ✅ Authentication (Email/Password) — enabled
- ✅ Firestore — database created, security rules deployed (`firestore.rules`)
- ✅ Android apps registered (`com.emfitsolutions.gopreach` and the `.debug`
  variant), `app/google-services.json` is the real config, not a placeholder
- ⏸️ **Storage is not set up** — Google now requires the Blaze (pay-as-you-go)
  plan to create a Storage bucket, which means linking a payment method. The
  app works fully without it; only the Super-Admin's Control Panel logo
  upload/replace feature needs it. To enable later: Firebase Console →
  Upgrade project → Blaze, then Storage → Get Started, then run
  `npx firebase-tools deploy --only storage --project gopreach-957a6`
  to push `storage.rules`.
- ✅ A working Super-Admin account exists (username `admin` — ask in-thread
  for the password rather than storing it here, since this file may end up
  somewhere more public than your machine)

## Bootstrapping another Super-Admin (or reference for how the first was made)

There's no in-app enrollment screen for Super-Admin — the spec has exactly
one administration root. It's created directly against Firebase:

1. Create a Firebase Auth user with email `{new-lowercase-id}@gopreach.internal`
   and a password. **The id must be lowercase-only** — Firebase Auth
   lowercases every email automatically, and [`AuthEmail.kt`](app/src/main/java/com/emfitsolutions/gopreach/data/repository/AuthEmail.kt)'s
   `personIdFromAuthEmail` has to recover the exact same id after sign-in, so
   any uppercase character breaks login permanently for that account. (See
   [`CredentialGenerator.newPersonId()`](app/src/main/java/com/emfitsolutions/gopreach/domain/CredentialGenerator.kt) —
   this is exactly why it generates lowercase-only ids.)
2. Create a `people/{that-id}` document matching [`Person`](app/src/main/java/com/emfitsolutions/gopreach/data/model/Person.kt)'s
   fields — set `username`, and `isTemporaryCredential: true` to force the
   normal first-login password-change flow (spec §4.5). **Do not include an
   `id` field in the document body** — `id` is `@DocumentId`-annotated, and
   Firestore throws on read (`toObject()`) if a document also stores a
   literal field with that name.
3. Create a `roleAssignments/{anyId}` document (same "no literal `id` field"
   rule applies): `personId` = that id, `roleType` = `"ADMIN:SUPER_ADMIN"`,
   `status` = `"ACTIVE"`.
4. Log in from the app with the username you set and that password.

Every other account (Admins, Coordinator Elders, Regular Elders, Publishers)
is created from inside the app by an existing role, per spec §4 — those
flows already avoid both pitfalls above.

## Why login is by username but Firebase Auth uses email

See [`AuthEmail.kt`](app/src/main/java/com/emfitsolutions/gopreach/data/repository/AuthEmail.kt) —
each Person gets a synthetic `{personId}@gopreach.internal` Auth address;
[`Person.email`](app/src/main/java/com/emfitsolutions/gopreach/data/model/Person.kt)
stays a separate, optional, real contact field untouched by Auth.

## Redeploying security rules after an edit

```
npx firebase-tools deploy --only firestore:rules --project gopreach-957a6
```

## Building

```
./gradlew :app:assembleDebug
```

Requires the Android SDK (compileSdk 35) and JDK 17.
