import {
  initializeTestEnvironment,
  assertSucceeds,
  assertFails,
} from "@firebase/rules-unit-testing";
import { readFileSync } from "fs";
import {
  doc, getDoc, setDoc, updateDoc, deleteDoc, collection,
} from "firebase/firestore";

const RULES_PATH = new URL("../firestore.rules", import.meta.url).pathname.replace(/^\/([A-Za-z]):/, "$1:");

let testEnv;
let results = [];

function record(name, pass, detail) {
  results.push({ name, pass, detail });
  console.log(`${pass ? "PASS" : "FAIL"} - ${name}${detail ? " :: " + detail : ""}`);
}

async function run() {
  testEnv = await initializeTestEnvironment({
    projectId: "gopreach-rules-test",
    firestore: { rules: readFileSync(RULES_PATH, "utf8"), host: "127.0.0.1", port: 8089 },
  });

  // Seed base data as admin (bypasses rules).
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    // People
    await setDoc(doc(db, "people", "adminA"), {
      isSuperAdmin: false, activeAdminRole: "ADMIN_PER_CONGREGATION", activeCongregationId: "congA",
    });
    await setDoc(doc(db, "people", "elderRegA"), {
      isSuperAdmin: false, activeAdminRole: "REGULAR_ELDER", activeCongregationId: "congA",
    });
    await setDoc(doc(db, "people", "elderRegA_stale"), {
      // Simulates the OLD, buggy client that never denormalized this field.
      isSuperAdmin: false, activeAdminRole: "REGULAR_ELDER", activeCongregationId: null,
    });
    await setDoc(doc(db, "people", "secretaryA"), {
      isSuperAdmin: false, activeAdminRole: "SECRETARY", activeCongregationId: "congA",
    });
    await setDoc(doc(db, "people", "pubA"), {
      isSuperAdmin: false, activeAdminRole: null, activeCongregationId: "congA",
    });
    await setDoc(doc(db, "people", "pubB"), {
      isSuperAdmin: false, activeAdminRole: null, activeCongregationId: "congA",
    });
    await setDoc(doc(db, "people", "pubOtherCong"), {
      isSuperAdmin: false, activeAdminRole: null, activeCongregationId: "congB",
    });
    await setDoc(doc(db, "people", "superAdmin1"), {
      isSuperAdmin: true, activeAdminRole: "SUPER_ADMIN", activeCongregationId: null,
    });

    // A Return Visit owned by pubA, in congA.
    await setDoc(doc(db, "interestedPeople", "rv1"), {
      publisherPersonId: "pubA", congregationId: "congA", pipelineStage: "RETURN_VISIT", name: "Juan",
    });
    // A Bible Study owned by pubA, in congA.
    await setDoc(doc(db, "interestedPeople", "bs1"), {
      publisherPersonId: "pubA", congregationId: "congA", pipelineStage: "BIBLE_STUDY", name: "Maria",
    });
    // Another congregation's Return Visit, owned by pubOtherCong.
    await setDoc(doc(db, "interestedPeople", "rvOther"), {
      publisherPersonId: "pubOtherCong", congregationId: "congB", pipelineStage: "RETURN_VISIT", name: "Pedro",
    });

    // A Monthly Report belonging to pubA, in congA, not yet posted.
    await setDoc(doc(db, "monthlyReports", "report1"), {
      publisherPersonId: "pubA", congregationId: "congA", status: "SUBMITTED",
      periodMonth: 1, bibleStudiesCount: 2,
    });

    // A plain RoleAssignment (Coordinator Elder) in congA, for privilege tests.
    await setDoc(doc(db, "roleAssignments", "ra1"), {
      personId: "someoneA", roleType: "ADMIN:COORDINATOR_ELDER", congregationId: "congA", status: "ACTIVE",
    });
  });

  const asAdminA = testEnv.authenticatedContext("adminA", { email: "adminA@x.com" }).firestore();
  const asElderRegA = testEnv.authenticatedContext("elderRegA", { email: "elderRegA@x.com" }).firestore();
  const asElderRegA_stale = testEnv.authenticatedContext("elderRegA_stale", { email: "elderRegA_stale@x.com" }).firestore();
  const asSecretaryA = testEnv.authenticatedContext("secretaryA", { email: "secretaryA@x.com" }).firestore();
  const asPubA = testEnv.authenticatedContext("pubA", { email: "pubA@x.com" }).firestore();
  const asPubB = testEnv.authenticatedContext("pubB", { email: "pubB@x.com" }).firestore();
  const asSuperAdmin = testEnv.authenticatedContext("superAdmin1", { email: "superAdmin1@x.com" }).firestore();

  // ============ TEST 1: Regular Elder editing another publisher's Monthly Report ============
  // This is the exact bug that was fixed (activeCongregationId now populated).
  await (async () => {
    const ref = doc(asElderRegA, "monthlyReports", "report1");
    const ok = await assertSucceeds(updateDoc(ref, { bibleStudiesCount: 5 }));
    record("Regular Elder (fixed) can edit another publisher's Monthly Report in own congregation", true);
  })().catch((e) => record("Regular Elder (fixed) can edit another publisher's Monthly Report in own congregation", false, e.message));

  // Same scenario but with the STALE (pre-fix) null activeCongregationId — should FAIL,
  // proving this really was the root cause and the rule itself is otherwise correct.
  await (async () => {
    const ref = doc(asElderRegA_stale, "monthlyReports", "report1");
    await assertFails(updateDoc(ref, { bibleStudiesCount: 6 }));
    record("Regular Elder (stale/pre-fix null congregation) is REJECTED editing another publisher's report — confirms this was the real bug", true);
  })().catch((e) => record("Regular Elder (stale/pre-fix null congregation) is REJECTED editing another publisher's report — confirms this was the real bug", false, e.message));

  // ============ TEST 2: Secretary has Service-Overseer-equivalent report access ============
  await (async () => {
    const ref = doc(asSecretaryA, "monthlyReports", "report1");
    await assertSucceeds(updateDoc(ref, { bibleStudiesCount: 7 }));
    record("Secretary can edit another publisher's Monthly Report in own congregation (Service-Overseer parity)", true);
  })().catch((e) => record("Secretary can edit another publisher's Monthly Report in own congregation (Service-Overseer parity)", false, e.message));

  // ============ TEST 3: Congregation Admin scoping ============
  await (async () => {
    // Admin A can manage a roleAssignment in their OWN congregation (congA).
    const ref = doc(asAdminA, "roleAssignments", "ra1");
    await assertSucceeds(updateDoc(ref, { status: "INACTIVE" }));
    record("Congregation Admin can edit a roleAssignment in their own congregation", true);
  })().catch((e) => record("Congregation Admin can edit a roleAssignment in their own congregation", false, e.message));

  await (async () => {
    // Admin A tries to create a roleAssignment for congB (another congregation) — must fail.
    const ref = doc(asAdminA, "roleAssignments", "raOther");
    await assertFails(setDoc(ref, {
      personId: "someoneB", roleType: "ADMIN:COORDINATOR_ELDER", congregationId: "congB", status: "ACTIVE",
    }));
    record("Congregation Admin CANNOT create a roleAssignment for another congregation", true);
  })().catch((e) => record("Congregation Admin CANNOT create a roleAssignment for another congregation", false, e.message));

  await (async () => {
    // Admin A tries to promote someone to SUPER_ADMIN within their own congregation — must fail.
    const ref = doc(asAdminA, "roleAssignments", "raEscalate");
    await assertFails(setDoc(ref, {
      personId: "adminA", roleType: "ADMIN:SUPER_ADMIN", congregationId: "congA", status: "ACTIVE",
    }));
    record("Congregation Admin CANNOT assign themselves/anyone SUPER_ADMIN", true);
  })().catch((e) => record("Congregation Admin CANNOT assign themselves/anyone SUPER_ADMIN", false, e.message));

  await (async () => {
    // Admin A tries to create a CIRCUIT_OVERSEER assignment — must fail.
    const ref = doc(asAdminA, "roleAssignments", "raCircuit");
    await assertFails(setDoc(ref, {
      personId: "someoneA", roleType: "ADMIN:CIRCUIT_OVERSEER", congregationId: "congA", status: "ACTIVE",
    }));
    record("Congregation Admin CANNOT create a CIRCUIT_OVERSEER assignment", true);
  })().catch((e) => record("Congregation Admin CANNOT create a CIRCUIT_OVERSEER assignment", false, e.message));

  // ============ TEST 4: Self-escalation via people.isSuperAdmin ============
  await (async () => {
    // A plain publisher tries to set their own isSuperAdmin=true directly.
    const ref = doc(asPubA, "people", "pubA");
    await assertFails(updateDoc(ref, { isSuperAdmin: true }));
    record("Publisher CANNOT set isSuperAdmin=true on their own person doc", true);
  })().catch((e) => record("Publisher CANNOT set isSuperAdmin=true on their own person doc", false, e.message));

  await (async () => {
    // A plain publisher tries to set SOMEONE ELSE's isSuperAdmin=true.
    const ref = doc(asPubA, "people", "pubB");
    await assertFails(updateDoc(ref, { isSuperAdmin: true }));
    record("Publisher CANNOT set isSuperAdmin=true on another person's doc", true);
  })().catch((e) => record("Publisher CANNOT set isSuperAdmin=true on another person's doc", false, e.message));

  await (async () => {
    // A publisher can still update their own ordinary fields (no regression).
    const ref = doc(asPubA, "people", "pubA");
    await assertSucceeds(updateDoc(ref, { contact: "09171234567" }));
    record("Publisher CAN still update their own ordinary fields (no regression)", true);
  })().catch((e) => record("Publisher CAN still update their own ordinary fields (no regression)", false, e.message));

  // ============ TEST 5: Territory Map Return Visit sharing ============
  await (async () => {
    // Publisher B adds Return Visit history to Publisher A's Return Visit.
    const ref = doc(collection(asPubB, "interestedPeople", "rv1", "visits"));
    await assertSucceeds(setDoc(ref, {
      interestedPersonId: "rv1", createdByPersonId: "pubB", publisherPersonId: "pubB",
      visitDate: Date.now(), outcome: "CALL_AGAIN", timeConsumedMinutes: 10,
    }));
    record("Publisher B CAN add Return Visit history to Publisher A's Return Visit", true);
  })().catch((e) => record("Publisher B CAN add Return Visit history to Publisher A's Return Visit", false, e.message));

  await (async () => {
    // Publisher B tries to edit the PARENT Return Visit (should fail — owner-only).
    const ref = doc(asPubB, "interestedPeople", "rv1");
    await assertFails(updateDoc(ref, { name: "Renamed by B" }));
    record("Publisher B CANNOT edit the parent Return Visit they don't own", true);
  })().catch((e) => record("Publisher B CANNOT edit the parent Return Visit they don't own", false, e.message));

  await (async () => {
    // Publisher B tries to add Bible Study visit history to Publisher A's Bible Study — must fail.
    const ref = doc(collection(asPubB, "interestedPeople", "bs1", "visits"));
    await assertFails(setDoc(ref, {
      interestedPersonId: "bs1", createdByPersonId: "pubB", publisherPersonId: "pubB",
      visitDate: Date.now(), outcome: "CALL_AGAIN", timeConsumedMinutes: 10,
    }));
    record("Publisher B CANNOT add Bible Study visit history for someone else's Bible Study", true);
  })().catch((e) => record("Publisher B CANNOT add Bible Study visit history for someone else's Bible Study", false, e.message));

  await (async () => {
    // Publisher A (the actual owner) CAN add their own Bible Study visit history.
    const ref = doc(collection(asPubA, "interestedPeople", "bs1", "visits"));
    await assertSucceeds(setDoc(ref, {
      interestedPersonId: "bs1", createdByPersonId: "pubA", publisherPersonId: "pubA",
      visitDate: Date.now(), outcome: "NEXT_TOPIC", timeConsumedMinutes: 15,
    }));
    record("Publisher A (owner) CAN add their own Bible Study visit history", true);
  })().catch((e) => record("Publisher A (owner) CAN add their own Bible Study visit history", false, e.message));

  await (async () => {
    // Publisher B tries to access/edit a Return Visit from a DIFFERENT congregation entirely.
    const ref = doc(asPubB, "interestedPeople", "rvOther");
    await assertFails(updateDoc(ref, { name: "Hacked" }));
    record("Publisher B CANNOT edit a Return Visit belonging to another congregation", true);
  })().catch((e) => record("Publisher B CANNOT edit a Return Visit belonging to another congregation", false, e.message));

  // ============ TEST 6: Super Admin retains full access ============
  await (async () => {
    const ref = doc(asSuperAdmin, "interestedPeople", "rvOther");
    await assertSucceeds(updateDoc(ref, { name: "Super Admin edit" }));
    record("Super Admin CAN edit any congregation's records", true);
  })().catch((e) => record("Super Admin CAN edit any congregation's records", false, e.message));

  // ============ TEST 7: catch-all `{document=**}` block (schedules,
  // territories, shared locations, app settings, backups, audit log) ============
  // This is the exact bug that was fixed (`document.matches(...)` — `document`
  // is a `path`, which has no `.matches()` — threw "Function not found" on
  // every evaluation, and an evaluation error denies, so this catch-all
  // rejected EVERY read/write to these collections, for every account,
  // always, with PERMISSION_DENIED — reported app-side as "Sync Error").
  await (async () => {
    const ref = doc(asPubA, "territories", "t1");
    await assertSucceeds(setDoc(ref, { name: "Territory 1", congregationId: "congA" }));
    record("Publisher CAN write a territory (catch-all block reachable, not a dead rule)", true);
  })().catch((e) => record("Publisher CAN write a territory (catch-all block reachable, not a dead rule)", false, e.message));

  await (async () => {
    const ref = doc(asPubA, "schedules", "s1");
    await assertSucceeds(setDoc(ref, { congregationId: "congA" }));
    record("Publisher CAN write a schedule (catch-all block reachable)", true);
  })().catch((e) => record("Publisher CAN write a schedule (catch-all block reachable)", false, e.message));

  // The catch-all must still stay OUT of every collection with its own real
  // rule — this is the privilege-escalation gap the guard was built to close
  // in the first place (see this block's own comment in firestore.rules);
  // re-check it here so this suite fails loudly if a future edit to the
  // exclusion list (or its shape) ever reopens it.
  await (async () => {
    const ref = doc(asPubA, "userAccessGrants", "pubA");
    await assertFails(setDoc(ref, { scopeType: "ALL_CONGREGATIONS", permissions: ["MANAGE_USERS"] }));
    record("Publisher still CANNOT self-grant via userAccessGrants (catch-all correctly excludes it)", true);
  })().catch((e) => record("Publisher still CANNOT self-grant via userAccessGrants (catch-all correctly excludes it)", false, e.message));

  await testEnv.cleanup();

  console.log("\n=== SUMMARY ===");
  const failed = results.filter((r) => !r.pass);
  console.log(`${results.length - failed.length}/${results.length} passed`);
  if (failed.length > 0) {
    console.log("FAILURES:");
    failed.forEach((f) => console.log(`  - ${f.name}: ${f.detail}`));
    process.exitCode = 1;
  }
}

run().catch((e) => {
  console.error("Test harness crashed:", e);
  process.exitCode = 1;
});
