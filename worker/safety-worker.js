#!/usr/bin/env node
//
// Stand-in safety worker -- SCHEMA.md section 10.
//
// This is Dev B's component. It exists here because the Android app's whole safety story is
// undemonstrable without something server-side actually performing the cutoff, and a demo
// that describes the feature instead of showing it is worth much less. Dev B's
// implementation supersedes this; nothing in the app depends on this file.
//
// Two jobs, both straight from the contract:
//
//   10.1  Cutoff sweep. Every APPLIANCE that is ON and has been on longer than its
//         `max_on_duration` is switched off, its usage period closed, and an alert raised.
//
//   10.2  Light schedules. Every LIGHT with `schedule_enabled` is driven to match its
//         window, so a light comes on at its start edge and goes off at its end.
//
// Credentials come from GOOGLE_APPLICATION_CREDENTIALS (a path to a service-account JSON)
// and never from this repository. The key bypasses every security rule.
//
//   $env:GOOGLE_APPLICATION_CREDENTIALS = "C:\path\to\key.json"
//   node safety-worker.js

const { initializeApp, cert } = require("firebase-admin/app");
const { getFirestore, Timestamp, FieldValue } = require("firebase-admin/firestore");

const keyPath = process.env.GOOGLE_APPLICATION_CREDENTIALS;
if (!keyPath) {
  console.error("Set GOOGLE_APPLICATION_CREDENTIALS to your service-account JSON path.");
  process.exit(1);
}

initializeApp({ credential: cert(require(keyPath)) });
const db = getFirestore();

// The contract fixes this at 60 seconds. It is also why the app never shows 00:00 against a
// device that is still ON -- the cutoff lands up to a minute after the countdown expires,
// and the sheet says "Switching off..." across that gap rather than pretending otherwise.
const TICK_MS = 60 * 1000;

/** Section 10.1 -- the cutoff sweep. */
async function sweepCutoffs() {
  const snapshot = await db
    .collection("devices")
    .where("type", "==", "APPLIANCE")
    .where("status", "==", "ON")
    .get();

  const now = Date.now();
  let fired = 0;

  for (const doc of snapshot.docs) {
    const device = doc.data();
    const limit = device.config && device.config.max_on_duration;
    const turnedOnAt = device.turned_on_at;

    // A device ON with no start time is a contract violation upstream. Skipping it is the
    // safe direction: guessing a start would produce a cutoff at an arbitrary moment.
    if (!limit || !turnedOnAt) continue;

    const elapsed = (now - turnedOnAt.toDate().getTime()) / 1000;
    if (elapsed < limit) continue;

    // The open usage period has to be found before the batch: a query cannot run inside one.
    const open = await db
      .collection("usage_events")
      .where("owner_uid", "==", device.owner_uid)
      .where("device_id", "==", doc.id)
      .where("ended_at", "==", null)
      .get();

    const batch = db.batch();

    // Status and turned_on_at are written together, always. Leaving the start time set on a
    // device that is now off is what produces a false cutoff on its next power-on.
    batch.update(doc.ref, {
      status: "OFF",
      turned_on_at: null,
      last_changed_at: FieldValue.serverTimestamp(),
      last_changed_by: "WORKER",
    });

    for (const event of open.docs) {
      const started = event.data().started_at;
      batch.update(event.ref, {
        ended_at: FieldValue.serverTimestamp(),
        duration_seconds: started
          ? Math.max(0, Math.round((now - started.toDate().getTime()) / 1000))
          : null,
      });
    }

    // `message` answers *why* and deliberately does not name the device or say what became
    // of it -- that is the app's half of the sentence. `device_name` is denormalised so the
    // alert list renders without a read per row, and still reads correctly after the device
    // is renamed or deleted.
    batch.set(db.collection("alerts").doc(), {
      owner_uid: device.owner_uid,
      device_id: doc.id,
      device_name: device.name,
      floor_id: device.floor_id,
      type: "MAX_DURATION_EXCEEDED",
      message: "Maximum on time reached",
      created_at: FieldValue.serverTimestamp(),
      acknowledged: false,
    });

    await batch.commit();
    fired++;
    console.log(
      `  cutoff: ${device.name} was on ${Math.round(elapsed)}s of a ${limit}s limit`,
    );
  }

  return fired;
}

/** Section 10.2 -- light schedules, evaluated in the home's timezone. */
async function sweepSchedules() {
  const users = await db.collection("users").get();
  const zoneByUid = new Map();
  users.forEach((u) => zoneByUid.set(u.id, (u.data() || {}).timezone || "UTC"));

  const snapshot = await db.collection("devices").where("type", "==", "LIGHT").get();
  let changed = 0;

  for (const doc of snapshot.docs) {
    const device = doc.data();
    const config = device.config || {};
    if (!config.schedule_enabled || !config.schedule_on || !config.schedule_off) continue;

    const zone = zoneByUid.get(device.owner_uid) || "UTC";
    const nowMinutes = minutesOfDayIn(zone);
    const on = toMinutes(config.schedule_on);
    const off = toMinutes(config.schedule_off);
    if (on === null || off === null || on === off) continue;

    // A window whose end is before its start crosses midnight and is one window, not none.
    const inside = on < off ? nowMinutes >= on && nowMinutes < off : nowMinutes >= on || nowMinutes < off;
    const target = inside ? "ON" : "OFF";

    // Only ON and OFF are driven. A light in ERROR or DISCONNECTED is not something the
    // scheduler should quietly overwrite.
    if (device.status !== "ON" && device.status !== "OFF") continue;
    if (device.status === target) continue;

    const batch = db.batch();
    batch.update(doc.ref, {
      status: target,
      turned_on_at: target === "ON" ? FieldValue.serverTimestamp() : null,
      last_changed_at: FieldValue.serverTimestamp(),
      last_changed_by: "WORKER",
    });

    if (target === "ON") {
      batch.set(db.collection("usage_events").doc(), {
        owner_uid: device.owner_uid,
        device_id: doc.id,
        channel_id: null,
        started_at: FieldValue.serverTimestamp(),
        ended_at: null,
        duration_seconds: null,
      });
    } else {
      const open = await db
        .collection("usage_events")
        .where("owner_uid", "==", device.owner_uid)
        .where("device_id", "==", doc.id)
        .where("ended_at", "==", null)
        .get();
      const now = Date.now();
      for (const event of open.docs) {
        const started = event.data().started_at;
        batch.update(event.ref, {
          ended_at: FieldValue.serverTimestamp(),
          duration_seconds: started
            ? Math.max(0, Math.round((now - started.toDate().getTime()) / 1000))
            : null,
        });
      }
    }

    await batch.commit();
    changed++;
    console.log(`  schedule: ${device.name} -> ${target}`);
  }

  return changed;
}

function toMinutes(value) {
  const m = /^(\d{2}):(\d{2})$/.exec(value || "");
  return m ? Number(m[1]) * 60 + Number(m[2]) : null;
}

function minutesOfDayIn(zone) {
  const parts = new Intl.DateTimeFormat("en-GB", {
    timeZone: zone,
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).formatToParts(new Date());
  const hour = Number(parts.find((p) => p.type === "hour").value);
  const minute = Number(parts.find((p) => p.type === "minute").value);
  return hour * 60 + minute;
}

async function tick() {
  const stamp = new Date().toISOString().slice(11, 19);
  try {
    const cutoffs = await sweepCutoffs();
    const schedules = await sweepSchedules();
    if (cutoffs === 0 && schedules === 0) console.log(`${stamp}  nothing to do`);
  } catch (error) {
    // A failed sweep must not kill the loop. The next tick is 60 seconds away and the
    // condition it is checking has not gone anywhere.
    console.error(`${stamp}  sweep failed: ${error.message}`);
  }
}

const once = process.argv.includes("--once");
console.log(once ? "running one sweep" : `sweeping every ${TICK_MS / 1000}s -- Ctrl+C to stop`);

tick().then(() => {
  if (once) process.exit(0);
  setInterval(tick, TICK_MS);
});
