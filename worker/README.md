# Stand-in safety worker

**This is Dev B's component.** It lives here because the app's safety story cannot be
demonstrated without something server-side actually performing the cutoff, and a demo that
*describes* the feature is worth much less than one that shows it happening. Dev B's
implementation supersedes this file; nothing in the Android app depends on it.

It implements SCHEMA.md section 10:

| | |
|---|---|
| **10.1 Cutoff sweep** | Every `APPLIANCE` that is `ON` past its `max_on_duration` is switched off, its usage period closed, and a `MAX_DURATION_EXCEEDED` alert raised — all in one batch. |
| **10.2 Light schedules** | Every `LIGHT` with `schedule_enabled` is driven to match its window, evaluated in the home's timezone from `users/{uid}.timezone`. |

Ticks every 60 seconds, which is what the contract specifies and why the app never shows
`00:00` against a device that is still `ON` — the cutoff lands up to a minute after the
countdown expires, and the hazard sheet says `Switching off…` across that gap.

## Running it

The service-account key **must not** live in this repository; `.gitignore` blocks the usual
filenames, but the safest thing is to keep it somewhere else entirely.

```powershell
cd worker
npm install
$env:GOOGLE_APPLICATION_CREDENTIALS = "C:\path\outside\this\repo\key.json"
node safety-worker.js
```

`node safety-worker.js --once` runs a single sweep and exits, which is what you want when
testing rather than filming.

## For the demo

Set one appliance to a **60–120 second** limit (screen prompt 07 §8) so the whole sequence
fits inside the video: the ring depletes, crosses into `stateError` for the final tenth,
reaches `Switching off…`, and then the worker's write arrives — the ring fades, the card
drops to `OFF`, the Critical line slides in, and `Auto cutoffs` increments.

Start the worker before you start recording. It logs each cutoff it performs, so the
terminal beside the phone is itself evidence that the write came from the server rather than
from the app.

## What it deliberately does not do

- **Never writes floor geometry, device placement or `config`.** Those belong to the app.
- **Skips a device that is `ON` with no `turned_on_at`.** That is a contract violation
  upstream, and guessing a start time would produce a cutoff at an arbitrary moment.
- **Leaves `ERROR` and `DISCONNECTED` alone.** A fault is not something a scheduler should
  quietly overwrite.
- **Writes `last_changed_by: "WORKER"`** on everything, which is what the app's metadata
  footers and the simulator's event log read to name the actor.
