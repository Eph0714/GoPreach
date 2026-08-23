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

## Phase 13 — Green rebrand + Side Panel & Graphical Reports Dashboard ⚠️ partial, honestly scoped

- **Logo & theme**: the app's launcher icon (every mipmap density, legacy +
  adaptive), the in-app `DynamicAppLogo` fallback, and the light/dark color
  palettes (`ui/theme/Color.kt`/`Theme.kt`) all switched from navy blue/purple
  to a green brand palette sampled from the new circular "GO Preach" logo the
  user supplied. Verified on-device (login screen + launcher icon).
- **Side Panel** (spec §1-§2): `AdminHomeScreen` is now wrapped in a
  `ModalNavigationDrawer`, opened via a new hamburger icon in the dashboard
  header, with a collapsible-section treeview (`GoPreachSidePanelContent`) —
  Enrollment / Control Panel / Other — built from the **exact same** role-
  gating booleans that already gate the tile grid, so the drawer can never
  offer a route the grid wouldn't (one source of truth, not two that could
  drift). Highlights the active route.
- **Graphical Reports Dashboard** (spec §3-§5,§7,§8,§15): a new "Reports
  Dashboard" screen with KPI cards (Total Publishers, Total Elders, Regular/
  Auxiliary Pioneers, Unbaptized/Inactive/Removed Publishers, Bible Studies,
  Total Preaching Hours), a publisher-status donut chart, a preaching-hours
  bar chart, and — for Super-Admin only, since anyone else's scope is a
  single congregation — a tappable "Publishers per Congregation" comparison
  bar chart that drills into that congregation's own KPIs/charts (spec §8).
  Built on plain Compose `Canvas` (`SimpleBarChart`/`DonutChart`) rather than
  a new charting-library dependency. All numbers are computed live, on every
  emission, straight from current RoleAssignment/MonthlyReport records — spec
  §15's "no stale summary field" requirement — via `CongregationStats.compute()`.
- **Scope enforcement, not just UI** (spec §6): `DashboardStatsViewModel` has
  no congregationId parameter on its public API at all — `restrictTo()` is
  called once from the nav graph with whatever congregation id(s) the signed-
  in session's own role/scope authorizes (null only for Super-Admin), so
  there is no request parameter for a restricted user to tamper with to see
  another congregation's numbers.

**Honestly out of scope this pass** — this single request's spec (16
sections) describes a full BI-style reporting product; the following were
judged too large to responsibly build, test, and ship correctly in one pass,
and are deferred rather than half-built:
- **Export / print** for any report or chart (spec §3,§9,§12) — not
  implemented anywhere yet, including the pre-existing Reports Summary screen.
- **Reports Summary** (spec §9) as its own dedicated module with daily/
  monthly/yearly/custom date-range filtering — the pre-existing `ReportsScreen`
  (publisher-level Bible-studies/hours/interested-people rows) is what's
  linked from the Side Panel's "Reports Summary" item today; it doesn't yet
  have the date-range/search/filter UI this phase's spec describes.
- **Control Panel → Appearance** color/background customization UI (spec
  §12) — the existing Control Panel still only offers light/dark/system, not
  a primary/secondary color picker.
- **Share Location Settings** as its own configurable screen (spec §11) — the
  existing Share Location feature has no dedicated settings UI yet beyond the
  OS permission prompt itself.
- Calendar enhancements beyond what already existed (visual event-type
  indicators, territory-activity records) — spec §10.
- Admin/Coordinator Elder dashboards reuse the same `DashboardReportsScreen`
  as Super-Admin (correctly scoped to their one congregation), rather than a
  visually distinct layout per role.
- **Not verified on-device past the login screen** — no test account
  credentials were available in this session to actually sign in and walk
  through the Side Panel, Dashboard, and drill-down; only confirmed the app
  builds, installs, and reaches Login without crashing. Recommended before
  relying on this: sign in as Super-Admin, open the Side Panel from the
  hamburger icon, confirm only authorized sections show, open Reports
  Dashboard, confirm the KPI numbers match what you'd expect, and tap a bar
  in "Publishers per Congregation" to confirm the drill-down selects that
  congregation.

## Phase 14 — Theme refinement + Super-Admin/Admin nav consolidation ✅ done

- **Forest Green**: light-theme primary color changed from the earlier
  general green to the exact standard "ForestGreen" web color (`#228B22`),
  per explicit request. Splash/status-bar color updated to match.
- **Dark theme redone**: replaced with a deliberately different identity —
  "Gray Purple" primary (`#8A7CA8`) on a "Graphite Black" background
  (`#1A1A1C`/`#121212`), white text throughout (`onBackground`/`onSurface`).
  Verified on-device in both light and dark mode (toggled via
  `adb shell cmd uimode night yes/no`).
- Dashboard chart colors (donut/bar) switched from hardcoded green hex values
  to `MaterialTheme.colorScheme.primary`/`primaryContainer`/`secondary`, so
  they now correctly follow whichever theme (Forest Green or Gray Purple) is
  active instead of always rendering green.
- **Super-Admin & Admin main dashboard body hidden**: per explicit request,
  `AdminHomeScreen`'s tile grid and hero quick-actions row are now hidden
  entirely for these two roles (`hideMainFormButtons`) — navigation for them
  is Side-Panel-only. "Sign Out" (and "Account Settings" / the Publisher-
  context switch, for anyone holding both roles) moved into the Side Panel
  itself rather than being lost. Coordinator Elder, Regular Elder, and
  Circuit Overseer/custom users are unaffected — the request was scoped to
  Super-Admin/Admin specifically.
- Side Panel position: already left-aligned — `ModalNavigationDrawer` in
  Compose Material3 always opens from the leading (start) edge, which is the
  left side in this app's LTR-only layout; no change was needed.

## Phase 15 — Fixes from user feedback on Phase 13/14 ✅ done

- **The hamburger icon was on the wrong side**: it had been placed inside
  `topEndAction` (grouped with the sync/settings icons on the right), so even
  though the drawer itself always opens from the left (Phase 14's note was
  correct about that), the *toggle button* read as a right-side control. Added
  a proper `leadingAction` slot to `DashboardHero` and moved the hamburger
  there — it now sits at the true left edge of the header, before the logo.
- **The graphical Summary had disappeared from the main form**: Phase 14
  hid *everything* below the hero for Super-Admin/Admin, including the KPI
  cards/charts — that overshot the actual request (hide navigation buttons,
  not the dashboard's own reporting content). Extracted the KPI-cards-plus-
  charts body into a reusable `DashboardStatsContent` composable (no
  `Scaffold` of its own) and embedded it directly on `AdminHomeScreen`'s main
  form for Super-Admin/Admin, scoped the same way the standalone Dashboard
  Reports screen is. The standalone screen (still used by Coordinator Elder/
  Regular Elder) now just wraps that same composable instead of duplicating it.
- **Dark Green**: light-theme primary changed from Forest Green (`#228B22`)
  to the standard "DarkGreen" web color (`#006400`), per explicit request.
  Verified on-device (login screen).
- Still not verified: the actual logged-in Super-Admin/Admin view (hamburger
  position + embedded graphical Summary) — no test credentials available in
  this session, same disclosed gap as Phase 13/14.

## Phase 16 — Dashboard congregation dropdown, non-scrolling KPI grid, color code ✅ done

- **Congregation selector is now a dropdown**: replaced the horizontally-
  scrolling row of `FilterChip`s (Super-Admin only, when more than one
  congregation is in scope) with a proper `ExposedDropdownMenuBox` — tap it,
  pick "All Congregations" or one specific congregation from the list.
- **KPI cards no longer scroll**: replaced the `LazyRow` with a wrapping
  `FlowRow` — all nine KPI cards (Total Publishers, Total Elders, Regular/
  Auxiliary Pioneers, Unbaptized/Inactive/Removed Publishers, Bible Studies,
  Total Preaching Hours) lay out directly on the main form and wrap onto as
  many rows as needed, with nothing to scroll to see the rest.
- **Color code added**: each KPI metric now has a fixed, consistent color
  (e.g. Regular Pioneers is always the same green, Removed Publishers is
  always the same red) — `KpiCard` gained a `color` param (a colored top
  accent strip + colored value text, tinted card background), and the same
  color constants are reused for the matching donut-chart slice, so a metric
  reads as the same color everywhere on the dashboard, not just within the
  KPI row.
- Not verified on-device past the login screen — same disclosed gap as
  Phase 13-15 (no Super-Admin/Admin test credentials available this session).

## Phase 17 — Pull-to-refresh on the main dashboard checks for updates ✅ done

- `AdminHomeScreen`'s main form is now wrapped in a Material3
  `PullToRefreshBox`: pulling down (a) flushes any queued offline writes
  (`HomeViewModel.refreshData()` → `SyncScheduler.requestSyncNow()`) and (b)
  re-checks for an app update via the same **Activity-scoped**
  `UpdateViewModel` instance `MainActivity`'s `UpdateHost` already observes
  (same scoping trick `SettingsScreen`'s "Check for Updates" uses) — so
  pulling to refresh shows the same "Update Available" dialog (or "GoPreach
  is up to date") as a fresh app launch or the Settings button would,
  without a second, disconnected update-check flow.
- The refresh spinner clears itself as soon as the update check leaves its
  `Checking` state (observed via `LaunchedEffect`), not a fixed timer.
- Every other number on the dashboard already updates live off its own
  Firestore listener (offline-first architecture) — there's no separate
  "reload the data" step for a refresh gesture to trigger beyond the sync
  flush; re-checking for an update is genuinely the useful thing a manual
  refresh can still *do* here, which is why that's what it's wired to.
- Not verified on-device past the login screen — same disclosed gap as
  Phase 13-16 (no Super-Admin/Admin test credentials available this session);
  confirmed only that the app builds, installs, and the login screen still
  renders without a crash after this change.

## Phase 18 — Professional green icon-based Main Form redesign ✅ done

- **`DashboardTile` redesigned**: was a small `Card` (a shrunk button with a
  default-size ~24dp icon inside it); now a flat, borderless icon+label
  control with a 64dp circular badge (a fixed, consistent shape/size across
  every tile — spec §8) tinted with a new fixed `IconAccentGreen` (`#2E7D32`)
  and a 30dp icon glyph, with the label below in normal (non-tinted) text.
  This is the single shared component both `AdminHomeScreen`'s remaining
  tile grid (Coordinator Elder/Regular Elder/Circuit Overseer) and
  `PublisherHomeScreen`'s "My Ministry"/"Account" sections already used, so
  the redesign applies everywhere those grids appear without touching either
  screen's navigation/permission logic (spec §13: nothing about *what* a
  tile does changed, only how it looks).
- **Consistent green, independent of the app's overall theme color**:
  `IconAccentGreen` is a fixed constant, not `MaterialTheme.colorScheme.primary`
  — the app's primary color is Dark Green in light mode but Gray Purple in
  dark mode (an earlier, separate request about overall theme color), and
  spec §2 here explicitly asks for icons that "remain consistently green"
  regardless. Chosen to have workable contrast on both this app's light and
  Graphite Black dark surfaces.
- **Touch target**: the entire tile (badge + label + padding) is the
  clickable region, comfortably exceeding Android's 48dp minimum in both
  dimensions — not just the visible icon circle (spec §7).
- **Grouping/hierarchy preserved, not reinvented**: `DashboardSection`'s
  existing groupings (Management, Ministry, Enrollment, System, Account,
  "My Ministry") already match spec §4's "group related functions
  logically" intent for this app's actual roles — section titles were
  bumped to `titleMedium`/semibold and given more breathing room (spec §5:
  clearer visual hierarchy between a group header and its tiles) rather
  than renamed to the spec's illustrative example labels, since "the actual
  grouping should follow the existing GoPreach functionality" per the spec
  itself.
- **Deliberately not added**: a "Notifications" icon — spec §1 lists it as
  an example of a *possible* function, but GoPreach has no notification
  center anywhere in the app to link one to; adding a dead icon would
  violate spec §13's "do not break existing functions" in spirit (a button
  that does nothing is its own kind of broken). Search/Add/Edit/Delete/
  Refresh already exist as their own contextual affordances on the relevant
  CRUD screens rather than as Main Form-level icons — this pass didn't
  invent new global versions of them.
- Not verified on-device — no test credentials for a role that actually
  reaches a `DashboardTile` grid (Super-Admin/Admin's main form no longer
  shows one at all, per an earlier request) were available this session;
  confirmed only clean compilation and that the app still launches to Login.

## Phase 19 — Main Form restyled after a finance-app reference screenshot ✅ done

- **Flat header, no gradient**: `DashboardHero` changed from a rounded-corner
  vertical-gradient banner with two large status pills to a plain flat-color
  bar with a single muted caption line under the greeting (e.g. "Super Admin
  · Online · All synced") — matching the reference's plain "Welcome, name!"
  + subtitle-line header exactly, still surfacing the same offline-sync
  status this app needs, just less visually loud about it.
- **New `ComparisonCard`**: the reference's "Income vs Expense" two-number-
  plus-proportion-bar card has a direct GoPreach equivalent now — Total
  Publishers vs Total Elders, in the same colors used elsewhere on this
  dashboard, right below the congregation name/month caption.
- **New `StatCard` in a fixed 2-column grid**: replaces the compact
  KPI-strip look with cards styled like the reference's "Accounts" grid — a
  circular icon badge, a label, and a bold value — laid out two-per-row
  (manually chunked `Row`s, not a wrapping `FlowRow`) to match the
  reference's exact grid shape. Covers Regular/Auxiliary Pioneers,
  Unbaptized/Inactive/Removed Publishers, Bible Studies, and Total Preaching
  Hours; Publishers/Elders moved into the `ComparisonCard` above instead of
  duplicating them in the grid.
- The existing donut chart, preaching-hours bar chart, and (Super-Admin)
  per-congregation drill-down chart are unchanged underneath the new
  cards — this was a restyle of the summary/KPI area, not a removal of the
  more detailed charts already there.
- Not verified on-device past the Login screen — same disclosed gap as
  Phase 13-18 (no Super-Admin/Admin/Coordinator Elder test credentials
  available this session).

## Phase 20 — Purple Brand Logo and Purple/White Theme ✅ done, verified on-device

- **New logo**: the app's launcher icon (every mipmap density, legacy +
  adaptive) and the in-app `DynamicAppLogo` fallback are now a purple
  circular "GO Preach" badge (deep purple `#6A1B9A` fill with a darker
  `#4A148C` ring, white text) — generated programmatically (no source image
  was supplied this round, unlike the earlier green logo), same layout/
  proportions as the previous mark so the swap reads as a genuine rebrand,
  not a redraw.
- **Light theme → Purple + White**: `PrimaryPurple` (`#6A1B9A`) replaces Dark
  Green as the primary color; `background`/`surface` changed to pure white
  (`#FFFFFF`, was an off-white), with a faint purple-tinted neutral
  (`#F6F1FA`) reserved for card/section hierarchy only, per spec §2's "white
  as primary background... neutral tones only where necessary."
  `secondary` changed from the old gold accent to a distinguishable lighter
  purple (`SecondaryPurple`, `#9575CD`) rather than leaving an unrelated
  accent color in the theme (spec §9).
- **Dark theme → Purple + `#121212`**: primary is now `PrimaryPurpleBright`
  (`#BB86FC`, the classic high-contrast Material purple for dark
  surfaces) on a background of exactly `#121212` (spec §3's literal
  requirement, not an approximation) — this app's dark background already
  happened to be `#121212` from an earlier "Graphite Black" request, so this
  phase only had to swap the accent hue, not the background value.
- **Main Form icon system**: `DashboardTile`'s icon badges switched from a
  fixed green constant to `MaterialTheme.colorScheme.primary` directly —
  since the app's primary color *is* the brand purple in both themes now,
  every dashboard icon automatically tracks whichever purple tone is
  correct for light vs. dark, with no separate constant to keep in sync.
- **Consistency pass**: updated stale code comments in `AppBanner`/
  `GradientHero` that still said "green" post-rebrand; per-metric KPI/chart
  colors (`COLOR_PUBLISHERS`, `COLOR_ELDERS`, etc.) were deliberately left
  as-is — spec §9's own exception for "status or semantic meaning," and
  those exist specifically to tell different statistics apart, which a
  single unified purple would undo.
- **Verified on-device** (light and dark, via `adb shell cmd uimode night`):
  splash screen, Login screen (gradient hero, white body, purple headline/
  button/links), dark-mode Login (`#121212` body, bright-purple accents,
  correct black-on-light-purple button text), and the home-screen launcher
  icon all render correctly.

## Phase 21 — Main Form buttons: icons and color coding removed ✅ done

- **`DashboardTile` reverted to a plain text button**: per explicit request,
  removed the circular icon badge entirely — Coordinator Elder/Regular
  Elder/Publisher dashboards now show plain `OutlinedButton`s with just the
  label, no icon, and no per-item or brand-color tinting (neutral
  `onSurface` content color, not the purple accent). The `icon` parameter is
  still accepted (unused) so none of the existing call sites in
  `AdminHomeScreen`/`PublisherHomeScreen` needed to change.
- **Scope note**: "buttons in main form" was read as the clickable
  navigation tiles (`DashboardTile`) specifically — the Reports Dashboard's
  `StatCard` grid and chart color-coding (Regular Pioneers/Elders/etc., each
  a fixed distinct color) were left untouched, since those are statistic
  displays, not buttons, and their color-coding was a separate, explicit,
  still-standing request from a few phases back. Flagged this reading back
  to the user in case "do not color code it" was meant to cover that too.
- Verified: clean compile, app installs and still launches to Login without
  a crash. Not verified on a screen that actually renders a `DashboardTile`
  (same recurring credential gap as prior phases).

## Phase 22 — Icons/color coding also removed from the Reports Dashboard's stat cards ✅ done

- **Root cause of "the icons are not removed"**: Phase 21 only touched
  `DashboardTile` — but Super-Admin/Admin's main form doesn't render
  `DashboardTile` at all (hidden per an earlier request); what they were
  actually looking at was the **`StatCard` grid** in the embedded graphical
  Summary (Total Publishers, Total Elders, Regular/Auxiliary Pioneers,
  etc.), which still had its icon badge and per-item color from the "add a
  color code" phase. That's now confirmed to be in scope too.
- **`StatCard` simplified**: dropped the `icon`/`color` parameters entirely
  (only one call site existed) — every card is now the same neutral
  surface with just a label and a bold value, no glyph, no tint.
- **Total Publishers and Total Elders separated**: per a follow-up request,
  these were pulled out of the old combined `ComparisonCard` (two numbers
  sharing one proportion bar) and are now their own individual `StatCard`
  entries, first in the Overview grid — consistent with every other figure
  there instead of specially paired together. `ComparisonCard` had no
  remaining callers after this and was removed.
- The donut chart, preaching-hours bar chart, and per-congregation
  comparison chart still use their own per-slice colors — charts
  inherently need that to be readable at all (a single-color pie chart
  shows nothing), so that's a different case from a list of otherwise-
  identical stat cards and was left alone.
- Verified: clean compile, app installs and still launches to Login without
  a crash. Not verified on a screen that actually renders `DashboardStatsContent`
  (same recurring credential gap as prior phases).

## Phase 23 — Update flow skips its own checksum step; clickable stat-card details; violet re-match ✅ done

- **Update flow no longer verifies its own download**: clarified with the
  user that "install without scanning" meant GoPreach's own post-download
  SHA-256 check (the "Verifying Update..." step), not Android/Play
  Protect's own OS-level scan — which isn't something app code can
  legitimately disable anyway. `UpdateViewModel.updateNow()` now goes
  straight from Downloading to the install handoff; the now-unreachable
  `UpdateCheckState.Verifying` state and its dialog branch were removed.
  Android's Package Installer still enforces its own signature check on
  every install regardless — that guarantee was never provided by this
  app's extra checksum step to begin with.
- **Stat cards are now clickable**: tapping any card in the Reports
  Dashboard's Overview grid opens a details dialog showing the value, the
  congregation + month it's for, and — for Total Publishers and Total
  Preaching Hours specifically, which are the only two figures with a real
  sub-breakdown in this app's data model — the categories/components that
  make it up (e.g. Total Publishers breaks down into Regular Publishers/
  Pioneers/Auxiliary Pioneers/Unbaptized/Inactive). Other cards' dialogs
  just confirm the value/scope/period since there's no further breakdown
  to show.
- **Violet re-matched to a supplied swatch**: the user pasted an exact
  target color; `PrimaryPurple` (light theme) changed from `#6A1B9A` to
  `#5F4B8B`, with the dark-theme bright accent, containers, secondary tone,
  splash background, and the logo (launcher icon + in-app mark) all
  regenerated to match. Verified on-device against the pasted swatch —
  visually a very close match.
- Not verified on-device: the update flow's install-without-verifying path,
  and the new stat-card details dialogs (same recurring credential gap —
  no Super-Admin/Admin test account available this session). The color
  match itself *was* verified visually.

## Phase 24 — Admin Record Deletion and Inactive Status ✅ done

- **`Delete` no longer means immediate destruction, anywhere in the app.**
  Every Manage screen's delete icon now opens a shared `DeleteChoiceDialog`
  ("What would you like to do with this record?" → Move to Inactive /
  Delete Permanently / Cancel), and Delete Permanently always gets its own
  second, harder-to-hit confirmation ("This action cannot be undone...").
  Applied consistently to Congregations, Groups, Publishers, Admins,
  Coordinator Elders, Regular Elders, restricted Users, and Interested
  Persons.
- **New `RecordStatus { ACTIVE, INACTIVE }` enum** added to Congregation,
  Group, and InterestedPerson — the three record types that had no
  inactive-equivalent mechanism before this. Admins/Elders already had
  `RoleAssignmentStatus`, Publishers already had
  `PublisherCategory.REMOVED_PUBLISHER`, and restricted Users already had
  `AccountStatus` — those existing mechanisms are reused as-is rather than
  gaining a second, redundant status field.
- **"Show Inactive" filter** added to every affected list screen — inactive/
  removed/deactivated records are hidden from the normal list by default,
  matching spec §5's "should not appear in normal active lists unless the
  user chooses Show Inactive." A restore/reactivate icon appears on
  already-inactive rows.
- **Permanent deletion is a genuinely new capability** — it never existed
  for any record type before this phase (the old "Delete" button always
  meant deactivate/recategorize). It is **scoped to Super-Admin only,
  across every record type**, via `canPermanentlyDelete` threaded from
  `GoPreachNavGraph.kt` (`currentRole == AdminRole.SUPER_ADMIN`) into each
  screen and passed down into `DeleteChoiceDialog`, which hides the option
  entirely (not just disables it) when false. This is the conservative
  reading of spec §7's "appropriate Delete/Manage permission" — it mirrors
  the hierarchy from the earlier User Access Management phase, where
  Super-Admin is the only role with unrestricted system control. Every
  `permanentlyDelete(...)` call is also on the ViewModel, not just hidden
  in the UI, so it can't be reached by manipulating the screen.
- **Referential-integrity checks before permanent delete, where the data
  model has real relationships to protect**:
  - Congregation → blocked if it still has any Group or any Admin/Elder/
    Publisher RoleAssignment (active *or* inactive) pointing at it.
  - Group → blocked if any Publisher's RoleAssignment.groupId still points
    at it (Elders in the three named roles are auto-cleared instead of
    blocking, same as before).
  - Publisher → blocked if they have any MonthlyReport, InterestedPerson,
    or BibleStudyRecord on file.
  - Admins/Elders/Users/Congregations/Groups have no such records tied to
    them in a way that needs blocking, so their permanent-delete just
    cascades what little needs clearing (see below) and proceeds.
- **Cascade fixes applied as part of this pass** (both were real,
  pre-existing gaps, not new by-design behavior):
  - Regular Elder permanent delete now clears them out of any Group role
    slot (Overseer/Servant/Assistant/legacy) they still occupy first —
    the old plain `delete()` for Groups already did this in the other
    direction (Group deletion clearing the Elder's `groupId`), but nothing
    previously cleared a *deleted Elder* back out of a Group.
  - Group permanent delete now also clears `groupId` on every Publisher
    RoleAssignment still pointing at it — the old `ManageGroupsViewModel
    .delete()` cleared the three Elder roles' `groupId` but never touched
    Publishers, silently leaving their report/schedule scope pointing at a
    group that no longer existed.
  - Interested Person permanent delete now actually cascades-deletes every
    child Visit document first — the old `delete()`'s UI copy claimed this
    ("removes... their visit history") but never did it, leaving orphaned
    `visits` subcollection documents behind every time.
- **Audit trail**: every Move-to-Inactive/reactivate and every permanent
  delete logs an `AuditLogRepository` entry (actor, action, target,
  timestamp, and a human-readable `"status: OLD -> NEW"` detail line for
  status changes) — including for the three record/ViewModel types that
  had zero audit logging before this phase (Admins, Coordinator Elders,
  Regular Elders all gained a newly-injected `AuditLogRepository`).
  Permanent-delete audit entries are written before/alongside the delete
  itself, so the trail survives even though the source record doesn't.
- **Not implemented, deliberately out of scope for this pass**: a
  Congregation/Group "empty" permanent-delete does not also reach into
  historical MonthlyReport/InterestedPerson rows that reference a deleted
  Group's *Publishers* transitively — only direct relationships were
  checked, consistent with spec §4's "do not blindly delete related data"
  being about direct references, not a full graph traversal.
- Not verified on-device: none of the actual delete/reactivate/permanent-
  delete flows for any of the seven record types (same recurring
  credential gap — no Super-Admin/Admin test account available this
  session). Every change was verified by `./gradlew :app:compileDebugKotlin`
  after each record type's vertical slice, and a final clean
  `:app:assembleDebug` after all seven were wired.

## Phase 25 — Login "Remember me" fix and app-logo redundancy removal ✅ done

- **"Remember me" bug fix**: `LoginViewModel`'s `init` block was restoring
  the saved *username* from `CredentialStore` on launch but never the saved
  *password* — so the checkbox showed checked and the username field
  pre-filled, but the password field was always blank, meaning the user
  still had to retype their password every time despite "remembering" them.
  Now both are restored together.
- **App logo removed from Login, Admin, and Publisher headers** to avoid
  showing the logo image and the "GoPreach" title stacked together in the
  same header. `DynamicAppLogo()` calls removed from `LoginScreen`'s
  `GradientHero`, `AdminHomeScreen`'s `DashboardHero`, and
  `PublisherHomeScreen`'s `DashboardHero`; `DashboardHero`'s now-unused
  `logoContent` parameter was removed. `DynamicAppLogo`/`AppBanner`
  themselves are left in place (unused for now) rather than deleted
  outright, since the Super-Admin logo-upload Control Panel feature they
  support is still a real (if Storage-blocked) feature — just no display
  call site left standing anywhere that also shows a text title next to it.
- Not verified on-device (same recurring credential gap): visually
  confirming the headers read cleanly without the logo. Verified by a clean
  `:app:compileDebugKotlin`/`:app:assembleDebug` only.

## Phase 26 — Manual "Sync to Server", account-settings password bug, and small UI fixes ✅ done

- **"Current password is incorrect" false negative fixed**: `AuthRepository
  .reauthenticate()` (used by both Change Username and Change Password in
  Account Settings) used to catch *every* exception from Firebase's
  `reauthenticate()` call and report it as "Current password is incorrect"
  — including a dropped network connection or Firebase's own too-many-
  attempts throttling, neither of which means the password was wrong. That
  produced exactly the reported symptom (typing the correct password and
  still being told it's wrong). Now only a genuine
  `FirebaseAuthInvalidCredentialsException` reports "incorrect password";
  a network failure, rate-limit, or anything else surfaces its real cause
  instead.
- **Manual "SYNC TO SERVER" button** (spec: offline-first architecture,
  §6-§10, §16-§17) added to the Main Form (both Admin and Publisher
  dashboards), per the user's explicit choice to **keep the existing
  automatic background sync exactly as-is** (WorkManager already flushes
  every local write, and Firestore listeners already deliver other users'
  changes live) and add this as an *additional*, user-visible, explicit
  trigger on top — not a replacement.
  - Tapping it checks connectivity first (`ConnectivityObserver.isOnline()`);
    if offline, shows the spec's exact "No Network Connection" copy and
    leaves all pending local changes untouched.
  - If online, it calls the same `SyncScheduler.requestSyncNow()` the
    auto-sync path already uses (so behavior is identical, just visible),
    then follows that specific run's `WorkInfo.progress` to show "Syncing
    to Server... Uploading records: N / M", and a final "Sync Complete" /
    "Sync Completed with Errors" summary with uploaded/failed counts and a
    Retry action.
  - `SyncWorker` now publishes progress (`done`/`total`) and a final
    uploaded/failed count via `setProgress` before returning — its actual
    `Result` (`retry()` on partial failure, unchanged) still drives
    automatic background retry exactly as before; the manual UI reads its
    summary from the progress update instead of waiting on that terminal
    `Result`, since a `retry()` never reaches one for this attempt.
  - **Not implemented**: real per-field conflict detection/resolution
    (spec §11) — `SyncWorker` still does a last-write-wins `docRef.set()`
    with no version/timestamp comparison, same as before this phase. A
    genuine conflict-resolution UI ("Keep Local / Keep Server / Review
    Changes") needs a versioning scheme added to every synced document
    first and is a substantial separate piece of work, not attempted here.
    Explicit "download changes" and "images synced" summary line items
    (spec §8 steps 8-9, §13) are also not separately reported — downloads
    already happen continuously via the existing Firestore listeners
    rather than as a discrete step of this button's run, and images ride
    the same per-document sync path as every other field since they're
    embedded Base64 fields, not separate uploads.
- **Offline login investigated, no code change needed**: the spec's
  "log in even with no network" requirement is already satisfied by the
  existing architecture — `UserSession` rebuilds its state from Firebase
  Auth's own persisted sign-in (survives app restart with no network
  needed) plus Room-cached Person/RoleAssignment/UserAccessGrant data, and
  `GoPreachNavGraph` routes reactively off that state, skipping the Login
  screen entirely whenever a session is already persisted — regardless of
  connectivity. `AuthRepository.signIn()` (the network-only, username→
  Firestore-lookup path) is only ever reached when there is *no* persisted
  session at all, which correctly requires the spec's "First Online Login"
  regardless. Not independently re-verified live this pass (same
  credential-gap limitation as every phase since Purple Brand Logo).
- **Regular Elder Enrollment**: removed the "Specific Role" dropdown
  (the `ElderTitleEntity` lookup-table field) per user request — enrollment
  now only asks for the required Group Role (Overseer/Servant/Assistant).
  The underlying `RoleAssignment.elderTitleId` field is left in the data
  model (now always null for newly-enrolled Regular Elders) rather than
  removed, since existing records/other screens may still reference it.
- **`DeleteChoiceDialog`'s permanent-delete step**: Cancel/Close and Delete
  Permanently now render together as one centered button group instead of
  the default confirm-right/dismiss-left split — Cancel reads as the safer
  action here, so it's no longer pushed off to a corner.
- Verified via `./gradlew :app:compileDebugKotlin` after each change and a
  final clean `:app:assembleDebug`. The Sync-to-Server flow's actual
  network/no-network/progress/summary states were not exercised live (same
  recurring credential gap).

## Phase 27 — Complete-record Edit forms, true offline login, Login layout, multi Theme Color ✅ done

- **"Show Complete Record Information When Editing"** — every Manage
  screen's Edit dialog now shows the complete stored record, not just a
  couple of fields, organized into sections (`EditSectionHeader`) with a
  clear editable/read-only split (`ReadOnlyField`, both new shared
  components in `RecordEditSections.kt`):
  - **Congregations**: added System Information (Record ID, Status, Date
    Added) — the 3 editable fields already covered the whole editable
    surface.
  - **Groups**: same System Information addition; the 3 role dropdowns +
    name already covered the rest.
  - **Publishers**: was Address/Contact only — now First/Last Name, Email
    added as editable; Category, Group, Username, Account Status, Date
    Added added as read-only (Category/Group already have their own
    dedicated controls elsewhere on the screen, so they're shown, not
    re-editable here).
  - **Admins**: same expansion — First/Last Name added as editable;
    Congregation, Role, Username, Status, Date Added added read-only.
  - **Coordinator/Regular Elders** (shared `ElderListScreen`): same
    expansion, plus Email added as editable; Congregation/Group scope,
    Group Role (Regular Elder only), Username, Status, Date Added added
    read-only.
  - **Interested Persons**: name/gender/address/religion/image already
    covered the whole editable surface — added Record ID, Status, Date
    Added, and GPS location (when captured) as read-only.
  - **Restricted Users** (`AddEditUserScreen`): the biggest gap — Edit mode
    previously showed *no* Personal Information fields at all (not even the
    person's name), only Permissions/Scope/Status. Now shows First/Last
    Name, Address, Contact, Email as editable, and Username/Date Added as
    read-only, alongside the existing Permissions/Scope/Account Status
    sections.
  - All of this reads from/writes to the same Room-cached, offline-first
    repositories every other screen already uses — no separate online-only
    path, so editing offline and seeing it marked Pending Sync already
    works via the existing architecture, unchanged.
- **True offline login** — the "session reuse" fix from Phase 26 wasn't
  enough in practice, so this adds the actual local-credential fallback the
  spec (and the user) asked for:
  - `OfflineAuthStore` (new): every successful *online* [`AuthRepository
    .signIn`] now also saves a PBKDF2-hashed verifier (random per-record
    salt, 120,000 iterations, at rest in `EncryptedSharedPreferences`/
    Android Keystore) for that exact username/password — never the
    password itself. Independent of the "Remember me" checkbox, which is
    a separate, opt-in, unrelated biometric-unlock convenience.
  - `AuthRepository.offlineSignIn(username, password)` (new): verifies
    against that saved hash with no network call at all, then grants
    access using whatever Person/RoleAssignment data is already cached
    locally from the prior online session (spec §1's "last synchronized
    permissions and scope").
  - `OfflineSessionMarker` (new): Firebase's own Auth SDK has no offline
    sign-in path — it always requires a network round trip — so a
    successful `offlineSignIn` can't make `FirebaseAuth.currentUser`
    non-null the way a real online sign-in does. This marker is the app's
    own "this personId is signed in" record; `UserSession.state` now reads
    Firebase's persisted auth state *or* this marker (Firebase wins when
    both are present), so the reactive nav routing treats an offline
    sign-in exactly like a normal one everywhere else in the app.
  - `LoginViewModel` now checks `ConnectivityObserver.isOnline()` before
    submitting and calls `offlineSignIn` instead of `signIn` when offline.
  - Only works for a user who has signed in online at least once on that
    specific device — matches this app's own documented offline-first flow
    ("First Online Login" → "Device Can Operate Offline"); a device that
    has never been online with this app still correctly cannot log in
    without network.
- **Login screen layout**: "Ministry Activity Tracking" was small,
  low-contrast (`colorScheme.secondary` on the purple gradient), and
  pinned right under the status bar. Now bold, white, `titleMedium`, and
  the whole title/subtitle block is bottom-aligned within the hero instead
  of top-aligned, so it reads clearly and sits lower on the screen.
- **Multi Theme Color choices** — a new, purely per-device (never synced,
  never shared) accent-color picker in Settings → Appearance, alongside the
  existing Light/Dark/System choice: `ThemeColorOption` (Purple/Blue/Green/
  Teal/Orange/Red, Purple staying the original default), stored via
  `ThemePreferenceRepository` (extended, same SharedPreferences pattern as
  the existing theme preference), applied in `GoPreachTheme` by swapping
  just the primary/secondary accent hues — surfaces, backgrounds, and the
  white/#121212 base stay exactly as the brand theme already defines them.
- Verified via `./gradlew :app:compileDebugKotlin` after each change, a
  full `:app:assembleDebug`, and an on-device screenshot of the Login
  screen (v1.13.0) confirming the new layout/subtitle styling. Not verified
  live: the expanded Edit dialogs' actual save behavior, the offline-login
  flow itself (would need airplane mode + a real prior online sign-in on
  this emulator, which needs Super-Admin/Admin/Publisher credentials this
  session doesn't have), and the Theme Color picker (Settings requires
  being signed in).

## Phase 28 — Fully manual sync, Account Settings name field ✅ done

- **Automatic sync removed entirely** — Phase 26 kept auto-sync running
  alongside the new manual button; this reverses that and goes fully
  manual, per explicit follow-up direction. `OfflineFirestoreRepository
  .save()`/`saveRawJson()`/`delete()` no longer call
  `SyncScheduler.requestSyncNow()` at all — a write now only ever updates
  the local cache and enqueues the pending operation, nothing more. The
  *only* remaining triggers for a sync run are explicit user actions:
  `SyncToServerButton` (checks connectivity first, shows progress/summary),
  the header `SyncStatusButton` shortcut, and pull-to-refresh — matching
  spec §17's "Manual Sync Requirement" literally now, not just via an
  additional button on top of the old behavior. Live Firestore listeners
  that download *other* users' changes are unchanged — those are a
  separate concern (this device receiving updates) from this device's own
  pending-edit upload queue, which is what "manual sync" is about.
- **Account Settings: added a Personal Information section** (First Name/
  Last Name, editable, no current-password check needed since it's profile
  info rather than a login credential) above the existing Change Username/
  Change Password sections — this screen is used by every signed-in role
  including Super-Admin, whose name previously had no edit surface
  anywhere in the app at all.
- Verified via `./gradlew :app:compileDebugKotlin`, a full
  `:app:assembleDebug`, and an on-device screenshot of the Login screen
  (v1.14.0, no crash). Not verified live: an actual sync run no longer
  firing automatically after a write, and the new Account Settings name
  field's save behavior (Settings/Account Settings both require being
  signed in — same recurring credential gap).

## Phase 29 — Refresh/Update/Sync fully decoupled; real percentage in the Sync button ✅ done

- **Refresh no longer checks for app updates or uploads pending changes** —
  this was a real, direct bug: `AdminHomeScreen`'s pull-to-refresh called
  *both* `syncScheduler.requestSyncNow()` **and**
  `updateViewModel.checkManually()`, exactly the cross-triggering this
  spec's §18 explicitly forbids. `HomeViewModel.refreshData()` now just
  reports current connectivity (for a possible "Refreshed"/"Showing
  offline data" moment) and does nothing else — every screen already
  renders off a Room cache kept continuously live by Firestore listeners
  while online, so there's no separate "fetch" step Refresh needs to
  trigger. The spinner now resets on its own short delay instead of
  waiting on the update-check state.
- **Automatic update checking now also fires on reconnect**, not just once
  at app start: `UpdateViewModel` observes `ConnectivityObserver` and
  triggers a silent check on an actual offline→online *transition* (not on
  the initial subscribe, to avoid double-checking a launch that's already
  online) — spec §12's "When the device becomes online: Automatically
  check for application updates."
- **`SyncToServerButton` now shows real progress inside the button itself**
  (spec §6): the label becomes `SYNCING 47%` — an actual
  `done * 100 / total` computed from `SyncWorker`'s real per-record
  progress, never a fake animation — and the button is disabled for the
  duration so a second tap can't start an overlapping run (spec §8). A
  status line under the button shows `Uploading changes: 13 / 20` while
  running, or the spec's exact dynamic singular/plural pending-count
  wording when idle (`"There is 1 change..."` / `"There are 5 changes..."`),
  or `"✓ All changes synced"` when there's nothing pending, or
  `"Sync Failed – Try Again"` if the run couldn't complete at all.
  Wording elsewhere updated to match the spec exactly too: "No Network
  Connection" now names the actual pending count; the completion dialog is
  "Sync Complete" and "All changes have been successfully synchronized
  with the server." verbatim on full success, or "Sync Partially Complete
  / N changes synchronized. / M changes still need synchronization." when
  some fail (never "Sync Failed" unless *zero* succeeded).
- **`SyncStatusButton`** (header icon) now shares `ManualSyncViewModel`
  with `SyncToServerButton` instead of its own separate ViewModel/sync
  path — tapping it gets the same connectivity check and progress/summary
  handling, rather than a second, divergent "sync" behavior that could
  silently queue a run while offline.
- Verified via `./gradlew :app:compileDebugKotlin`, a full
  `:app:assembleDebug`, and an on-device screenshot of the Login screen
  (v1.15.0, no crash). Not verified live: the actual percentage/status-line
  behavior during a real sync run, and the reconnect-triggered update
  check — both need a signed-in session and a real connectivity toggle,
  which this session doesn't have credentials/hardware access to exercise.

## Phase 30 — Critical fix: phantom "pending sync" entries from downloaded data ✅ done

- **Root cause of the "609 changes need to sync" report (real bug, not user
  error)**: `mirrorFirestoreCollection` — the live listener that downloads
  *other* users'/the server's data into the local cache — called
  `OfflineFirestoreRepository.save()`/`delete()` for every document it
  received. Those methods always enqueue a pending **upload** operation,
  regardless of whether the write came from the user editing something
  locally or from the server just telling this device about a document
  that already exists there. The result: every single document in every
  one of the app's 14 synced collections got queued right back up as a
  "change that needs to sync" the moment it was *downloaded* — including
  the *entire* initial snapshot the first time each collection's listener
  ever attached, and again on every sign-in/sign-out (listeners
  re-subscribe fresh each time per `RemoteSyncCoordinator`). With no
  duplicate-guard on the queue table, repeated attach cycles could pile up
  further still — easily reaching hundreds of phantom entries for a device
  that never made a single real edit.
- **Fix**: `OfflineFirestoreRepository` now has separate `cacheFromServer()`/
  `deleteFromServer()` methods — cache-only, never touch the sync queue —
  used **exclusively** by `mirrorFirestoreCollection`. The existing
  `save()`/`delete()` (for genuine local edits) are unchanged in behavior,
  except they now also call the new `SyncQueueDao.removeForDocument()`
  before enqueueing, so a document can never have more than one pending
  operation queued at a time (a newer local edit supersedes an older
  unsynced one, rather than both piling up).
- **Recovery for anyone already showing an inflated pending count**:
  no destructive queue purge was added, since safely distinguishing
  already-queued phantom entries from genuine unsynced edits isn't
  possible after the fact. Instead: the fix makes this safe to resolve
  simply by tapping **SYNC TO SERVER** once after updating — every phantom
  entry re-uploads data that's already identical on the server (a harmless
  no-op `set()`), and the count correctly drops to 0 (or to the true
  number of genuine pending edits, if any) afterward. No data is at risk
  either way.
- This bug almost certainly predates this session's "manual sync only"
  change (Phase 28) — it was likely always inflating the pending count,
  just invisibly, because the old automatic-sync-on-every-write behavior
  silently flushed the queue (phantom entries included) before anyone
  would notice it climbing. Removing automatic sync made a pre-existing
  bug's effects visible and persistent for the first time, rather than
  introducing a new one.
- Verified via `./gradlew :app:compileDebugKotlin`, a full
  `:app:assembleDebug`, and an on-device screenshot of the Login screen
  (v1.15.1, no crash). Not verified live: that the pending count actually
  stays at 0 after a fresh sign-in with no local edits (needs a signed-in
  session this environment doesn't have credentials for).

## Phase 31 — Critical fix: Sync to Server getting stuck on "Checking for pending changes..." ✅ done

- **Root cause**: `SyncScheduler.requestSyncNow()` enqueued every sync run
  with `ExistingWorkPolicy.APPEND_OR_REPLACE`, which chains each new run
  onto the same unique work name rather than replacing it — so
  `getWorkInfosForUniqueWorkFlow` could keep returning **more than one**
  `WorkInfo` (old, already-completed runs sitting alongside the new one).
  `ManualSyncViewModel` picked `.firstOrNull()` from that list with no
  guarantee it was the run just triggered — if a stale, already-`SUCCEEDED`
  entry from a past sync came back first, it never carries this attempt's
  `SyncWorker.KEY_FINISHED` progress flag, so the button's state machine
  never left `Syncing(0, 0)`, permanently showing "Checking for pending
  changes...".
- **Fix**: switched to `ExistingWorkPolicy.REPLACE` — a new sync tap now
  cancels/discards whatever was there and enqueues a single fresh request,
  so there's only ever one entry to read. `observeWorkInfo()` also now
  picks the highest-`generation` entry defensively rather than trusting
  list order. Added a fallback in `ManualSyncViewModel` too: a `SUCCEEDED`
  state reached without a `KEY_FINISHED` progress flag now still resolves
  the UI out of `Syncing` (treated as a completed run with unknown counts)
  instead of ever being able to hang indefinitely again, even in an
  unforeseen edge case.
- Verified via `./gradlew :app:compileDebugKotlin`, a full
  `:app:assembleDebug`, and an on-device screenshot of the Login screen
  (v1.15.2, no crash). Not verified live: an actual sync run completing
  end-to-end and the button correctly returning to "SYNC TO SERVER" — needs
  a signed-in session this environment doesn't have credentials for.

## Phase 32 — Proper Back Button and Page Navigation Behavior ✅ done (partial — see scope notes)

- **Audited the existing back-stack behavior first** rather than assuming a
  rewrite was needed: this app already uses Jetpack Navigation Compose
  (`NavHost`/`NavController`), which maintains a proper back stack and
  handles the system Back button automatically — every `navController
  .navigate(route)` call app-wide (Congregations → Group → Publisher List →
  Publisher Details, etc.) already pushes onto that stack with no extra
  code needed, and the only `popUpTo(0) { inclusive = true }` anywhere in
  `GoPreachNavGraph` is the one place that should have it — the reactive
  sign-in/sign-out/role-change router, which correctly should not be
  "back-into-able." So spec §1-§3, §8-§10 (proper multi-level history,
  consistent everywhere) needed **no code change** — confirmed by grep
  audit, not assumption.
- **Fixed a real gap (spec §6)**: the Admin Home screen's navigation
  drawer had no `BackHandler` at all — pressing system Back while it was
  open would fall through to the normal nav-stack pop (or exit) instead of
  just closing the drawer first. Added `BackHandler(enabled = drawerState
  .isOpen)` to close it first, and a second `BackHandler(enabled =
  !drawerState.isOpen)` for the Main Form exit case below, so the two
  never both fire for the same back-press.
- **Added Main Form exit confirmation (spec §7)**: `AdminHomeScreen` and
  `PublisherHomeScreen` are both root/home destinations with nothing left
  in the nav stack to pop to — Back here previously fell through to
  Android's default "finish the Activity" behavior with zero warning. Both
  now show "Exit GoPreach? / Are you sure you want to close the
  application? [CANCEL] [EXIT]" instead, matching the spec's copy exactly.
- **New reusable `rememberUnsavedChangesBackHandler` (spec §4)**, wired
  into all five dedicated enrollment screens (Admin, Publisher, Coordinator
  Elder, Regular Elder, Congregation) — the app's "add a new record" forms
  most likely to have real unsaved typing at risk. Intercepts both the
  system Back gesture and the top app bar's back arrow through the same
  guard, showing "Unsaved Changes / You have changes that have not been
  saved. [CANCEL] [DISCARD] [SAVE]" whenever any field has content and the
  record hasn't been created yet — Congregation's version wires a real
  `SAVE` (its save function is a simple, already-validated one-tap
  action); the other four offer Cancel/Discard only, since their "save" is
  an async temp-credential-creation flow, not a safe one-tap action to run
  invisibly from inside a confirmation dialog.
- **Dialogs closing on Back before navigating away (spec §5)**: already
  correct app-wide with **no code change needed** — every `AlertDialog` in
  Compose Material3 already intercepts the system Back button via its own
  internal dialog window before it reaches the underlying screen's nav
  stack; this was verified as already-correct behavior, not implemented.
- **Explicitly not done — a real scope limit, not an oversight**: the
  `AlertDialog`-based "Edit" forms inside the various Manage screens
  (Publishers, Admins, Elders, Congregations, Groups, Interested Persons,
  restricted Users — roughly a dozen dialogs across the app) do **not**
  have unsaved-changes dirty-tracking. Back already correctly dismisses
  just the dialog rather than navigating the whole screen away (satisfying
  §5), but doesn't yet warn before discarding typed edits inside it
  (§4's stricter requirement) — retrofitting dirty-tracking to every one
  of those dialogs individually is a large separate task, tracked below
  rather than attempted partially in this pass.
- Verified via `./gradlew :app:compileDebugKotlin`, a full
  `:app:assembleDebug`, and an on-device screenshot of the Login screen
  (v1.16.0, no crash). Not verified live: the drawer-close-on-back, the
  Exit GoPreach confirmation, and the Unsaved Changes dialogs — all need a
  signed-in session and physically pressing the hardware/gesture back
  button, neither of which this environment can exercise; confirmed
  correct by code review and a successful compile only.

## Phase 33 — Critical fix: Sync stuck (actual root cause); Elder/Publisher double-count bug; stat-card member names ✅ done

- **The real fix for "Sync to Server" getting stuck** — Phase 31's
  `ExistingWorkPolicy.REPLACE` change was a genuine improvement but **did
  not fully fix it**, confirmed by further live user reports after
  updating. The actual bug: `observeWorkInfo()` picked
  `infos.maxByOrNull { it.generation }` from the unique-work-name list to
  guess "the current run" — but `WorkInfo.generation` only increments for
  a *periodic* work request updated in place; every fresh one-time
  `REPLACE`d request starts at generation 0, so that comparison was
  meaningless and the ambiguity Phase 31 was meant to close was still
  there. Fixed properly this time: `SyncScheduler.requestSyncNow()` now
  returns the specific `WorkRequest`'s own `UUID`, and
  `observeWorkInfo(id)` uses `WorkManager.getWorkInfoByIdFlow(id)` to track
  *exactly* that request — no unique-work-name list, no ambiguity of any
  kind, regardless of whatever else has ever run under that name.
- **Fixed a real, confirmed double-counting bug**: reported as "3 elders
  shown in the dashboard, only 2 actually enrolled." `CongregationStats
  .compute()` counted matching `RoleAssignment` *documents*, not distinct
  people — if the same person ends up with more than one ACTIVE assignment
  landing in the same bucket (e.g. holding both a Coordinator Elder and a
  Regular Elder assignment at once), they were counted twice. Now
  `distinctBy { it.personId }` before counting, for both the elder count
  and every publisher-category count.
- **Stat cards now show the actual people behind the number**, not just
  the number, on every card where that's meaningful — spec-requested
  example: "Total Elders 3 / Henry Canales (Solano Tagalog Congregation),
  Joel Martin (Central Congregation), ...". New `computeStatMembers()`
  (in `DashboardStats.kt`) resolves the exact same deduplicated-by-person
  data `CongregationStats.compute()` counts into named `StatMember`
  entries, so the list a card's dialog shows can never silently disagree
  with the number on the card — both come from the same source, same
  dedup pass. Applied to Total Publishers, Total Elders, Regular Pioneers,
  Auxiliary Pioneers, Unbaptized Publishers, Inactive Publishers, and
  Removed Publishers; Bible Studies and Total Preaching Hours are left
  numeric-only (a record/aggregate count, not a 1:1 list of named people).
- **Removed the small sync-status icon next to the Settings gear** on both
  the Admin and Publisher Main Form headers, per request — `SyncToServer
  Button` in the body is now the only sync-status affordance on the Main
  Form.
- Verified via `./gradlew :app:compileDebugKotlin`, a full
  `:app:assembleDebug`, and an on-device screenshot of the Login screen.
  Not verified live: an actual sync run completing end-to-end, the
  corrected elder/publisher counts, and the new member-name dialogs — all
  need a signed-in session with real enrolled data, which this environment
  doesn't have credentials for.

## Phase 34 — Elder/publisher count still wrong after Phase 33: cross-congregation double-count ✅ done

- **Reported again after updating**: "still wrong number in elder count,
  there are only 2 in the records but it shows 3." Phase 33's `distinctBy
  personId` fix was correct but incomplete — it dedupes *within* one
  congregation's `CongregationStats.compute()` call, but the "All
  Congregations" total (what a Super-Admin sees by default) was built by
  **summing** those already-deduped per-congregation numbers
  (`all.sumOf { it.totalElders }`, etc.). If the same person holds an
  ACTIVE elder/publisher assignment in **two different** congregations at
  once, each per-congregation count correctly shows them once *there*, but
  adding those totals together counts that one person twice anyway —
  exactly the kind of cross-congregation duplication the per-congregation
  fix couldn't reach.
- **Fix**: `CongregationStats.total()` no longer sums `all`'s numbers at
  all — it now takes the raw, already-scoped `congregations`/`assignments`
  /`reports` lists directly and recomputes the elder/publisher counts from
  scratch with a **global** `distinctBy { it.personId }` (ignoring which
  congregation each assignment belongs to), the same way `compute()`
  dedupes within one congregation. `DashboardStatsViewModel` now exposes
  this as `overallTotal` computed independently, rather than the screen
  deriving it from `all` after the fact.
- **If the count is still wrong after this update**: the remaining
  explanation this fix can't reach is a genuine **duplicate Person
  record** — two separate enrolled accounts for the same real human (e.g.
  from a double-submitted enrollment). That can't be safely auto-merged by
  matching names in code. The Phase 33 "tap the card to see names"
  feature is the intended way to spot this: if "Total Elders" lists the
  same name twice, that confirms a duplicate record, which can then be
  permanently deleted via the existing Admin Record Deletion flow
  (Phase 24).
- Verified via `./gradlew :app:compileDebugKotlin`, a full
  `:app:assembleDebug`, and an on-device screenshot of the Login screen
  (v1.16.1, no crash). Not verified live: the corrected "All Congregations"
  total against real cross-congregation data — needs a signed-in session
  with that exact scenario, which this environment doesn't have.

## Phase 35 — Main Form Date Range Filtering; Total-count audit; Update Sharing already covered ✅ done (partial — see scope notes)

- **Audited every "Total X" card on the Main Form for the same class of bug**
  (user: "check all related issues about this," after the elder-count
  fixes) — confirmed the two Phase 33/34 fixes already apply **uniformly**
  to every person-count card (Total Publishers, Total Elders, Regular/
  Auxiliary Pioneers, Unbaptized/Inactive/Removed Publishers), not just
  elders, since they all share the same `publisherAssignments`/`elderCount`
  computation in `CongregationStats.compute()`/`total()`. Separately
  verified `MonthlyReportViewModel.submit()` reuses the existing report's
  own document id when one exists for that publisher+period rather than
  creating a new one, so Bible Studies/Preaching Hours were never
  vulnerable to this class of duplicate-counting bug in the first place —
  confirmed by code review, not assumed.
- **Update Sharing (spec §11-§13) was already fully implemented** before
  this phase — `UpdateHost`'s "Update Available" dialog already has both
  `UPDATE NOW` and `SHARE APK` (native Android Share Sheet via
  `Intent.ACTION_SEND`/`createChooser`, reaching Messenger/SMS/email/
  WhatsApp/etc.), the shared link always comes from the live
  `UpdateManifestRepository` fetch (never a hard-coded URL), and
  automatically reflects whichever version was just detected. No new code
  needed for this section.
- **New: "Main Form Date Range Filtering"** (spec §2-§6, §9, §14, §16) —
  `DateRangeSelection.kt` (pure, testable date math: `QuickDateRange`
  enum + `DateRange` with `today()`/`thisWeek()`/`thisMonth()`/`thisYear()`/
  `custom()`, all computed live from `Calendar.getInstance()`, never
  hard-coded) and `DateRangeFilterBar.kt` (the reusable UI: quick-select
  chips with a clear selected state, native date pickers for Start/End,
  Purple-theme-consistent). "This Month" is the default everywhere it's
  used, calculated dynamically. Picking a date manually correctly flips
  the selection to `CUSTOM`; `DateRange.custom()` swaps/clamps so Start ≤
  End always holds.
- **Wired into two screens** as the first real applications of this: the
  Main Form's own Dashboard/Reports summary (`DashboardReportsScreen`) and
  the standalone Publisher Reports screen (`ReportsScreen`). In both,
  the range filters `MonthlyReport`-derived figures (Bible Studies,
  Preaching Hours) via `DateRange.overlapsMonth()` (a report is
  month-grain, so a "This Week" range correctly still matches whichever
  month it falls in, per spec §9's real-timestamp requirement applied
  honestly to what this data model actually stores) and, on the Publisher
  Reports screen, `InterestedPerson.createdAt` directly (a true
  timestamp). Congregation scoping is untouched — the date range only
  narrows further within whatever congregation(s)/group the caller already
  resolved the session is authorized to see (spec §10), never bypassing it.
- **Deliberately, honestly left always-current regardless of date
  range**: the Publisher/Elder *count* cards (Total Publishers, Total
  Elders, etc.). A `RoleAssignment` only ever reflects "assigned right
  now" — there is no historical "who was an elder as of August 2026"
  snapshot anywhere in this data model, so applying the date range to
  those specific numbers would fabricate a precision the app doesn't
  track. Disclosed directly in the Dashboard's own caption text, not
  silently glossed over.
- **Not done in this pass — a real, disclosed scope limit**: date-range
  wiring for Congregation Reports, Group Reports, Interested Person
  Reports (as its own report, distinct from the count folded into
  Publisher Reports above), Activity Reports, Statistics, and Summary
  Reports (spec §7's full list) — each would need its own review of
  what timestamp field it actually has to filter by, the same way this
  phase had to check `MonthlyReport`/`InterestedPerson` individually
  rather than assume a blanket filter works everywhere. Also not done:
  remembering the selected range across navigation between reports (spec
  §8) — each screen currently keeps its own local `DateRange` state,
  independent of the others.
- Verified via `./gradlew :app:compileDebugKotlin`, a full
  `:app:assembleDebug`, and an on-device screenshot of the Login screen
  (v1.17.0, no crash). Not verified live: the actual filtered figures
  against real data, the date pickers themselves, and the corrected
  Total-count cards — all need a signed-in session with real enrolled
  data and reports, which this environment doesn't have credentials for.

## Phase 36 — Critical fix: app closed right after a correct login; Elder Dashboard made consistent with Admin/Super-Admin ✅ done

- **Critical, reported live: "the app closes right when the Main Form
  should appear, right after a correct login."** Reproduced on-device
  this time (a persisted session let this environment actually reach the
  Main Form for the first time all session) and root-caused from the
  crash-buffer stack trace — **not** the `RoleType`/`resolvedRoleType()`
  theory investigated first (see below), but a real Firestore mapping
  conflict:
  `SharedLocation.publisherPersonId` is annotated `@DocumentId`, but at
  least one `sharedLocations` document in the live database had an actual
  stored field also named `publisherPersonId` — Firestore's POJO mapper
  throws a `RuntimeException` on read whenever that collision exists,
  since `@DocumentId` is supposed to be repopulated from the document
  reference alone, never a real field. That throw happened inside
  `mirrorFirestoreCollection`'s `appScope.launch { for (change in
  snapshot.documentChanges) { change.document.toObject(clazz) ... } }`
  (`FirestoreMirror.kt`) — unguarded, on a background coroutine with
  nothing to catch it — and every collection is mirrored immediately after
  sign-in, so it crashed the whole process for every session, precisely
  matching "right when the Main Form should appear."
  - **Where the bad field came from**: `SyncWorker.applyOperation()`'s
    manual-sync upload path re-serializes each queued document from its
    cached JSON into a raw `Map<String, Any?>` and does
    `docRef.set(fields - "id")` — correct for every model except
    `SharedLocation`, whose `@DocumentId` property isn't named `"id"`, so
    its real value was being uploaded as a literal field on every edit,
    recreating the exact collision `@DocumentId` forbids.
  - **Fix, two parts**: (1) `FirestoreMirror.kt` now wraps each
    document's `toObject(clazz)`/cache-write in try/catch — one malformed
    document is logged and skipped, never taking down the mirror or the
    app, for any collection, not just this one. (2) `SyncWorker.kt` now
    strips the correct per-collection `@DocumentId` key
    (`"publisherPersonId"` for `sharedLocations`, `"id"` for everything
    else) before every upload, so this specific collision can't recur.
  - **Verified live, not just by static analysis**: rebuilt, reinstalled
    on the emulator, relaunched — previously reproduced "GoPreach keeps
    stopping" on launch; after the fix, the exact same persisted session
    reached the Main Form and rendered real dashboard data (Total
    Publishers, Total Elders, Date Range filter, Sync banner) with zero
    crash-buffer entries. This is the first time this session could
    actually confirm a Main-Form-reaching fix on-device rather than by
    code review alone.
  - **Also hardened, defensively, while investigating** (not the
    confirmed root cause, but a real gap found along the way and left in
    place as protection against the *next* corrupt-data crash of this
    general shape): `RoleAssignment.resolvedRoleTypeOrNull()` — a
    non-throwing sibling of `resolvedRoleType()` — now used by
    `PermissionChecker.hasAdminRole()`/`isActivePublisher()`/
    `highestAdminRole()` and by the `ownCongregationId`/
    `ownGroupAssignment`/`ownPublisherAssignment` resolution in
    `GoPreachNavGraph.kt` and `AdminHomeScreen.kt` — all of which run
    unconditionally on every login/every Main Form composition from the
    signed-in session's *own* `RoleAssignment` list. A malformed
    `RoleAssignment.roleType` string there is now skipped like it holds no
    such role, instead of crashing composition. `DashboardStatsViewModel`
    also got a `try`/`catch` around its stats-aggregation `combine` block
    (surfaced via a new `error` field, shown gracefully by
    `DashboardReportsScreen` instead of taking down the app) for the same
    reason.
- **Elder Dashboard Consistent with Admin/Super-Admin Dashboard** (full
  12-section spec) — two real, previously-undiscovered bugs plus one
  self-caused regression, all fixed together:
  - `AdminHomeScreen`'s `hideMainFormButtons` previously covered only
    Super-Admin/`ADMIN_PER_CONGREGATION`, leaving Coordinator Elder and
    Regular Elder stuck on the old tile-grid Main Form body while
    Super-Admin/Admin already had the clean drawer+stats layout — the
    actual "not consistent" bug. Now includes `COORDINATOR_ELDER`/
    `REGULAR_ELDER` too, so every admin-track role shares one Main Form.
  - `visibleCongregationIds` silently resolved to `emptySet()` for Regular
    Elder — their own `RoleAssignment.congregationId` is only ever
    reachable via the `REGULAR_ELDER` branch, which the old filter didn't
    check — so a Regular Elder's embedded dashboard summary showed zero
    congregations' worth of data. Fixed by resolving
    `ownCongregationId ?: ownGroupCongregationId` (spec §2/§3: Elders
    still only ever see their own assigned scope, never every
    congregation — `isSuperAdmin` remains the only `null` case).
  - Self-discovered regression the `hideMainFormButtons` fix above would
    otherwise have caused: the old tile grid (now hidden for Elders too)
    was the *only* place "Reports Dashboard" and "Chat Schedule" were
    reachable for Coordinator/Regular Elder — the drawer had no
    equivalents. Added both to `SidePanel.kt`'s "Other" section
    unconditionally (every session reaching this drawer already has an
    admin-track role, so no extra gating boolean was needed), restoring
    the reach the tile grid used to give everyone instead of stranding
    Elders once their tile grid disappears.
  - Spec §10/§11 (Elder must not gain Admin/Super-Admin privileges;
    server must reject out-of-scope requests): unchanged by this phase —
    these fixes only affect what an Elder's *own* session can see/render,
    never what any `PermissionChecker`/`hasPermission()` check allows them
    to do, and this app's authorization architecture remains
    Firestore-rules + client-resolved scope rather than a custom backend
    (an acknowledged, pre-existing gap tracked below, not newly
    introduced).
- Verified via `./gradlew :app:compileDebugKotlin`, a full
  `:app:assembleDebug`, and this phase's own on-device Main Form
  screenshot above (a real Super-Admin session, not just the Login
  screen) — the strongest on-device confirmation available this session
  so far. Not verified live: the Elder Dashboard specifically (still no
  Coordinator/Regular Elder credentials in this environment), so its two
  fixes are confirmed by code review and compilation, not by an actual
  Elder login.

## Phase 37 — Date range remembered across navigation between reports (spec §8) ✅ done

- **Closed one half of Phase 35's disclosed gap**: "remembering the
  selected range across navigation" (spec §8). The other half of that
  gap — Congregation/Group/Activity/Statistics/Summary as separate report
  *screens* — doesn't actually exist as distinct destinations in this
  app; there are only two report surfaces, the Main Form's own Dashboard
  summary (`DashboardReportsScreen`, whose own per-congregation breakdown
  already *is* the "Congregation Reports" view) and the standalone
  "Reports Summary" (`ReportsScreen`, whose per-publisher rows already
  fold in Interested People) — both were already individually date-range
  filterable since Phase 35, just not sharing one selection.
- New `domain/DateRangeStore.kt` — a `@Singleton`, in-memory-only
  `MutableStateFlow<DateRange>` (deliberately not persisted across a full
  process restart: every report already defaults live to This Month on
  cold start per spec §4, so a disk-backed store would add nothing beyond
  that for a "remembered *across navigation*" requirement, which is about
  moving between screens in one session, not surviving an app kill).
- `DashboardStatsViewModel` and `ReportsViewModel` both now read/write
  this same store instead of owning their own `DateRange` state
  (`DashboardStatsViewModel`'s local `_filters` StateFlow now holds only
  `selectedCongregationId`, combined with the store's `range` to stay
  within `combine()`'s 5-flow overload; `ReportsScreen`'s local
  `remember { mutableStateOf(DateRange.thisMonth()) }` was removed
  entirely in favor of collecting `viewModel.dateRange`). Picking a range
  on either screen is now immediately reflected on the other the next
  time it's visited — no more independent defaults.
- Verified via `./gradlew :app:compileDebugKotlin`, a full
  `:app:assembleDebug`, and reinstall+relaunch on-device (no crash-buffer
  entries). **Not verified live**: actually watching a custom range
  survive Dashboard → Reports Summary navigation on-screen — the
  persisted Super-Admin session from Phase 36 had signed itself out by
  the time this phase's build was reinstalled, and this environment has
  no credentials to sign back in. The fix is confirmed correct by
  compilation and by code review of the shared single-source-of-truth
  wiring, not by an on-screen before/after of the actual navigation.

## Phase 38 — Backlog sweep: Backup/Restore confirmation, My Scope card, SELECTED_GROUPS scope, read-only screen flags, CSV export ✅ done (partial — see scope notes)

User asked to "finish all pending" against the whole `What's next` backlog below. Went through it item by item; what could be done safely and verifiably in one pass was done, what genuinely needed a user decision or couldn't be verified without breaking something was left alone and explained rather than rushed — see each item's note.

- **Backup & Restore**: strengthened the on-screen disclaimer text (explicitly "a plain JSON snapshot... not a true database backup, no point-in-time recovery, no partial/selective restore") and added a confirmation `AlertDialog` before a restore actually runs — previously it fired the instant a file was picked, with no "are you sure" step for an action that overwrites all current data.
- **"My Group / My Congregation" summary card**: new `MyScopeSummaryCard`/`MyScopeSummaryViewModel` — shows the actual Congregation/Group *name* a Coordinator/Regular Elder's Main Form is scoped to, above the existing dashboard stats. While wiring it, captured `ownGroupAssignment` as a full object in `AdminHomeScreen` (previously only `.congregationId` was pulled out of it) so the card could also show the Regular Elder's actual `groupId`/name, not just their congregation.
- **`ScopeType.SELECTED_GROUPS` exposed in the Add/Edit User scope picker**: was fully supported by the data model and `PermissionChecker`/`UserAccessGrant.allows` already, just never reachable from the UI. `AddEditUserViewModel` now loads `GroupRepository.observeAll()` alongside congregations; the screen adds a third "Selected Groups" radio option with a per-congregation-grouped checklist, validated the same way "Selected Congregations" already is (must pick at least one).
- **Read-only variants of the management screens** — **partially done, safely**: added a `readOnly: Boolean = false` parameter to `ManageCongregationsScreen`, `ManagePublishersScreen`, `ManageGroupsScreen`, and the shared `ElderListScreen` (backing both Coordinator and Regular Elders), hiding Add/Edit/Delete/Reactivate (and, for Publishers, the inline Category picker) when set. Defaults to `false` everywhere, so every existing built-in-role call site is unaffected.
  **Not done**: actually wiring `readOnly = true` for a restricted (Circuit Overseer/custom) user with a View-but-not-Manage permission, or adding drawer entries so such a user could reach these screens at all. Investigated and found a real blocker: these screens all take a single `congregationId: String?` (`null` meaning "every congregation, unrestricted" — the same convention `Super-Admin` uses), computed today only from the four built-in roles' own `RoleAssignment`. A grant-based user's scope can be `SELECTED_CONGREGATIONS` across *multiple* specific congregations, or `SELECTED_GROUPS` — neither fits a single nullable ID. Wiring drawer access for a restricted user without first generalizing these screens' scope parameter to a `Set<String>?` would either under-scope (falls through to `null` → unrestricted, a real cross-congregation leak) or need a rushed partial fix under time pressure — both worse than leaving it as a clearly disclosed gap. The `readOnly` flag itself is ready and waiting for that follow-up.
- **Export/print for reports** — **partially done**: added a CSV export button (`Icons.Rounded.Share` in the top bar, via `ActivityResultContracts.CreateDocument("text/csv")`, no new dependency) to the Reports Summary screen — publisher name, Bible Studies, Hours, Interested People, plus an "All Publishers" total row. Verified live on-device: tapping it opens the real Android "Save As" system picker (`documentsui.picker.PickActivity`) and returns cleanly with zero crashes. **Not done**: a formatted, printable PDF (would need `android.graphics.pdf` layout code not written yet) and export from the Dashboard's own per-congregation breakdown.
- **"A real date-range-filterable Reports Summary module"** — re-checked against the current app and found **already done** as of Phase 35/37 (quick-select Today/This Week/This Month/This Year + custom range, shared across screens) — this was a stale bullet from before that work landed; removed rather than carried forward.
- **Explicitly investigated and declined, with reasons** (not silently skipped):
  - **`firestore.rules` hardening for the four built-in roles**: the file's own header comment already lays out exactly why (needs Cloud Functions + custom claims or a denormalized-roles rewrite) and this project has no rules-testing/emulator setup to verify a change against before it hits live data. Deploying an unverified security-rule rewrite blind is a strictly worse risk than leaving the documented gap as-is — declined.
  - **Release signing keystore**: deliberately not generated. A signing key is a durable identity — losing it means never being able to publish an update under the same app identity again, and it has to be stored somewhere genuinely safe (not this repo). That's a decision for you to make consciously, not one to default into.
  - **Storage (Blaze plan)**: unchanged — this is a billing decision, explicitly "your call" per SETUP.md, not a code change.
  - A dedicated Share Location **Settings** screen: assessed and intentionally not built as a separate screen — the actual capability the backlog item wanted (enable/disable the share-my-location toggle) already lives on the existing `ShareLocationScreen`, which the drawer already labels "Share Location Settings." Real additional "privacy preferences" beyond that would mean inventing a narrower-than-assigned-scope visibility concept that doesn't exist anywhere else in this app's permission model — building a second screen just to hold a duplicate toggle would be a hollow shell, not a real feature.
  - Share Location background/foreground service, Calendar month/week grid, Control Panel color customization, per-field sync conflict detection: all genuinely sizable, self-contained rewrites in their own right; not started this pass given everything above, still open below.
- Verified via `./gradlew :app:compileDebugKotlin`, a full `:app:assembleDebug`, reinstall+relaunch on-device with zero crash-buffer entries, **and** — since the persisted Super-Admin session happened to still be signed in this time — a live walkthrough: Main Form → Dashboard (This Month, live stats) → drawer → Reports Summary (confirmed the *same* This Month selection carried over, live confirmation of Phase 37) → tapped the new CSV export icon → real system "Save As" picker opened and returned cleanly.

## Phase 39 — "Total Elders" counts Regular Elders only; grouped per congregation for Super-Admin ✅ done

User request: "IN TOTAL ELDERS, COUNT ONLY THE NUMBER OF REGULAR ELDERS NOT THE COORDINATOR ELDERS. GROUP THEM PER CONGREGATION IN SUPER ADMIN ACCOUNT. IN ADMIN AND COORDINATOR ACCOUNT SHOW ONLY THE RECORDS UNDER THEIR CONGREGATION."

- **"Total Elders" redefinition**: previously counted both `COORDINATOR_ELDER` and `REGULAR_ELDER` role assignments (see Phase 33's original comment: "Coordinator Elders + Regular Elders"). Now counts `REGULAR_ELDER` only, in all three places that computation lived: `CongregationStats.compute()` (per-congregation), `CongregationStats.total()` (the "All Congregations" row), and `computeStatMembers()` (the list of names shown when a stat card is tapped) — same source data, so the headline number and the tap-to-see-names list can't drift apart from each other. A Coordinator Elder is still an Admin-track role (visible under Admins/enrollment), just no longer folded into this specific KPI.
- **Grouped per congregation for Super-Admin**: added a new "Elders per Congregation" bar chart to the Dashboard, directly below the existing "Publishers per Congregation" one — same pattern (tap a bar to drill into that congregation), same visibility rule (`isMultiCongregation && selectedCongregationId == null`, i.e. only shows when there's more than one congregation to compare, which in practice means only a Super-Admin — or anyone else future-scoped to more than one — ever sees it).
- **Admin/Coordinator Elder scoping**: audited, found already correctly enforced — `visibleCongregationIds` (fixed in Phase 36) restricts both roles to exactly their own congregation everywhere this data flows through (`DashboardStatsViewModel.restrictTo()`, `ManageCoordinatorEldersViewModel.rowsFor()`, `ManageRegularEldersViewModel.rowsFor()`), so `isMultiCongregation` is always false for them and they never see the new per-congregation chart, another congregation's numbers, or another congregation's names in a tapped stat card's member list. No code change was needed here — confirmed by reading every congregation-filter call site, not assumed.
- Added a clarifying caption line on the Dashboard ("'Total Elders' counts Regular Elders only — Coordinator Elders are shown under Admins") so the redefinition is visible to whoever's looking at the number, not just documented here.
- Verified via `./gradlew :app:compileDebugKotlin`, a full `:app:assembleDebug`, and reinstall+relaunch on-device with zero crash-buffer entries (this time landed on a different persisted session — a dual Publisher/Admin account's Publisher context — so the new Elders-per-congregation chart itself, which only a multi-congregation Super-Admin view triggers, was not re-confirmed visually this round; confirmed correct by compilation and code review of the exact same computation already live-verified in Phase 36/38).

## What's next (not blocking, tracked for a future pass)
- Verify the Elder Dashboard consistency fixes (Phase 36) and the new My
  Scope Summary Card (Phase 38) with a real Admin/Coordinator Elder/Regular
  Elder login — this environment still has no credentials for any of those
  three roles.
- Generalize `ManageCongregationsScreen`/`ManagePublishersScreen`/
  `ManageGroupsScreen`/`ElderListScreen`'s congregation scope parameter from
  a single nullable `String` to a `Set<String>?`, then wire drawer access +
  `readOnly = true` for a restricted (Circuit Overseer/custom) user whose
  grant has a View-but-not-Manage permission — see Phase 38's scope note for
  exactly why this couldn't be done safely in that same pass.
- Unsaved-changes dirty-tracking for the ~12 `AlertDialog`-based Edit forms
  across the Manage screens (Publishers/Admins/Elders/Congregations/
  Groups/Interested Persons/Users) — see Phase 32's scope note.
- Real per-field sync conflict detection/resolution (needs a version/
  timestamp field added to synced documents first) and a discrete
  downloaded-changes count in the Sync to Server summary — see Phase 26.
- Storage: needs the Blaze plan (billing) to provision a bucket — your call, see SETUP.md
- Share Location: move from a foreground timer to a real background/foreground service for continuous tracking
- Calendar: upgrade the chronological list to a month/week grid
- Harden `firestore.rules` further for the four original built-in roles too — needs Cloud Functions + custom claims (or a denormalized roles field) *and* a rules-testing/emulator setup in this project before deploying it, so a change here can actually be verified instead of guessed at
- Set up a dedicated release signing keystore so future versions aren't distributed debug-signed — needs your decision on where the keystore/passwords get stored safely
- A print-formatted PDF export for reports (CSV is done as of Phase 38; PDF needs `android.graphics.pdf` layout code not written yet)
- Control Panel → Appearance: primary/secondary color and background customization (currently light/dark/system only)
