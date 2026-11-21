const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const VALID_PORTAL_KEY =
  "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
const OTHER_PORTAL_KEY =
  "fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastTime(offsetMs = 5000) {
  return new Date(Date.now() - offsetMs);
}

function validClientPayload(uid, overrides = {}) {
  const ts = pastTime(10000);
  return {
    userId: uid,
    name: "Northstar Studio",
    company: "Northstar Architecture",
    notes: "Brand refresh and Q4 web deliverables",
    colorHex: "#2555E6",
    sortOrder: 0,
    archived: false,
    portalEnabled: false,
    portalEmail: "",
    portalKey: "",
    createdAt: ts,
    updatedAt: ts,
    ...overrides,
  };
}

function validProjectPayload(uid, clientId, overrides = {}) {
  const ts = pastTime(9000);
  return {
    userId: uid,
    clientId,
    name: "Northstar Studio",
    description: "Primary retainer deliverables",
    colorHex: "#2555E6",
    sortOrder: 0,
    defaultProject: true,
    archived: false,
    createdAt: ts,
    updatedAt: ts,
    ...overrides,
  };
}

function validTaskPayload(uid, clientId, projectId, overrides = {}) {
  const ts = pastTime(8000);
  return {
    userId: uid,
    clientId,
    projectId,
    title: "Deliver homepage wireframes",
    details: "Include mobile and desktop breakpoints",
    priority: "HIGH",
    completed: false,
    dueLabel: "Fri",
    createdByClient: false,
    createdAt: ts,
    updatedAt: ts,
    ...overrides,
  };
}

function validPortalPayload(ownerId, clientId, overrides = {}) {
  const ts = pastTime(9000);
  return {
    ownerId,
    clientId,
    clientName: "Northstar Studio",
    clientCompany: "Northstar Architecture",
    clientColorHex: "#2555E6",
    portalEmail: "alex@northstar.io",
    active: true,
    createdAt: ts,
    updatedAt: ts,
    ...overrides,
  };
}

function validPortalProjectPayload(portalKey, ownerId, clientId, overrides = {}) {
  const ts = pastTime(8500);
  return {
    portalKey,
    ownerId,
    clientId,
    name: "Northstar Studio",
    description: "Main deliverables",
    colorHex: "#2555E6",
    sortOrder: 0,
    defaultProject: true,
    createdAt: ts,
    updatedAt: ts,
    ...overrides,
  };
}

function validPortalTaskPayload(portalKey, ownerId, clientId, projectId, overrides = {}) {
  const ts = pastTime(7000);
  return {
    portalKey,
    ownerId,
    clientId,
    projectId,
    title: "Review new landing page hero",
    details: "Need feedback before Friday",
    priority: "NORMAL",
    completed: false,
    dueLabel: "Fri",
    createdByClient: true,
    createdAt: ts,
    updatedAt: ts,
    ...overrides,
  };
}

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

test("Unauthenticated users cannot access owner clients, projects, or tasks", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/clients`).get());
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/projects`).get());
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/tasks`).get());
  await assertFails(
    unauthDb
      .doc(`users/${ALICE_UID}/clients/client_1`)
      .set(validClientPayload(ALICE_UID))
  );
  await assertFails(
    unauthDb
      .doc(`users/${ALICE_UID}/projects/default_client_1`)
      .set(validProjectPayload(ALICE_UID, "client_1"))
  );
});

test("Owner can atomically create client + default project + tasks, edit, move project, and delete", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  // Orphaned project without parent client must fail
  await assertFails(
    aliceDb
      .doc(`users/${ALICE_UID}/projects/proj_orphan`)
      .set(validProjectPayload(ALICE_UID, "missing_client"))
  );

  // Atomic batch: Client + Default Project
  const batch = aliceDb.batch();
  const clientRef = aliceDb.doc(`users/${ALICE_UID}/clients/client_1`);
  const defaultProjRef = aliceDb.doc(`users/${ALICE_UID}/projects/default_client_1`);
  batch.set(clientRef, validClientPayload(ALICE_UID));
  batch.set(defaultProjRef, validProjectPayload(ALICE_UID, "client_1"));
  await assertSucceeds(batch.commit());

  // Second project for the same client
  const proj2Ref = aliceDb.doc(`users/${ALICE_UID}/projects/proj_2`);
  await assertSucceeds(
    proj2Ref.set(
      validProjectPayload(ALICE_UID, "client_1", {
        name: "Mobile App Redesign",
        sortOrder: 1,
        defaultProject: false,
      })
    )
  );

  // Orphaned task (missing project) must fail
  await assertFails(
    aliceDb
      .doc(`users/${ALICE_UID}/tasks/task_orphan`)
      .set(validTaskPayload(ALICE_UID, "client_1", "missing_proj"))
  );

  // Valid task under default project
  const taskRef = aliceDb.doc(`users/${ALICE_UID}/tasks/task_1`);
  await assertSucceeds(
    taskRef.set(validTaskPayload(ALICE_UID, "client_1", "default_client_1"))
  );

  // Owner queries (mirrors ClientDeskRepository)
  await assertSucceeds(aliceDb.collection(`users/${ALICE_UID}/clients`).get());
  await assertSucceeds(aliceDb.collection(`users/${ALICE_UID}/projects`).get());
  await assertSucceeds(aliceDb.collection(`users/${ALICE_UID}/tasks`).get());

  // Owner updates task & moves it to proj_2
  await assertSucceeds(
    taskRef.update({
      title: "Finalize mobile nav",
      details: "Updated for v2",
      priority: "NORMAL",
      dueLabel: "Mon",
      completed: false,
      projectId: "proj_2",
      updatedAt: pastTime(1000),
    })
  );

  // Toggle completed
  await assertSucceeds(
    taskRef.update({
      completed: true,
      updatedAt: pastTime(500),
    })
  );

  // Reject ghost field on project update
  await assertFails(
    proj2Ref.update({
      name: "Renamed",
      ghostField: "bad",
      updatedAt: pastTime(200),
    })
  );
});

test("Legacy tasks without projectId can be migrated atomically to a client's default project", async () => {
  // Seed legacy client & legacy task (without portal fields, projectId, createdByClient)
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const adminDb = ctx.firestore();
    const ts = pastTime(20000);
    await adminDb.doc(`users/${ALICE_UID}/clients/legacy_c1`).set({
      userId: ALICE_UID,
      name: "Legacy Corp",
      company: "",
      notes: "",
      colorHex: "#2555E6",
      sortOrder: 0,
      archived: false,
      createdAt: ts,
      updatedAt: ts,
    });
    await adminDb.doc(`users/${ALICE_UID}/tasks/legacy_t1`).set({
      userId: ALICE_UID,
      clientId: "legacy_c1",
      title: "Old task before projects",
      details: "",
      priority: "NORMAL",
      completed: false,
      dueLabel: "",
      createdAt: ts,
      updatedAt: ts,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const batch = aliceDb.batch();
  batch.set(
    aliceDb.doc(`users/${ALICE_UID}/projects/default_legacy_c1`),
    validProjectPayload(ALICE_UID, "legacy_c1", { name: "Legacy Corp" })
  );
  batch.update(aliceDb.doc(`users/${ALICE_UID}/tasks/legacy_t1`), {
    projectId: "default_legacy_c1",
    createdByClient: false,
    updatedAt: pastTime(1000),
  });
  await assertSucceeds(batch.commit());
});

test("Client Portal enforces no enumeration, read-only projects, create-only open tasks, and zero edits/deletes", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  // Owner creates client + project + portal + mirrored project + mirrored task
  const setupBatch = aliceDb.batch();
  setupBatch.set(
    aliceDb.doc(`users/${ALICE_UID}/clients/client_1`),
    validClientPayload(ALICE_UID, {
      portalEnabled: true,
      portalEmail: "alex@northstar.io",
      portalKey: VALID_PORTAL_KEY,
    })
  );
  setupBatch.set(
    aliceDb.doc(`users/${ALICE_UID}/projects/default_client_1`),
    validProjectPayload(ALICE_UID, "client_1")
  );
  setupBatch.set(
    aliceDb.doc(`clientPortals/${VALID_PORTAL_KEY}`),
    validPortalPayload(ALICE_UID, "client_1")
  );
  setupBatch.set(
    aliceDb.doc(`clientPortals/${VALID_PORTAL_KEY}/projects/default_client_1`),
    validPortalProjectPayload(VALID_PORTAL_KEY, ALICE_UID, "client_1")
  );
  setupBatch.set(
    aliceDb.doc(`clientPortals/${VALID_PORTAL_KEY}/tasks/owner_task_1`),
    validPortalTaskPayload(
      VALID_PORTAL_KEY,
      ALICE_UID,
      "client_1",
      "default_client_1",
      { createdByClient: false, title: "Owner created task" }
    )
  );
  await assertSucceeds(setupBatch.commit());

  const clientPortalDb = testEnv.unauthenticatedContext().firestore();

  // 1. Listing /clientPortals root collection MUST be blocked (no account/email scraping)
  await assertFails(clientPortalDb.collection("clientPortals").get());

  // 2. Client with valid portal key can GET portal metadata and LIST scoped projects & tasks
  await assertSucceeds(clientPortalDb.doc(`clientPortals/${VALID_PORTAL_KEY}`).get());
  await assertSucceeds(
    clientPortalDb
      .collection(`clientPortals/${VALID_PORTAL_KEY}/projects`)
      .where("portalKey", "==", VALID_PORTAL_KEY)
      .get()
  );
  await assertSucceeds(
    clientPortalDb
      .collection(`clientPortals/${VALID_PORTAL_KEY}/tasks`)
      .where("portalKey", "==", VALID_PORTAL_KEY)
      .get()
  );

  // 3. Unfiltered list query without portalKey filter fails
  await assertFails(
    clientPortalDb.collection(`clientPortals/${VALID_PORTAL_KEY}/tasks`).get()
  );

  // 4. Client CANNOT create, update, or delete projects
  await assertFails(
    clientPortalDb
      .doc(`clientPortals/${VALID_PORTAL_KEY}/projects/hacked_proj`)
      .set(validPortalProjectPayload(VALID_PORTAL_KEY, ALICE_UID, "client_1"))
  );
  await assertFails(
    clientPortalDb
      .doc(`clientPortals/${VALID_PORTAL_KEY}/projects/default_client_1`)
      .update({ name: "Hacked", updatedAt: pastTime(500) })
  );
  await assertFails(
    clientPortalDb
      .doc(`clientPortals/${VALID_PORTAL_KEY}/projects/default_client_1`)
      .delete()
  );

  // 5. Client CAN submit a brand-new OPEN task into their existing project
  const clientTaskRef = clientPortalDb.doc(
    `clientPortals/${VALID_PORTAL_KEY}/tasks/client_task_1`
  );
  await assertSucceeds(
    clientTaskRef.set(
      validPortalTaskPayload(
        VALID_PORTAL_KEY,
        ALICE_UID,
        "client_1",
        "default_client_1",
        { title: "Add dark mode screenshots", priority: "HIGH", completed: false, createdByClient: true }
      )
    )
  );

  // 6. Client CANNOT submit a task already marked completed, with createdByClient=false, or wrong owner/project
  await assertFails(
    clientPortalDb
      .doc(`clientPortals/${VALID_PORTAL_KEY}/tasks/client_task_bad_done`)
      .set(
        validPortalTaskPayload(
          VALID_PORTAL_KEY,
          ALICE_UID,
          "client_1",
          "default_client_1",
          { completed: true, createdByClient: true }
        )
      )
  );
  await assertFails(
    clientPortalDb
      .doc(`clientPortals/${VALID_PORTAL_KEY}/tasks/client_task_bad_proj`)
      .set(
        validPortalTaskPayload(
          VALID_PORTAL_KEY,
          ALICE_UID,
          "client_1",
          "nonexistent_proj",
          { completed: false, createdByClient: true }
        )
      )
  );
  await assertFails(
    clientPortalDb
      .doc(`clientPortals/${VALID_PORTAL_KEY}/tasks/client_task_spoof_owner`)
      .set(
        validPortalTaskPayload(
          VALID_PORTAL_KEY,
          BOB_UID,
          "client_1",
          "default_client_1",
          { completed: false, createdByClient: true }
        )
      )
  );

  // 7. Client CANNOT edit, toggle status, or delete ANY existing task (owner's OR their own)
  await assertFails(
    clientPortalDb
      .doc(`clientPortals/${VALID_PORTAL_KEY}/tasks/owner_task_1`)
      .update({ completed: true, updatedAt: pastTime(400) })
  );
  await assertFails(
    clientTaskRef.update({ title: "Edited by client", updatedAt: pastTime(400) })
  );
  await assertFails(clientTaskRef.delete());

  // 8. Owner CAN update task status and delete tasks in the portal mirror
  await assertSucceeds(
    aliceDb
      .doc(`clientPortals/${VALID_PORTAL_KEY}/tasks/client_task_1`)
      .update({ completed: true, updatedAt: pastTime(300) })
  );
  await assertSucceeds(
    aliceDb.doc(`clientPortals/${VALID_PORTAL_KEY}/tasks/client_task_1`).delete()
  );

  // 9. Another authenticated user (Bob) CANNOT tamper with Alice's portal or owner collections
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection(`users/${ALICE_UID}/projects`).get());
  await assertFails(
    bobDb
      .doc(`clientPortals/${VALID_PORTAL_KEY}`)
      .update({ active: false, updatedAt: pastTime(200) })
  );
  await assertFails(
    bobDb
      .doc(`clientPortals/${VALID_PORTAL_KEY}/tasks/owner_task_1`)
      .update({ completed: true, updatedAt: pastTime(200) })
  );
  await assertFails(
    bobDb.doc(`clientPortals/${OTHER_PORTAL_KEY}`).set(
      validPortalPayload(ALICE_UID, "client_1")
    )
  );
});
