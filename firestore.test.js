const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const TRAINER_UID = "trainer_cobie_01";
const TRAINER2_UID = "trainer_jun_02";
const MEMBER_UID = "member_alex_01";
const OTHER_UID = "member_stranger_99";
const ADMIN_UID = "admin_boss_01";
const INVITE_CODE = "BG-" + TRAINER_UID.slice(0, 6).toUpperCase();

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastDate(msOffset = 2000) {
  return new Date(Date.now() - msOffset);
}

async function seedUsersAndInvite() {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    const t = pastDate(20000);
    await db.collection("users").doc(ADMIN_UID).set({
      userId: ADMIN_UID,
      name: "Admin Boss",
      email: "admin@bodygrind.fit",
      accountType: "ADMIN",
      role: "admin",
      status: "active",
      createdAt: t,
      updatedAt: t,
    });
    await db.collection("users").doc(TRAINER_UID).set({
      userId: TRAINER_UID,
      name: "Coach Cobie",
      email: "cobie@bodygrind.fit",
      accountType: "TRAINER",
      status: "active",
      trainerInviteCode: INVITE_CODE,
      trainerTitle: "Strength Coach",
      createdAt: t,
      updatedAt: t,
    });
    await db.collection("users").doc(TRAINER2_UID).set({
      userId: TRAINER2_UID,
      name: "Coach Jun",
      email: "jun@bodygrind.fit",
      accountType: "TRAINER",
      status: "active",
      createdAt: t,
      updatedAt: t,
    });
    await db.collection("users").doc(MEMBER_UID).set({
      userId: MEMBER_UID,
      name: "Alex",
      email: "alex@bodygrind.fit",
      accountType: "MEMBER",
      status: "active",
      fitnessGoal: "Build Muscle",
      fitnessLevel: "Intermediate",
      createdAt: t,
      updatedAt: t,
    });
    await db.collection("users").doc(OTHER_UID).set({
      userId: OTHER_UID,
      name: "Stranger",
      email: "stranger@bodygrind.fit",
      accountType: "MEMBER",
      status: "active",
      createdAt: t,
      updatedAt: t,
    });
    await db.collection("trainerInvites").doc(INVITE_CODE).set({
      trainerId: TRAINER_UID,
      trainerName: "Coach Cobie",
      trainerTitle: "Strength Coach",
      specialization: "Hypertrophy",
      active: true,
      createdAt: t,
      updatedAt: t,
    });
  });
}

async function seedActiveLink({
  shareWorkouts = true,
  shareStreakXp = true,
  shareBodyStats = false,
} = {}) {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    const t = pastDate(10000);
    await db.collection("links").doc(MEMBER_UID).set({
      memberId: MEMBER_UID,
      trainerId: TRAINER_UID,
      memberName: "Alex",
      trainerName: "Coach Cobie",
      trainerTitle: "Strength Coach",
      inviteCode: INVITE_CODE,
      status: "ACTIVE",
      memberStatus: "ACTIVE",
      requestedBy: "MEMBER",
      shareWorkouts,
      shareStreakXp,
      shareBodyStats,
      agreedShareWorkouts: shareWorkouts,
      agreedShareStreakXp: shareStreakXp,
      agreedShareBodyStats: shareBodyStats,
      fitnessGoal: "Build Muscle",
      fitnessLevel: "Intermediate",
      acceptedAt: t,
      lastActiveAt: t,
      createdAt: t,
      updatedAt: t,
    });
  });
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

test("Unauthenticated users cannot read or write any coaching collections", async () => {
  await seedUsersAndInvite();
  const unauth = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauth.collection("users").doc(MEMBER_UID).get());
  await assertFails(unauth.collection("trainerInvites").doc(INVITE_CODE).get());
  await assertFails(unauth.collection("links").doc(MEMBER_UID).get());
  await assertFails(unauth.collection("chats").doc(`${TRAINER_UID}__${MEMBER_UID}`).get());
});

test("Users can create and update their own doc, cannot escalate accountType or list users", async () => {
  const db = testEnv.authenticatedContext(MEMBER_UID).firestore();
  const now = pastDate(1500);
  await assertSucceeds(
    db.collection("users").doc(MEMBER_UID).set({
      userId: MEMBER_UID,
      name: "Alex",
      email: "alex@bodygrind.fit",
      accountType: "MEMBER",
      status: "active",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Reject future timestamp
  await assertFails(
    db.collection("users").doc(OTHER_UID).set({
      userId: OTHER_UID,
      accountType: "MEMBER",
      status: "active",
      createdAt: new Date(Date.now() + 60000),
      updatedAt: new Date(Date.now() + 60000),
    })
  );

  // Reject role change on update
  await assertFails(
    db.collection("users").doc(MEMBER_UID).update({
      accountType: "TRAINER",
      updatedAt: pastDate(500),
    })
  );

  // Reject listing users
  await assertFails(db.collection("users").get());
});

test("Legacy pre-existing user docs without userId/createdAt or with extra/null fields can update lastActiveAt and backfill safely", async () => {
  const legacyUid = "4xrhtzQdNzfuYRXVbFvCYfOq8tf1";
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const adminDb = ctx.firestore();
    await adminDb.collection("users").doc(legacyUid).set({
      name: "ML Chilama",
      email: "mlchilama21@gmail.com",
      accountType: "MEMBER",
      status: "active",
      trainerTitle: null,
      membershipStatus: "active",
      legacyCreatedAt: "2025-01-01T00:00:00Z",
    });
  });

  const db = testEnv.authenticatedContext(legacyUid).firestore();
  const now = pastDate(1000);

  // Check-in lastActiveAt-only update succeeds
  await assertSucceeds(
    db.collection("users").doc(legacyUid).update({
      lastActiveAt: pastDate(1500),
    })
  );

  // Coaching backfill (userId, createdAt, lastActiveAt, updatedAt) succeeds
  await assertSucceeds(
    db.collection("users").doc(legacyUid).update({
      userId: legacyUid,
      createdAt: now,
      lastActiveAt: now,
      updatedAt: now,
    })
  );

  // Subsequent attempt to escalate accountType or tamper with userId/createdAt fails
  await assertFails(
    db.collection("users").doc(legacyUid).update({
      accountType: "TRAINER",
      updatedAt: pastDate(500),
    })
  );
  await assertFails(
    db.collection("users").doc(legacyUid).update({
      createdAt: pastDate(500),
      updatedAt: pastDate(500),
    })
  );
  await assertFails(
    db.collection("users").doc(legacyUid).update({
      userId: "different_uid",
      updatedAt: pastDate(500),
    })
  );
});

test("Trainer can create invite code; signed-in member can get invite by code but cannot list all invites", async () => {
  await seedUsersAndInvite();
  const trainerDb = testEnv.authenticatedContext(TRAINER_UID).firestore();
  const memberDb = testEnv.authenticatedContext(MEMBER_UID).firestore();
  const now = pastDate(1000);

  await assertSucceeds(
    trainerDb.collection("trainerInvites").doc("BG-NEW123").set({
      trainerId: TRAINER_UID,
      trainerName: "Coach Cobie",
      trainerTitle: "Strength Coach",
      active: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(memberDb.collection("trainerInvites").doc(INVITE_CODE).get());
  await assertFails(memberDb.collection("trainerInvites").get());

  // Member cannot create a trainer invite
  await assertFails(
    memberDb.collection("trainerInvites").doc("BG-FAKE").set({
      trainerId: MEMBER_UID,
      trainerName: "Fake",
      active: true,
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Member links to trainer via invite code with consent toggles; trainer queries own links", async () => {
  await seedUsersAndInvite();
  const memberDb = testEnv.authenticatedContext(MEMBER_UID).firestore();
  const trainerDb = testEnv.authenticatedContext(TRAINER_UID).firestore();
  const otherDb = testEnv.authenticatedContext(OTHER_UID).firestore();
  const now = pastDate(1000);

  await assertSucceeds(
    memberDb.collection("links").doc(MEMBER_UID).set({
      memberId: MEMBER_UID,
      trainerId: TRAINER_UID,
      memberName: "Alex",
      trainerName: "Coach Cobie",
      trainerTitle: "Strength Coach",
      inviteCode: INVITE_CODE,
      status: "ACTIVE",
      memberStatus: "ACTIVE",
      requestedBy: "MEMBER",
      shareWorkouts: true,
      shareStreakXp: true,
      shareBodyStats: false,
      agreedShareWorkouts: true,
      agreedShareStreakXp: true,
      agreedShareBodyStats: false,
      acceptedAt: now,
      lastActiveAt: now,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Trainer lists links filtered by trainerId
  await assertSucceeds(
    trainerDb.collection("links").where("trainerId", "==", TRAINER_UID).get()
  );
  // Unfiltered list fails
  await assertFails(trainerDb.collection("links").get());
  // Stranger cannot read link
  await assertFails(otherDb.collection("links").doc(MEMBER_UID).get());
});

test("Trainer-initiated link request requires PENDING_CONFIRMATION with all sharing false until member accepts", async () => {
  await seedUsersAndInvite();
  const trainerDb = testEnv.authenticatedContext(TRAINER_UID).firestore();
  const memberDb = testEnv.authenticatedContext(MEMBER_UID).firestore();
  const now = pastDate(1000);

  // Trainer cannot force ACTIVE link or turn sharing ON
  await assertFails(
    trainerDb.collection("links").doc(MEMBER_UID).set({
      memberId: MEMBER_UID,
      trainerId: TRAINER_UID,
      memberName: "Alex",
      trainerName: "Coach Cobie",
      status: "ACTIVE",
      memberStatus: "ACTIVE",
      requestedBy: "TRAINER",
      shareWorkouts: true,
      shareStreakXp: false,
      shareBodyStats: false,
      agreedShareWorkouts: true,
      agreedShareStreakXp: false,
      agreedShareBodyStats: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Valid PENDING_CONFIRMATION request succeeds
  await assertSucceeds(
    trainerDb.collection("links").doc(MEMBER_UID).set({
      memberId: MEMBER_UID,
      trainerId: TRAINER_UID,
      memberName: "Alex",
      trainerName: "Coach Cobie",
      status: "PENDING_CONFIRMATION",
      memberStatus: "ACTIVE",
      requestedBy: "TRAINER",
      shareWorkouts: false,
      shareStreakXp: false,
      shareBodyStats: false,
      agreedShareWorkouts: false,
      agreedShareStreakXp: false,
      agreedShareBodyStats: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Member accepts and turns on chosen categories
  await assertSucceeds(
    memberDb.collection("links").doc(MEMBER_UID).update({
      status: "ACTIVE",
      shareWorkouts: true,
      shareStreakXp: false,
      shareBodyStats: true,
      agreedShareWorkouts: true,
      agreedShareStreakXp: false,
      agreedShareBodyStats: true,
      acceptedAt: pastDate(500),
      updatedAt: pastDate(500),
    })
  );
});

test("Privacy toggles strictly enforce what linked trainer can read, and unlinking immediately revokes access", async () => {
  await seedUsersAndInvite();
  await seedActiveLink({ shareWorkouts: true, shareStreakXp: false, shareBodyStats: false });

  const memberDb = testEnv.authenticatedContext(MEMBER_UID).firestore();
  const trainerDb = testEnv.authenticatedContext(TRAINER_UID).firestore();
  const trainer2Db = testEnv.authenticatedContext(TRAINER2_UID).firestore();
  const now = pastDate(1000);

  // Member writes all 3 categories of shared data
  await assertSucceeds(
    memberDb.collection("users").doc(MEMBER_UID).collection("sharedSummary").doc("workouts").set({
      memberId: MEMBER_UID,
      totalWorkouts: 12,
      completedThisWeek: 4,
      totalCaloriesBurned: 3400,
      totalMinutes: 420,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    memberDb.collection("users").doc(MEMBER_UID).collection("sharedWorkouts").doc("sess_1").set({
      memberId: MEMBER_UID,
      planName: "Upper Push",
      category: "Strength",
      completedAtMs: Date.now() - 5000,
      durationMinutes: 40,
      caloriesBurned: 310,
      completed: true,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    memberDb.collection("users").doc(MEMBER_UID).collection("sharedSummary").doc("streakXp").set({
      memberId: MEMBER_UID,
      streakDays: 6,
      level: 4,
      xpPoints: 950,
      fitnessGoal: "Build Muscle",
      fitnessLevel: "Intermediate",
      updatedAt: now,
    })
  );
  await assertSucceeds(
    memberDb.collection("users").doc(MEMBER_UID).collection("sharedSummary").doc("bodyStats").set({
      memberId: MEMBER_UID,
      heightCm: 176.0,
      weightKg: 74.5,
      bmi: 24.0,
      bmiCategory: "Normal",
      updatedAt: now,
    })
  );
  await assertSucceeds(
    memberDb.collection("users").doc(MEMBER_UID).collection("sharedMeasurements").doc("meas_1").set({
      memberId: MEMBER_UID,
      timestampMs: Date.now() - 5000,
      weightKg: 74.5,
      heightCm: 176.0,
      bmi: 24.0,
      bmiCategory: "Normal",
      updatedAt: now,
    })
  );

  // Linked trainer can read workouts, CANNOT read streakXp or bodyStats
  await assertSucceeds(
    trainerDb.collection("users").doc(MEMBER_UID).collection("sharedSummary").doc("workouts").get()
  );
  await assertSucceeds(
    trainerDb.collection("users").doc(MEMBER_UID).collection("sharedWorkouts").get()
  );
  await assertFails(
    trainerDb.collection("users").doc(MEMBER_UID).collection("sharedSummary").doc("streakXp").get()
  );
  await assertFails(
    trainerDb.collection("users").doc(MEMBER_UID).collection("sharedSummary").doc("bodyStats").get()
  );
  await assertFails(
    trainerDb.collection("users").doc(MEMBER_UID).collection("sharedMeasurements").get()
  );

  // Another trainer cannot read anything
  await assertFails(
    trainer2Db.collection("users").doc(MEMBER_UID).collection("sharedSummary").doc("workouts").get()
  );

  // Member enables bodyStats -> trainer immediately gains read access
  await assertSucceeds(
    memberDb.collection("links").doc(MEMBER_UID).update({
      shareBodyStats: true,
      updatedAt: pastDate(500),
    })
  );
  await assertSucceeds(
    trainerDb.collection("users").doc(MEMBER_UID).collection("sharedSummary").doc("bodyStats").get()
  );
  await assertSucceeds(
    trainerDb.collection("users").doc(MEMBER_UID).collection("sharedMeasurements").get()
  );

  // Unlink immediately revokes all reads including workouts & bodyStats
  await assertSucceeds(memberDb.collection("links").doc(MEMBER_UID).delete());
  await assertFails(
    trainerDb.collection("users").doc(MEMBER_UID).collection("sharedSummary").doc("workouts").get()
  );
  await assertFails(
    trainerDb.collection("users").doc(MEMBER_UID).collection("sharedSummary").doc("bodyStats").get()
  );
});

test("Bidirectional messaging works between actively linked trainer and member only", async () => {
  await seedUsersAndInvite();
  await seedActiveLink();

  const pairId = `${TRAINER_UID}__${MEMBER_UID}`;
  const trainerDb = testEnv.authenticatedContext(TRAINER_UID).firestore();
  const memberDb = testEnv.authenticatedContext(MEMBER_UID).firestore();
  const otherDb = testEnv.authenticatedContext(OTHER_UID).firestore();
  const now = pastDate(1000);

  await assertSucceeds(
    trainerDb.collection("chats").doc(pairId).set({
      trainerId: TRAINER_UID,
      memberId: MEMBER_UID,
      trainerName: "Coach Cobie",
      memberName: "Alex",
      lastMessage: "Hey Alex, ready for leg day?",
      lastSenderId: TRAINER_UID,
      lastMessageAt: now,
      trainerUnreadCount: 0,
      memberUnreadCount: 1,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    trainerDb.collection("chats").doc(pairId).collection("messages").doc("msg_1").set({
      trainerId: TRAINER_UID,
      memberId: MEMBER_UID,
      senderId: TRAINER_UID,
      senderRole: "TRAINER",
      senderName: "Coach Cobie",
      text: "Hey Alex, ready for leg day?",
      createdAt: now,
    })
  );

  await assertSucceeds(
    memberDb.collection("chats").doc(pairId).collection("messages").orderBy("createdAt", "asc").get()
  );
  await assertSucceeds(
    memberDb.collection("chats").doc(pairId).collection("messages").doc("msg_2").set({
      trainerId: TRAINER_UID,
      memberId: MEMBER_UID,
      senderId: MEMBER_UID,
      senderRole: "MEMBER",
      senderName: "Alex",
      text: "Yes coach, starting now!",
      createdAt: pastDate(500),
    })
  );
  await assertSucceeds(
    memberDb.collection("chats").doc(pairId).update({
      lastMessage: "Yes coach, starting now!",
      lastSenderId: MEMBER_UID,
      lastMessageAt: pastDate(500),
      trainerUnreadCount: 1,
      memberUnreadCount: 0,
      updatedAt: pastDate(500),
    })
  );

  // Unrelated user cannot read or write chat
  await assertFails(otherDb.collection("chats").doc(pairId).get());
  await assertFails(otherDb.collection("chats").doc(pairId).collection("messages").get());
});

test("Trainer assigns workout plan to linked member; member marks completed only", async () => {
  await seedUsersAndInvite();
  await seedActiveLink();

  const trainerDb = testEnv.authenticatedContext(TRAINER_UID).firestore();
  const memberDb = testEnv.authenticatedContext(MEMBER_UID).firestore();
  const now = pastDate(1000);

  await assertSucceeds(
    trainerDb.collection("users").doc(MEMBER_UID).collection("assignedPlans").doc("plan_a1").set({
      trainerId: TRAINER_UID,
      trainerName: "Coach Cobie",
      memberId: MEMBER_UID,
      planTitle: "Hypertrophy Push Day",
      planDescription: "Focus on controlled eccentric reps",
      category: "Strength",
      difficulty: "Intermediate",
      estimatedDurationMinutes: 35,
      note: "Rest 60s between sets",
      startDateEpochMs: Date.now(),
      exercises: [
        {
          exerciseId: "pushups",
          exerciseName: "Push-Ups",
          sets: 3,
          repsOrDuration: 15,
          isTimeBased: false,
          restSeconds: 45,
        },
      ],
      completed: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    memberDb.collection("users").doc(MEMBER_UID).collection("assignedPlans").get()
  );

  // Member cannot alter planTitle
  await assertFails(
    memberDb.collection("users").doc(MEMBER_UID).collection("assignedPlans").doc("plan_a1").update({
      planTitle: "Tampered Title",
      updatedAt: pastDate(500),
    })
  );

  // Member marks completed
  await assertSucceeds(
    memberDb.collection("users").doc(MEMBER_UID).collection("assignedPlans").doc("plan_a1").update({
      completed: true,
      completedAt: pastDate(500),
      updatedAt: pastDate(500),
    })
  );

  // Trainer sees completion
  const snap = await assertSucceeds(
    trainerDb.collection("users").doc(MEMBER_UID).collection("assignedPlans").doc("plan_a1").get()
  );
  if (snap.data().completed !== true) {
    throw new Error("Expected assigned plan to be marked completed");
  }
});

test("Trainer Portal: Unauthenticated and Members are blocked from /trainers collection (including own UID)", async () => {
  await seedUsersAndInvite();
  const unauth = testEnv.unauthenticatedContext().firestore();
  const memberDb = testEnv.authenticatedContext(MEMBER_UID).firestore();
  const now = pastDate(1000);

  await assertFails(unauth.collection("trainers").doc(TRAINER_UID).get());
  await assertFails(unauth.collection("trainers").get());
  await assertFails(memberDb.collection("trainers").doc(TRAINER_UID).get());
  await assertFails(memberDb.collection("trainers").doc(MEMBER_UID).get());
  await assertFails(memberDb.collection("trainers").get());
  await assertFails(
    memberDb.collection("trainers").doc(MEMBER_UID).set({
      trainerId: MEMBER_UID,
      name: "Alex Fake",
      email: "alex@bodygrind.fit",
      phone: "09171234567",
      specialization: "None",
      certificateStatus: "pending",
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Trainer Portal: Trainer can create/edit own profile, digits-only phone enforced, cannot self-approve or tamper others", async () => {
  await seedUsersAndInvite();
  const trainerDb = testEnv.authenticatedContext(TRAINER_UID).firestore();
  const now = pastDate(2000);

  // Trainer cannot create with certificateStatus == 'approved'
  await assertFails(
    trainerDb.collection("trainers").doc(TRAINER_UID).set({
      trainerId: TRAINER_UID,
      name: "Coach Cobie",
      email: "cobie@bodygrind.fit",
      phone: "09171234567",
      specialization: "Hypertrophy & Strength",
      certificateStatus: "approved",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Strict digits-only phone rejects '+' prefix
  await assertFails(
    trainerDb.collection("trainers").doc(TRAINER_UID).set({
      trainerId: TRAINER_UID,
      name: "Coach Cobie",
      email: "cobie@bodygrind.fit",
      phone: "+639171234567",
      specialization: "Hypertrophy & Strength",
      certificateStatus: "pending",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Unexpected/ghost field is rejected
  await assertFails(
    trainerDb.collection("trainers").doc(TRAINER_UID).set({
      trainerId: TRAINER_UID,
      name: "Coach Cobie",
      email: "cobie@bodygrind.fit",
      phone: "09171234567",
      specialization: "Hypertrophy & Strength",
      certificateStatus: "pending",
      isAdmin: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Trainer cannot create for another trainer UID
  await assertFails(
    trainerDb.collection("trainers").doc(TRAINER2_UID).set({
      trainerId: TRAINER2_UID,
      name: "Coach Jun",
      email: "jun@bodygrind.fit",
      phone: "09171234567",
      specialization: "Strength",
      certificateStatus: "pending",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Valid trainer creation with status pending and createdAt succeeds
  await assertSucceeds(
    trainerDb.collection("trainers").doc(TRAINER_UID).set({
      trainerId: TRAINER_UID,
      name: "Coach Cobie",
      email: "cobie@bodygrind.fit",
      phone: "09171234567",
      specialization: "Hypertrophy & Strength",
      certificateStatus: "pending",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Trainer can read own profile and partially update profile fields
  await assertSucceeds(trainerDb.collection("trainers").doc(TRAINER_UID).get());
  await assertSucceeds(
    trainerDb.collection("trainers").doc(TRAINER_UID).update({
      name: "Coach Cobie Cruz",
      phone: "09179998888",
      specialization: "Strength & Powerlifting",
      updatedAt: pastDate(1000),
    })
  );

  // Trainer cannot mutate immutable createdAt
  await assertFails(
    trainerDb.collection("trainers").doc(TRAINER_UID).update({
      createdAt: pastDate(500),
      updatedAt: pastDate(500),
    })
  );

  // Trainer cannot read another trainer's profile or list trainers
  await assertFails(trainerDb.collection("trainers").doc(TRAINER2_UID).get());
  await assertFails(trainerDb.collection("trainers").get());

  // Trainer cannot self-approve certificate on update
  await assertFails(
    trainerDb.collection("trainers").doc(TRAINER_UID).update({
      certificateStatus: "approved",
      updatedAt: pastDate(500),
    })
  );

  // Trainer cannot delete own profile
  await assertFails(trainerDb.collection("trainers").doc(TRAINER_UID).delete());
});

test("Trainer Portal: Admin can add existing users, edit/delete profiles, approve/reject, and trainer cert replacement resets to pending", async () => {
  await seedUsersAndInvite();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();
  const trainerDb = testEnv.authenticatedContext(TRAINER_UID).firestore();
  const created = pastDate(5000);

  // Admin CANNOT add a trainer profile for a non-existent /users/{uid}
  await assertFails(
    adminDb.collection("trainers").doc("ghost_user_999").set({
      trainerId: "ghost_user_999",
      name: "Ghost Coach",
      email: "ghost@bodygrind.fit",
      phone: "09181112222",
      specialization: "Mobility",
      certificateStatus: "pending",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Trainer creates own profile with initial certificate
  await assertSucceeds(
    trainerDb.collection("trainers").doc(TRAINER_UID).set({
      trainerId: TRAINER_UID,
      name: "Coach Cobie",
      email: "cobie@bodygrind.fit",
      phone: "09171234567",
      specialization: "Hypertrophy",
      certificateUrl: "https://storage.googleapis.com/test.pdf",
      certificateStoragePath: `trainers/${TRAINER_UID}/certificates/cert.pdf`,
      certificateFileName: "cert.pdf",
      certificateMimeType: "application/pdf",
      certificateStatus: "pending",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Admin lists all trainers and reads Cobie's profile
  await assertSucceeds(adminDb.collection("trainers").get());
  await assertSucceeds(adminDb.collection("trainers").doc(TRAINER_UID).get());

  // Admin edits Cobie's profile fields while preserving createdAt
  await assertSucceeds(
    adminDb.collection("trainers").doc(TRAINER_UID).update({
      name: "Coach Cobie Senior",
      specialization: "Hypertrophy & Rehab",
      updatedAt: pastDate(3000),
    })
  );

  // Admin approves certificate
  await assertSucceeds(
    adminDb.collection("trainers").doc(TRAINER_UID).update({
      certificateStatus: "approved",
      certificateReviewedBy: ADMIN_UID,
      certificateReviewedAt: pastDate(2000),
      updatedAt: pastDate(2000),
    })
  );

  // Trainer replacing an approved certificate WITHOUT resetting status to pending fails
  await assertFails(
    trainerDb.collection("trainers").doc(TRAINER_UID).update({
      certificateUrl: "https://storage.googleapis.com/new_cert.png",
      certificateStoragePath: `trainers/${TRAINER_UID}/certificates/new_cert.png`,
      certificateFileName: "new_cert.png",
      certificateMimeType: "image/png",
      updatedAt: pastDate(1000),
    })
  );

  // Trainer replacing an approved certificate WITH status pending and review fields removed succeeds
  await assertSucceeds(
    trainerDb.collection("trainers").doc(TRAINER_UID).set({
      trainerId: TRAINER_UID,
      name: "Coach Cobie Senior",
      email: "cobie@bodygrind.fit",
      phone: "09171234567",
      specialization: "Hypertrophy & Rehab",
      certificateUrl: "https://storage.googleapis.com/new_cert.png",
      certificateStoragePath: `trainers/${TRAINER_UID}/certificates/new_cert.png`,
      certificateFileName: "new_cert.png",
      certificateMimeType: "image/png",
      certificateStatus: "pending",
      createdAt: created,
      updatedAt: pastDate(800),
    })
  );

  // Admin creates trainer profile for existing user Jun
  await assertSucceeds(
    adminDb.collection("trainers").doc(TRAINER2_UID).set({
      trainerId: TRAINER2_UID,
      name: "Coach Jun",
      email: "jun@bodygrind.fit",
      phone: "09189876543",
      specialization: "Conditioning & HIIT",
      certificateStatus: "pending",
      createdAt: pastDate(700),
      updatedAt: pastDate(700),
    })
  );

  // Admin rejects certificate on Jun's profile
  await assertSucceeds(
    adminDb.collection("trainers").doc(TRAINER2_UID).update({
      certificateStatus: "rejected",
      certificateReviewedBy: ADMIN_UID,
      certificateReviewedAt: pastDate(400),
      updatedAt: pastDate(400),
    })
  );

  // Admin can delete trainer profile
  await assertSucceeds(adminDb.collection("trainers").doc(TRAINER2_UID).delete());
});

test("Scheduling (v10): Trainer & Admin edit availability in Asia/Manila; Member reads only and cannot write", async () => {
  await seedUsersAndInvite();
  const trainerDb = testEnv.authenticatedContext(TRAINER_UID).firestore();
  const trainer2Db = testEnv.authenticatedContext(TRAINER2_UID).firestore();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();
  const memberDb = testEnv.authenticatedContext(MEMBER_UID).firestore();
  const now = pastDate(1000);

  const validPayload = {
    trainerId: TRAINER_UID,
    trainerName: "Coach Cobie",
    timezone: "Asia/Manila",
    weeklySlots: [
      { dayOfWeek: 1, enabled: true, startTime: "08:00", endTime: "17:00" },
    ],
    dateOverrides: [
      { dateKey: "2026-03-05", isAvailable: false, startTime: "09:00", endTime: "15:00", note: "Rest" },
    ],
    updatedByUid: TRAINER_UID,
    updatedByRole: "trainer",
    updatedAt: now,
  };

  // Member cannot write trainer availability
  await assertFails(
    memberDb.collection("trainerAvailability").doc(TRAINER_UID).set({
      ...validPayload,
      updatedByUid: MEMBER_UID,
      updatedByRole: "member",
    })
  );

  // Another trainer cannot edit Cobie's availability
  await assertFails(
    trainer2Db.collection("trainerAvailability").doc(TRAINER_UID).set({
      ...validPayload,
      updatedByUid: TRAINER2_UID,
    })
  );

  // Non-Manila timezone rejected
  await assertFails(
    trainerDb.collection("trainerAvailability").doc(TRAINER_UID).set({
      ...validPayload,
      timezone: "America/Los_Angeles",
    })
  );

  // Trainer creates own availability
  await assertSucceeds(
    trainerDb.collection("trainerAvailability").doc(TRAINER_UID).set(validPayload)
  );

  // Member can read trainer availability
  await assertSucceeds(
    memberDb.collection("trainerAvailability").doc(TRAINER_UID).get()
  );

  // Admin can update any trainer's availability
  await assertSucceeds(
    adminDb.collection("trainerAvailability").doc(TRAINER_UID).set({
      ...validPayload,
      updatedByUid: ADMIN_UID,
      updatedByRole: "admin",
      updatedAt: pastDate(500),
    })
  );
});

test("Cancellation (v10): Only trainer/admin can cancel with reason; cancelled sessions/plans can never be marked completed", async () => {
  await seedUsersAndInvite();
  await seedActiveLink();
  const trainerDb = testEnv.authenticatedContext(TRAINER_UID).firestore();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();
  const memberDb = testEnv.authenticatedContext(MEMBER_UID).firestore();
  const now = pastDate(2000);

  // Trainer schedules a session
  await assertSucceeds(
    trainerDb.collection("scheduledSessions").doc("sess_001").set({
      trainerId: TRAINER_UID,
      trainerName: "Coach Cobie",
      memberId: MEMBER_UID,
      memberName: "Alex",
      assignedPlanId: null,
      title: "Lower Body Coaching",
      notes: "Bring knee sleeves",
      scheduledDateUtcMs: Date.now() + 86400000,
      scheduledDateKey: "2026-03-05",
      startTime: "09:00",
      endTime: "10:00",
      status: "scheduled",
      excludeFromBilling: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Member can read own scheduled session, CANNOT cancel or delete it
  await assertSucceeds(memberDb.collection("scheduledSessions").doc("sess_001").get());
  await assertFails(
    memberDb.collection("scheduledSessions").doc("sess_001").update({
      status: "cancelled",
      excludeFromBilling: true,
      cancelledReason: "I want to cancel",
      cancelledByUid: MEMBER_UID,
      cancelledByName: "Alex",
      cancelledByRole: "member",
      cancelledAt: pastDate(1000),
      updatedAt: pastDate(1000),
    })
  );
  await assertFails(memberDb.collection("scheduledSessions").doc("sess_001").delete());

  // Trainer cannot cancel without a valid reason or without excludeFromBilling == true
  await assertFails(
    trainerDb.collection("scheduledSessions").doc("sess_001").update({
      status: "cancelled",
      excludeFromBilling: true,
      cancelledReason: "",
      cancelledByUid: TRAINER_UID,
      cancelledByName: "Coach Cobie",
      cancelledByRole: "trainer",
      cancelledAt: pastDate(1000),
      updatedAt: pastDate(1000),
    })
  );
  await assertFails(
    trainerDb.collection("scheduledSessions").doc("sess_001").update({
      status: "cancelled",
      excludeFromBilling: false,
      cancelledReason: "Coach out sick",
      cancelledByUid: TRAINER_UID,
      cancelledByName: "Coach Cobie",
      cancelledByRole: "trainer",
      cancelledAt: pastDate(1000),
      updatedAt: pastDate(1000),
    })
  );

  // Trainer cancels with required reason + excludeFromBilling = true
  await assertSucceeds(
    trainerDb.collection("scheduledSessions").doc("sess_001").update({
      status: "cancelled",
      excludeFromBilling: true,
      cancelledReason: "Coach out sick",
      cancelledByUid: TRAINER_UID,
      cancelledByName: "Coach Cobie",
      cancelledByRole: "trainer",
      cancelledAt: pastDate(900),
      updatedAt: pastDate(900),
    })
  );

  // Cancelled session can NEVER be transitioned to completed (even by admin)
  await assertFails(
    adminDb.collection("scheduledSessions").doc("sess_001").update({
      status: "completed",
      updatedAt: pastDate(400),
    })
  );

  // Cancellation notification & mail queue: trainer can create, member blocked
  await assertSucceeds(
    trainerDb.collection("cancellationNotifications").doc("notif_001").set({
      sessionId: "sess_001",
      sessionTitle: "Lower Body Coaching",
      trainerId: TRAINER_UID,
      trainerName: "Coach Cobie",
      memberId: MEMBER_UID,
      memberName: "Alex",
      scheduledDateKey: "2026-03-05",
      reason: "Coach out sick",
      cancelledByUid: TRAINER_UID,
      cancelledByName: "Coach Cobie",
      cancelledByRole: "trainer",
      cancelledAt: pastDate(800),
      notifyAdmins: true,
      readByUids: [],
    })
  );
  await assertSucceeds(adminDb.collection("cancellationNotifications").get());
  await assertFails(memberDb.collection("cancellationNotifications").doc("notif_001").get());

  await assertSucceeds(
    trainerDb.collection("mail").doc("mail_001").set({
      type: "session_cancellation",
      cancelledByUid: TRAINER_UID,
      trainerId: TRAINER_UID,
      to: ["cobie@bodygrind.fit"],
      message: {
        subject: "[BodyGrind] Session Cancelled",
        text: "Lower Body Coaching was cancelled.",
      },
      createdAt: pastDate(700),
    })
  );
  await assertFails(
    memberDb.collection("mail").doc("mail_bad").set({
      type: "session_cancellation",
      cancelledByUid: MEMBER_UID,
      to: ["admin@bodygrind.fit"],
      message: { subject: "Spoof", text: "Spoof" },
    })
  );

  // Assigned plan cancelled by admin cannot be completed by member
  await assertSucceeds(
    trainerDb.collection("users").doc(MEMBER_UID).collection("assignedPlans").doc("plan_c1").set({
      trainerId: TRAINER_UID,
      trainerName: "Coach Cobie",
      memberId: MEMBER_UID,
      planTitle: "Cardio Circuit",
      category: "Cardio",
      difficulty: "Beginner",
      estimatedDurationMinutes: 25,
      startDateEpochMs: Date.now(),
      exercises: [],
      completed: false,
      status: "scheduled",
      excludeFromBilling: false,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    adminDb.collection("users").doc(MEMBER_UID).collection("assignedPlans").doc("plan_c1").update({
      status: "cancelled",
      completed: false,
      excludeFromBilling: true,
      cancelledReason: "Facility maintenance",
      cancelledByUid: ADMIN_UID,
      cancelledByName: "Admin Boss",
      cancelledByRole: "admin",
      cancelledAt: pastDate(600),
      updatedAt: pastDate(600),
    })
  );
  await assertFails(
    memberDb.collection("users").doc(MEMBER_UID).collection("assignedPlans").doc("plan_c1").update({
      completed: true,
      completedAt: pastDate(300),
      updatedAt: pastDate(300),
    })
  );
});



