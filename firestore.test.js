const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

let testEnv;

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function serverTs() {
  return new Date(Date.now() - 1000);
}

test("Unauthenticated user cannot read or write notes or history", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("notes").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("history").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("notes").doc("note-1").set({
      userId: ALICE_UID,
      title: "Test",
      content: "Body",
      isPinned: false,
      clientCreatedAtMillis: 1000,
      clientUpdatedAtMillis: 1000,
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );
});

test("Authenticated user can CRUD their own notes and history", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const noteRef = aliceDb.collection("users").doc(ALICE_UID).collection("notes").doc("note-uuid-1");

  await assertSucceeds(
    noteRef.set({
      userId: ALICE_UID,
      title: "My Calculation Note",
      content: "123456789123456789 * 987654321987654321",
      isPinned: true,
      clientCreatedAtMillis: 1700000000000,
      clientUpdatedAtMillis: 1700000000000,
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("notes").get());

  await assertSucceeds(
    noteRef.update({
      title: "Updated Note",
      clientUpdatedAtMillis: 1700000005000,
      updatedAt: serverTs(),
    })
  );

  const histRef = aliceDb.collection("users").doc(ALICE_UID).collection("history").doc("hist-uuid-1");
  await assertSucceeds(
    histRef.set({
      userId: ALICE_UID,
      expression: "2+2",
      result: "4",
      calculatorMode: "STANDARD",
      angleUnit: "DEG",
      precision: 32,
      isApproximated: false,
      clientCreatedAtMillis: 1700000000000,
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("history").get());
  await assertSucceeds(histRef.delete());
  await assertSucceeds(noteRef.delete());
});

test("Cross-user access to notes, history, and profile is denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("notes").doc("alice-note-1").set({
      userId: ALICE_UID,
      title: "Secret Note",
      content: "Private",
      isPinned: false,
      clientCreatedAtMillis: 1000,
      clientUpdatedAtMillis: 1000,
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );

  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("notes").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("notes").doc("alice-note-1").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("notes").doc("alice-note-1").delete());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
});

test("Shadow fields, spoofed userId, and invalid schema payloads are rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  // Ghost field rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("notes").doc("note-bad-1").set({
      userId: ALICE_UID,
      title: "Note",
      content: "Body",
      isPinned: false,
      isAdmin: true,
      clientCreatedAtMillis: 1000,
      clientUpdatedAtMillis: 1000,
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );

  // Spoofed userId rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("notes").doc("note-bad-2").set({
      userId: BOB_UID,
      title: "Note",
      content: "Body",
      isPinned: false,
      clientCreatedAtMillis: 1000,
      clientUpdatedAtMillis: 1000,
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );

  // Invalid calculatorMode enum rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("history").doc("hist-bad-1").set({
      userId: ALICE_UID,
      expression: "2+2",
      result: "4",
      calculatorMode: "HACKED",
      angleUnit: "DEG",
      precision: 32,
      isApproximated: false,
      clientCreatedAtMillis: 1000,
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );
});
