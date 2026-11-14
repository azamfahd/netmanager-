const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function serverTs(db) {
  return db.app.firebase.firestore.FieldValue.serverTimestamp();
}

function futureTs(db, offsetMs = 600000) {
  return db.app.firebase.firestore.Timestamp.fromMillis(Date.now() + offsetMs);
}

function pastTs(db, offsetMs = 10000) {
  return db.app.firebase.firestore.Timestamp.fromMillis(Date.now() - offsetMs);
}

function validMonthlyPayload(db, userId, year, month, overrides = {}) {
  const monthKey = `${year}-${String(month).padStart(2, "0")}`;
  return {
    userId,
    monthKey,
    monthNameAr: `شهر ${month} ${year}`,
    year,
    month,
    yearMonthSerial: year * 12 + month,
    totalRevenue: 15000,
    totalCost: 6000,
    netProfit: 9000,
    soldCardsCount: 30,
    totalCardsCount: 40,
    topSellingProfile: "500-Day",
    totalBytesConsumed: 5000000,
    packageBreakdown: [
      {
        packageName: "500-Day",
        cardsSold: 30,
        totalRevenue: 15000,
        netProfit: 9000,
        sharePercentage: 100,
      },
    ],
    createdAt: serverTs(db),
    updatedAt: serverTs(db),
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

test("Default-deny: rejects reads/writes on unconfigured paths even when authenticated", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("randomGlobal").doc("doc1").get());
  await assertFails(
    aliceDb.collection("randomGlobal").doc("doc1").set({ hello: "world" })
  );
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("unknownFinanceSub")
      .doc("doc1")
      .set({ a: 1 })
  );
});

test("Unauthenticated user: denied get, list, create, update, delete on monthlyProfits & packagePricing", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const adminDb = ctx.firestore();
    await adminDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("monthlyProfits")
      .doc("2026-10")
      .set(validMonthlyPayload(adminDb, ALICE_UID, 2026, 10, {
        createdAt: pastTs(adminDb),
        updatedAt: pastTs(adminDb),
      }));
  });

  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const profitsCol = unauthDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("monthlyProfits");
  const pricingCol = unauthDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("packagePricing");

  await assertFails(profitsCol.doc("2026-10").get());
  await assertFails(profitsCol.orderBy("yearMonthSerial", "asc").get());
  await assertFails(
    profitsCol
      .doc("2026-11")
      .set(validMonthlyPayload(unauthDb, ALICE_UID, 2026, 11))
  );
  await assertFails(profitsCol.doc("2026-10").update({ totalRevenue: 999 }));
  await assertFails(profitsCol.doc("2026-10").delete());

  await assertFails(pricingCol.get());
  await assertFails(
    pricingCol.doc("pkg1").set({
      userId: ALICE_UID,
      profileName: "500-Day",
      costPrice: 200,
      salePrice: 500,
      updatedAt: serverTs(unauthDb),
    })
  );
});

test("Cross-user isolation: Alice cannot read, query, write, update, or delete Bob's finance records", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const adminDb = ctx.firestore();
    await adminDb
      .collection("users")
      .doc(BOB_UID)
      .collection("monthlyProfits")
      .doc("2026-09")
      .set(validMonthlyPayload(adminDb, BOB_UID, 2026, 9, {
        createdAt: pastTs(adminDb),
        updatedAt: pastTs(adminDb),
      }));
    await adminDb
      .collection("users")
      .doc(BOB_UID)
      .collection("packagePricing")
      .doc("500-Day")
      .set({
        userId: BOB_UID,
        profileName: "500-Day",
        costPrice: 200,
        salePrice: 500,
        updatedAt: pastTs(adminDb),
      });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobProfits = aliceDb
    .collection("users")
    .doc(BOB_UID)
    .collection("monthlyProfits");
  const bobPricing = aliceDb
    .collection("users")
    .doc(BOB_UID)
    .collection("packagePricing");

  await assertFails(bobProfits.doc("2026-09").get());
  await assertFails(bobProfits.orderBy("yearMonthSerial", "asc").get());
  await assertFails(
    bobProfits.doc("2026-10").set(validMonthlyPayload(aliceDb, ALICE_UID, 2026, 10))
  );
  await assertFails(bobProfits.doc("2026-09").update({ totalRevenue: 0 }));
  await assertFails(bobProfits.doc("2026-09").delete());
  await assertFails(bobPricing.doc("500-Day").get());
  await assertFails(
    bobPricing.doc("500-Day").set({
      userId: ALICE_UID,
      profileName: "500-Day",
      costPrice: 1,
      salePrice: 2,
      updatedAt: serverTs(aliceDb),
    })
  );
});

test("Owner CRUD & sequential chronological query + pagination by yearMonthSerial succeed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const col = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("monthlyProfits");

  // Insert out of order intentionally
  await assertSucceeds(
    col.doc("2026-10").set(validMonthlyPayload(aliceDb, ALICE_UID, 2026, 10))
  );
  await assertSucceeds(
    col.doc("2025-11").set(validMonthlyPayload(aliceDb, ALICE_UID, 2025, 11))
  );
  await assertSucceeds(
    col.doc("2026-03").set(validMonthlyPayload(aliceDb, ALICE_UID, 2026, 3))
  );

  // Page 1: limit(2) ascending
  const page1 = await assertSucceeds(
    col.orderBy("yearMonthSerial", "asc").limit(2).get()
  );
  assert.deepEqual(
    page1.docs.map((d) => d.id),
    ["2025-11", "2026-03"]
  );

  // Page 2: startAfter(last) ascending
  const page2 = await assertSucceeds(
    col
      .orderBy("yearMonthSerial", "asc")
      .startAfter(page1.docs[page1.docs.length - 1])
      .limit(2)
      .get()
  );
  assert.deepEqual(
    page2.docs.map((d) => d.id),
    ["2026-10"]
  );

  // Update preserving immutable createdAt & keys
  await assertSucceeds(
    col.doc("2026-10").update({
      totalRevenue: 20000,
      netProfit: 12000,
      soldCardsCount: 40,
      updatedAt: serverTs(aliceDb),
    })
  );

  const updatedSnap = await assertSucceeds(col.doc("2026-10").get());
  assert.equal(updatedSnap.data().totalRevenue, 20000);
  assert.equal(updatedSnap.data().yearMonthSerial, 2026 * 12 + 10);
});

test("Validation rules reject malformed IDs, negative profits, mismatched counts/serials, ghost fields, immutable createdAt change, and future timestamps", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const col = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("monthlyProfits");

  // 1. Invalid month document ID
  await assertFails(
    col
      .doc("2026-13")
      .set(validMonthlyPayload(aliceDb, ALICE_UID, 2026, 13, { monthKey: "2026-13" }))
  );

  // 2. Mismatched monthKey vs document ID
  await assertFails(
    col.doc("2026-10").set(validMonthlyPayload(aliceDb, ALICE_UID, 2026, 9))
  );

  // 3. Mismatched yearMonthSerial
  await assertFails(
    col.doc("2026-10").set(
      validMonthlyPayload(aliceDb, ALICE_UID, 2026, 10, {
        yearMonthSerial: 99999,
      })
    )
  );

  // 4. Negative netProfit
  await assertFails(
    col
      .doc("2026-10")
      .set(validMonthlyPayload(aliceDb, ALICE_UID, 2026, 10, { netProfit: -50 }))
  );

  // 5. totalCardsCount < soldCardsCount
  await assertFails(
    col.doc("2026-10").set(
      validMonthlyPayload(aliceDb, ALICE_UID, 2026, 10, {
        soldCardsCount: 50,
        totalCardsCount: 10,
      })
    )
  );

  // 6. Undeclared ghost field
  await assertFails(
    col.doc("2026-10").set(
      validMonthlyPayload(aliceDb, ALICE_UID, 2026, 10, {
        extraHack: "bad",
      })
    )
  );

  // 7. Spoofed userId
  await assertFails(
    col
      .doc("2026-10")
      .set(validMonthlyPayload(aliceDb, BOB_UID, 2026, 10))
  );

  // 8. Future client timestamp > request.time
  await assertFails(
    col.doc("2026-10").set(
      validMonthlyPayload(aliceDb, ALICE_UID, 2026, 10, {
        updatedAt: futureTs(aliceDb),
      })
    )
  );

  // 9. Mutating immutable createdAt or yearMonthSerial on update
  await assertSucceeds(
    col.doc("2026-08").set(validMonthlyPayload(aliceDb, ALICE_UID, 2026, 8))
  );
  await assertFails(
    col.doc("2026-08").update({
      createdAt: serverTs(aliceDb),
      updatedAt: serverTs(aliceDb),
    })
  );
  await assertFails(
    col.doc("2026-08").update({
      yearMonthSerial: 2026 * 12 + 9,
      updatedAt: serverTs(aliceDb),
    })
  );
});

test("PackagePricing rules allow valid owner pricing and reject invalid/negative payloads", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const pricingCol = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("packagePricing");

  await assertSucceeds(
    pricingCol.doc("pkg_500_Day_abc123").set({
      userId: ALICE_UID,
      profileName: "500-Day",
      costPrice: 200,
      salePrice: 500,
      updatedAt: serverTs(aliceDb),
    })
  );

  await assertFails(
    pricingCol.doc("pkg_500_Day_abc123").set({
      userId: ALICE_UID,
      profileName: "500-Day",
      costPrice: -10,
      salePrice: 500,
      updatedAt: serverTs(aliceDb),
    })
  );

  await assertFails(
    pricingCol.doc("invalid doc.id").set({
      userId: ALICE_UID,
      profileName: "500-Day",
      costPrice: 100,
      salePrice: 200,
      updatedAt: serverTs(aliceDb),
    })
  );

  await assertFails(
    pricingCol.doc("pkg_500_Day_abc123").set({
      userId: ALICE_UID,
      profileName: "500-Day",
      costPrice: 100,
      salePrice: 200,
      extraField: "disallowed",
      updatedAt: serverTs(aliceDb),
    })
  );
});
