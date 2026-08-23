# GoPreach — Build Plan

Android app for congregation publisher activity tracking. Kotlin + Jetpack Compose, Firebase (Auth/Firestore/Storage) backend, offline-first via Room + WorkManager sync queue. Single app, role-based context routing (Admin context / Ministry Report context, or both if the logged-in Person holds both role types).

Package: `com.emfitsolutions.gopreach`
Min SDK: 24 · Target/Compile SDK: 35

Build style: phases run sequentially, auto-advance. At the end of each phase: full compile + lint sweep, fix everything found, then move to the next phase without stopping to ask.

## Phase 0 — Project Scaffold ✅ done
- Gradle wrapper, root/app build files, Compose + Hilt + Firebase + Room + WorkManager deps
- Package structure (data/domain/ui/di layers)
- Theme (Material 3, GoPreach brand palette), navigation graph skeleton
- App icon placeholder, "About — Powered by EMF IT Solutions, Est. 2026" screen
- Firebase project wiring stub (google-services.json placeholder + instructions)

## Phase 1 — Data Model & Offline Sync Foundation ✅ done
- Firestore schema: Person, RoleAssignment, Congregation, Group, ElderTitle, Territory, Schedule/ChatSchedule, Publisher categories, BibleStudyRecord, InterestedPerson + Visit, MonthlyReport
- Generic offline cache/outbox (Room `cached_documents` + `pending_sync_operations`) shared by every repository, instead of one Room entity per collection
- Repository layer (single source of truth, Firestore + Room), with a shared `mirrorFirestoreCollection` snapshot-listener helper
- SyncWorker (WorkManager) — flushes the pending queue on connectivity, `observePendingSyncCount()` drives a "pending sync" indicator
- `PermissionChecker` — `hasAdminRole(...)`, `isActivePublisher(...)`, `highestAdminRole(...)`

## Phase 2 — Auth & Enrollment Flows ✅ done
- Login (username/password, show/hide toggle, Forgot Password request) — `AuthRepository` maps username → a synthetic `{personId}@gopreach.internal` Firebase Auth email
- Temp-credential first-login flow: forced username+password change, re-login (`UserSession.requiresPasswordChange`)
- Reactive role-based post-login routing (Admin context / Publisher context / both) via `GoPreachNavGraph`
- Enrollment screens: Congregation (Super-Admin), Admin (Super-Admin), Coordinator Elder (Super-Admin/Admin), Regular Elder (+ElderTitle dropdown), Publisher Master File — each generates one-time temp credentials via a throwaway secondary `FirebaseApp` so it doesn't disturb the enrolling admin's session
- Shareable temp-credential link generation (`CredentialGenerator`)

## Phase 3 — Super-Admin Module ✅ done
- Manage Congregations (list/create/delete), Manage Admins (list)
- Control Panel: app logo upload/replace (Firebase Storage), shown live via `DynamicAppLogo` on every banner
- Backup & Restore: JSON export/import of the offline cache (Firestore itself is already durable — this is a portable snapshot, not disaster recovery)
- User Logs: `AuditLogEntry` + `AuditLogRepository`, instrumented on sign-in/enrollment/congregation CRUD/logo upload/backup restore; Super-Admin sees all + can delete, Admin/Coordinator Elder see their own congregation only
- Note: CRUD Coordinator Elder / Regular Elder / Publisher list-and-edit screens (beyond the enrollment "create" flow already built in Phase 2) still belong to Phase 4 below

## Phase 4 — Admin / Coordinator Elder / Regular Elder Modules ✅ done
- Publishers Master File (all categories) — list + change-category ("delete"/reactivate = recategorize to Removed/Inactive rather than erasing the record, since spec §2.2 already models those as categories)
- Groups CRUD + assign 1 Regular Elder (elder choices scoped to the group's own congregation)
- Territory Master File CRUD, optional assigned-group link
- Chat Schedule CRUD (`Schedule` model shared with Calendar, distinguished by `ScheduleKind`) — reusable `DateTimeField` component (native date+time pickers) for both
- Reports: Bible studies / interested people / hours, per publisher with an "All Publishers" summary — scoped by congregation (Admin/Coordinator Elder) or group (Regular Elder); will show real numbers once Phase 5 report submission is live
- All five screens role-scoped per the spec §3 permission matrix (Regular Elder excluded from Publishers/Groups/Territories per matrix, included for Chat Schedule/Reports at "own group" scope)

## Phase 5 — Ministry Report App (Publisher context) ✅ done
- Monthly report forms — shows only the fields required for the signed-in publisher's category (bible studies + hours for Pioneers; bible studies + Yes/No preaching participation for Publishers)
- Bible Study Record CRUD
- Interested People Records CRUD + multi-visit entries (list → detail drill-down within one screen, native date/time picker for visit logging)
- Submission lock: publisher can't edit their own report once submitted; a Coordinator/Regular Elder can, via an "Edit" affordance on the Phase 4 Reports screen (`allowEditWhenLocked`) — closes the loop spec §5.2 describes

## Phase 6 — Supporting Modules ✅ done
- Share Location — publisher toggles live sharing (foreground timer + Play Services fused location, not yet a background/foreground service — a natural follow-up); viewer list scoped per the spec §6.1 role table, "Open in Maps" hands off to any installed maps app rather than embedding a Maps SDK view (avoids requiring a Google Maps API key for this pass)
- Calendar — single chronological list (a month/week grid is a visual upgrade for later) with full view/add/edit/delete scoping per spec §6.2's role table, including Publisher personal notes (private, publisher-only)
- Polish audit: confirmed `PasswordVisualTransformation` appears only on the two legitimate password fields (Login, Forced Password Change) — nowhere else masks input, matching spec §1; banner used consistently on Login/Admin Home/Publisher Home per spec's "entry/dashboard screens" wording

## Phase 7 — Final Verification ✅ done
- Full `clean` build + lint sweep at every phase boundary throughout (not just incremental — caught a build-cache false-positive early on and disabled caching to keep verification trustworthy)
- Installed and launched on an emulator (Android SDK/AVD already present in this environment): app starts, login screen renders correctly (banner, logo, plain-text username, masked password with show/hide, Forgot Password, About) — confirmed via logcat and screenshots, no crashes
- README.md + SETUP.md with Firebase config and Super-Admin bootstrap steps

## Phase 8 — Live Firebase verification ✅ done
Wired up a real Firebase project (`gopreach-957a6`) and ran the actual login →
forced-password-change → re-login → role-scoped dashboard loop against it —
not just against local placeholders. This surfaced and fixed five real bugs
that unit-level work never would have caught:

1. **`personId` case sensitivity** — Firebase Auth lowercases every email, but
   the id embedded in it (from Firestore's own mixed-case auto-ids) wasn't
   lowercase, so login could never recover the right id after sign-in.
   Fixed with [`CredentialGenerator.newPersonId()`](app/src/main/java/com/emfitsolutions/gopreach/domain/CredentialGenerator.kt)
   (lowercase-only ids), used everywhere a new Person id is minted.
2. **`@DocumentId` + a literal `id` field collide** — every write went through
   Gson, which serializes the whole object including `id`; Firestore throws
   on `toObject()` when a `@DocumentId`-annotated property's name also exists
   as real document data. This silently broke every model's remote reads.
   Fixed in [`SyncWorker`](app/src/main/java/com/emfitsolutions/gopreach/data/sync/SyncWorker.kt) by stripping `id` before every write.
3. **Firestore's Kotlin `is`-prefix mapping bug** — `Person.isTemporaryCredential`
   and `SharedLocation.isSharing` silently failed to deserialize via
   Firestore's native mapper (Gson-based local reads were unaffected). Fixed
   with explicit `@get:PropertyName(...)` annotations.
4. **Forced-password-change sign-out race** — the username/password update
   queued through the normal offline-sync path, but signing out immediately
   after could beat that queued write to the server, permanently stranding it
   against security rules that require an authenticated session. Fixed by
   writing that specific update synchronously before signing out.
5. **Remote sync listeners were never started** — every repository had a
   `startRemoteSync()` Firestore listener, but nothing ever called them, so
   the offline cache only ever reflected what *that device* itself had
   written — anything created elsewhere (another admin's device, or in this
   case a directly-provisioned account) never synced down. Fixed with
   [`RemoteSyncCoordinator`](app/src/main/java/com/emfitsolutions/gopreach/data/sync/RemoteSyncCoordinator.kt), started once from `GoPreachApp.onCreate()`.

Also deployed `firestore.rules` (authenticated-read/write baseline, with a
carve-out for username→email lookup at login, which necessarily happens
before the user is authenticated) and `storage.rules` (written, not yet
deployed — see SETUP.md on the Blaze-plan requirement).

## Phase 9 — UI/UX polish, theming, icon ✅ done
- **Icon-grid dashboards** — `DashboardTile`/`DashboardSection` replace the long column of outlined buttons on both Admin Home and Publisher Home with a clean, sectioned grid (Management/Ministry/Enrollment/System, or My Ministry/Account) — same role-gating as before, just organized like a real app dashboard instead of a settings list
- **Light/dark theme, explicit and instant** — `ThemePreferenceRepository` (SharedPreferences-backed) + a Settings screen (System default/Light/Dark radio group), wired through `MainActivity` into `GoPreachTheme`; switching applies immediately, no restart. Dynamic (wallpaper-based) color is now off by default so the app's own brand palette is consistent across devices instead of shifting with the user's wallpaper
- Filled out both color schemes with `onSurface`/`surfaceVariant`/`outline` tokens so cards, dividers, and text keep proper contrast in both themes, not just `primary`/`background`
- **Refined app icon** — gradient background (brand blue) + a cleaner two-page book mark, still a placeholder per spec (Super-Admin can replace it at runtime via Control Panel), but a more polished default
- Fixed the Super-Admin congregation-picker gap: creating a Group or Territory as Super-Admin previously failed silently (Groups) or didn't even open the dialog (Territories) because there was no congregation selector; both now show one when needed, reusing the same dropdown pattern from enrollment
- Verified all of the above live on-device (light theme, dark theme, icon, dashboard grids, no crashes) — not just a clean build
- **Login screen redesign** — matched to the requested reference mockup: a rounded-bottom gradient hero panel (`GradientHero`) with soft translucent blobs + diagonal streak texture, app logo + "GoPreach"/"Ministry Activity Tracking" on the hero, restyled Username/Password fields (leading icons, 16dp rounded corners) below on a plain surface, and a raised pill-shaped "Log In" button. No fields/buttons were added beyond what the app actually does (no email/sign-up/social login, since there's no self-registration or social auth in the spec).
- **Real app icon** — replaced the placeholder book glyph with the client-supplied "GP" monogram badge (`751cb5fc-8c1a-402e-871a-fe927b92ef91.jpeg`): generated legacy launcher PNGs at all 5 densities plus an adaptive-icon foreground (badge inset in the safe zone over a matching black background layer) via a one-off `sharp` script. Verified on-device on the home screen (renders as a clean circular badge, no clipping) and confirmed the login screen redesign side-by-side in the same pass.

## Post-launch fix — role sync silently dying after login
Found via a live repro (Super-Admin login showing "Signed in as Unknown
role" despite a correct `roleAssignments` doc server-side): Firestore
snapshot listeners registered before sign-in against any rule-gated
collection die permanently on their first `PERMISSION_DENIED` and never
emit again, even after the user later authenticates. `RemoteSyncCoordinator`
started every listener once at process boot — before any auth existed — so
only the one publicly-readable collection (`people`) ever actually stayed
synced; everything else (`roleAssignments` included) silently never
recovered. Fixed by re-subscribing every listener on every Firebase Auth
state change instead of once ([`RemoteSyncCoordinator`](app/src/main/java/com/emfitsolutions/gopreach/data/sync/RemoteSyncCoordinator.kt));
[`FirestoreMirror`](app/src/main/java/com/emfitsolutions/gopreach/data/sync/FirestoreMirror.kt)
now also logs listener errors instead of swallowing them. Verified live:
Super-Admin login now correctly shows the full role-gated dashboard.

## Phase 10 — In-app auto-update + APK sharing ✅ done (pending on-device verification)
Full update/distribution system, built on GitHub Releases as the "update
server" rather than standing up bespoke backend infrastructure — GitHub's
free, public `releases/latest` API already provides exactly what an update
manifest needs (version/tag, a stable per-release download URL, release
notes, release date, and a SHA-256 digest of the asset), and GitHub's CDN
already hosts the APK bytes. See
[`UpdateManifestRepository`](app/src/main/java/com/emfitsolutions/gopreach/data/update/UpdateManifestRepository.kt)'s
doc comment for the reasoning.

- [`UpdateManifestRepository`](app/src/main/java/com/emfitsolutions/gopreach/data/update/UpdateManifestRepository.kt) —
  fetches + parses the manifest, numeric (not lexicographic) version comparison.
- [`ApkDownloader`](app/src/main/java/com/emfitsolutions/gopreach/data/update/ApkDownloader.kt) —
  streams the APK into the app's private external-files dir with live
  progress, plus a SHA-256 helper to verify against the manifest's digest.
- [`UpdateInstaller`](app/src/main/java/com/emfitsolutions/gopreach/data/update/UpdateInstaller.kt) —
  hands the verified APK to Android's own Package Installer via a
  `FileProvider` URI; checks `canRequestPackageInstalls()` first and routes
  to the OS's own "allow this app" setting if needed. This app never installs
  anything itself — Android's installer is what actually enforces "an update
  must be signed with the same certificate as what's already installed,"
  which is the real security guarantee here, not something this code
  reimplements or could bypass.
- [`UpdateViewModel`](app/src/main/java/com/emfitsolutions/gopreach/ui/components/update/UpdateViewModel.kt) +
  [`UpdateHost`](app/src/main/java/com/emfitsolutions/gopreach/ui/components/update/UpdateDialog.kt) —
  the full state machine (Checking → Available → Downloading → Verifying →
  ReadyToInstall → handed to the OS, or Failed → Try Again), mounted once at
  the app root (`MainActivity`) so it works whether or not the user is signed
  in yet. Checked once per app launch, silently — a check that finds nothing
  new shows nothing at all, so a normal launch is never interrupted. A
  Settings → "Check for Updates" entry runs the same check on demand and
  explicitly shows "GoPreach is up to date" when applicable.
- **Share APK**: `Available`'s dialog has an [SHARE APK] action that opens
  Android's native share sheet with the *current* latest APK URL (fetched
  moments earlier, never hard-coded), matching the spec's example message.
- A failed download/verification never touches the existing installation —
  the in-progress download is discarded, a "your current version is still
  available" message is shown, and Try Again re-attempts from the Available
  state.
- Existing data preservation: this is a normal Android app update (same
  applicationId, same signing key), which is exactly the case Android's own
  update mechanism (not a fresh install) is designed to preserve app data
  and settings across — nothing here needed to reimplement that.
- Publishing v1.2.0+: see SETUP.md's new "Publishing a new version" section —
  bump the version, build, cut a real new GitHub release tag. Nothing in the
  app changes for this.

**Known gap, called out rather than silently left**: the app is currently
**debug-signed** (no dedicated release keystore exists in this project yet).
In-place auto-updates work correctly between debug-signed builds made from
this same machine, but a real production rollout should set up a proper
release signing config first — see SETUP.md's signing note.

**Verified on-device** (emulator, `MiarhPen_Test` AVD): installed 1.0.0,
confirmed the Update Available dialog showed the correct Current/New version
text and real release notes, confirmed SHARE APK opened the native share
sheet with the live "latest" GitHub link, confirmed UPDATE NOW downloaded and
SHA-256-verified the APK, correctly routed to Android's own "Install unknown
apps" settings screen when that permission was missing, then handed off to
the real system Package Installer (which showed its own "Do you want to
update this app?" confirmation using GoPreach's own icon) and completed the
in-place install — `dumpsys` confirmed the running app was genuinely 1.1.0
afterward, with no data loss and no re-prompt.

## Phase 11 — Super-Admin Account and User Access Management ✅ done

- **Super-Admin self-service**: a new "Account Settings" screen (reachable by
  any signed-in Admin-track user, not just Super-Admin) lets you change your
  own username and password, each requiring your *current* password first
  (Firebase `reauthenticate()`). Changing the password signs you out
  afterward, same as the existing forced-password-change flow. Neither ever
  touches a plaintext password in Firestore — Firebase Auth's own hashed
  credential store is what actually holds and verifies it.
- **Role + Permission + Scope, not a role dropdown**: a new `AdminRole.CIRCUIT_OVERSEER`
  carries *no* built-in access of its own — everything it (or any future
  "custom user") may do lives in a separate `UserAccessGrant` document:
  `Permission` (16 cases — View/Add/Edit/Delete Congregations, View/Manage
  Elders, View/Manage Groups, View/Manage Publishers, the three report-view
  permissions, Print/Export Reports, Manage Users) crossed with `ScopeType`
  (All Congregations / Selected Congregations today; Selected Groups is
  modeled and enforced but not yet exposed in the picker UI).
- **User Management module**: a new screen (Super-Admin, or an Admin
  explicitly granted `MANAGE_USERS` — spec's "Admin can manage users only if
  explicitly authorized") lists every Circuit Overseer/custom user, with
  [Add User] generating temp credentials + a permission/scope form, and
  per-user Edit (same form) plus a three-state Active/Inactive/Suspended
  status control that's account-level, not role-level (spec §9) — a
  deactivated account can never sign in again, but nothing about it is
  deleted. Deactivating/suspending someone **mid-session** also signs them
  out immediately (`SessionState.isAccountBlocked`, checked reactively in
  `GoPreachNavGraph`), not just on their next login attempt.
- **Real backend enforcement, not just a hidden button**: `firestore.rules`
  now re-derives every restricted user's permission+scope check server-side
  from their own `userAccessGrants/{personId}` document (see that file's
  updated header comment for exactly which collections and why) — a
  restricted user hitting the Firestore API directly, bypassing the Android
  UI entirely, gets rejected the same way the UI would have refused to show
  the button. This is additive only: the four pre-existing built-in roles'
  authorization model is unchanged (still UI/PermissionChecker-enforced, a
  pre-existing, documented trade-off — see the rules file), since a
  restricted user is a brand-new kind of account this phase introduces and
  nothing that worked before can regress by correctly restricting it now.
- **Audit trail**: every username change, password change, new restricted
  user, permission/scope edit, and status change writes an `AuditLogEntry`
  with a human-readable `details` string (e.g. `"status: ACTIVE -> SUSPENDED"`),
  visible in the existing User Logs screen.
- Full name/username edits for *existing built-in-role* people (Admins,
  Elders, Publishers) were already covered by the earlier Add/Edit/Delete
  pass — this phase only adds the new restricted-role account type on top.

**Known, deliberate scope cuts (disclosed, not silently skipped)**:
- The "View Congregations / View Elders / View Groups / View Publishers"
  permissions are modeled, stored, and enforced server-side, but there's no
  dedicated *read-only* variant of the existing Congregations/Elders/Groups/
  Publishers management screens yet — building four read-only screen variants
  was out of scope for this pass. A restricted user granted only "View X" has
  nothing in the nav today that routes them to those screens at all; the
  Reports-based permissions (View Publisher/Group/Congregation Reports, the
  headline capability in every one of the spec's own worked examples) are
  fully wired end-to-end instead.
- `ScopeType.SELECTED_GROUPS` is fully modeled and enforced by
  `PermissionChecker`/`firestore.rules`, but the Add/Edit User screen's scope
  picker only exposes All Congregations / Selected Congregations today.
- The denormalized `Person.isSuperAdmin` flag that the security rules check
  must be set **by hand**, once, on any Super-Admin account created before
  this phase (see SETUP.md's updated bootstrap steps) — there's still no
  in-app "create another Super-Admin" flow, by design.

## Phase 12 — Interested Person supporting-place image capture ✅ done

- **Capture / Change / Clear**, all in one reusable `SupportingImageSection`
  composable (used from both the "New Interested Person" and "Edit
  Interested Person" dialogs — there was no edit dialog for a person's own
  fields at all before this phase, only Add + Delete, so this phase adds real
  editing too, not just the image part of it): [Capture Image] opens the
  device camera (`ActivityResultContracts.TakePicturePreview`, with a runtime
  `CAMERA` permission request first), shows the captured shot in an inline
  preview with **Use Photo** / **Retake Photo**, and only calls back into the
  form's state on "Use Photo" — nothing is saved to the record until the
  surrounding dialog's own Save/Add is tapped. [Change Image] is the same
  capture flow layered over an existing image, and per spec §6 the old image
  stays exactly where it is (in the dialog's local state) until a *new* one
  is confirmed — a cancelled or abandoned recapture can never lose what was
  already saved. [Clear Image] shows the exact confirmation copy from the
  spec, then empties the image slot back to the "No supporting image" +
  [Capture Image] empty state.
- **Storage**: the compressed JPEG (downscaled to ≤1024px, quality 60 — a
  few hundred KB at most) is Base64-encoded directly into a new
  `InterestedPerson.supportingImages` field, **not** Firebase Storage — this
  project's Storage bucket isn't provisioned (still needs the paid Blaze
  plan, see SETUP.md). Embedding it in the same Firestore document instead
  means it rides the existing offline-first Room-cache + Firestore-sync path
  with zero new plumbing, inherits the InterestedPerson record's own access
  control automatically (spec §9's "same security rules as the record"), and
  has no separate download URL of any kind to leak (spec §9's other
  requirement) — there's nothing to fetch independently of the record.
- **Future-ready structure** (spec §8): `SupportingImage` carries a `type`
  field (`SupportingImageType`: HOUSE/GATE/LANDMARK/MEETING_PLACE/OTHER) and
  `InterestedPerson.supportingImages` is already a list — today's UI only
  ever reads/writes the first entry (`primarySupportingImage`), so adding a
  real multi-image picker later is a UI change on top of an already-correct
  data model, not a migration.
- New `CAMERA` runtime permission + `<uses-feature ... required="false">` (so
  the app still installs on a camera-less device/emulator) in the manifest.

**Known gap, disclosed rather than skipped**: not verified on a physical
device's real camera this pass — compiles and installs cleanly, and the
emulator's simulated camera exercises the same code path, but there's no
Publisher test account credential available in this session to walk the
actual capture → preview → confirm → save round-trip end-to-end on-device.
Recommended before relying on this in the field: sign in as a Publisher, add
an Interested Person, capture an image, back out and reopen the record to
confirm it persisted, then exercise Change Image and Clear Image the same way.

## What's next (not blocking, tracked for a future pass)
- Storage: needs the Blaze plan (billing) to provision a bucket — your call, see SETUP.md
- Share Location: move from a foreground timer to a real background/foreground service for continuous tracking
- Calendar: upgrade the chronological list to a month/week grid
- Backup & Restore: this is a JSON snapshot of the offline cache, not a true DB backup — fine as a safety net, but call this out to users so expectations are set correctly
- Harden `firestore.rules` further for the four original built-in roles too (needs Cloud Functions + custom claims, or a denormalized roles field, to check specific role/scope per write — noted inline in the rules file; the new restricted-role checks added in Phase 11 don't need this, since they're already keyed by personId)
- Set up a dedicated release signing keystore so future versions aren't distributed debug-signed
- A dedicated "My Group / My Congregation" summary card on the Elder's dashboard home screen (the underlying access already works via Reports, per the Elder Roles work — just not its own dashboard widget yet)
- Read-only variants of the Congregations/Elders/Groups/Publishers management screens, so a restricted user's "View X" permission (without the matching "Manage X") has somewhere in the nav to actually go
- Expose `ScopeType.SELECTED_GROUPS` in the Add/Edit User scope picker
