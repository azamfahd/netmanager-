const { initializeTestEnvironment, assertFails, assertSucceeds } = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;

before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: "demo-project",
    firestore: {
      host: "127.0.0.1",
      port: 8085,
      rules: fs.readFileSync("firestore.rules", "utf8"),
    },
  });
});

after(async () => {
  await testEnv.cleanup();
});

beforeEach(async () => {
  await testEnv.clearFirestore();
});

test("Authenticated user can read/write their own routers", async () => {
  const authedApp = testEnv.authenticatedContext("alice");
  await assertSucceeds(authedApp.firestore().collection("users").doc("alice").collection("routers").doc("r1").set({
    id: "r1",
    name: "Router 1",
    ipAddress: "192.168.1.1"
  }));
});

test("Authenticated user can read/write their own system logs", async () => {
  const authedApp = testEnv.authenticatedContext("alice");
  await assertSucceeds(authedApp.firestore().collection("users").doc("alice").collection("systemLogs").doc("l1").set({
    id: "l1",
    actionType: "توليد كروت",
    timestamp: new Date()
  }));
});

test("Unauthenticated user cannot write any data", async () => {
  const unauthedApp = testEnv.unauthenticatedContext();
  await assertFails(unauthedApp.firestore().collection("users").doc("alice").collection("routers").doc("r1").set({
    id: "r1",
    name: "Router 1",
    ipAddress: "192.168.1.1"
  }));
});

test("User cannot write to another user's collection", async () => {
  const authedApp = testEnv.authenticatedContext("bob");
  await assertFails(authedApp.firestore().collection("users").doc("alice").collection("routers").doc("r1").set({
    id: "r1",
    name: "Router 1",
    ipAddress: "192.168.1.1"
  }));
});
