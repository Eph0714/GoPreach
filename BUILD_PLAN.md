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

## What's next (not blocking, tracked for a future pass)
- Storage: needs the Blaze plan (billing) to provision a bucket — your call, see SETUP.md
- Share Location: move from a foreground timer to a real background/foreground service for continuous tracking
- Calendar: upgrade the chronological list to a month/week grid
- Backup & Restore: this is a JSON snapshot of the offline cache, not a true DB backup — fine as a safety net, but call this out to users so expectations are set correctly
- Harden `firestore.rules` beyond "authenticated = allowed" (needs Cloud Functions + custom claims, or a denormalized roles field, to check specific role/scope per write — noted inline in the rules file)
