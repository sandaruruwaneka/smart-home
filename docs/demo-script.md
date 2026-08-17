# Demo video — shooting script

**Target length:** 6 minutes. Anything longer and the marker starts skipping.

The spine of this video is one claim: *the safety cutoff is real and happens on the server,
not in the app.* Everything else is context for it. If you are short on time, cut the
editor and the reports section, never the cutoff.

---

## Before you press record

| | |
|---|---|
| **Phone** | Signed in as `demo@smarthome.test`. Open on **Demo Floor** — not Ground floor, which holds unnamed leftovers. |
| **Worker** | Running in a terminal *visible in frame*. `cd worker && node safety-worker.js`. Its log lines are your evidence the write came from the server. |
| **Iron** | `Bedroom Iron` set to a **60-second** limit and currently **OFF**. Turn it on during the take, not before. |
| **Do not disturb** | On. A Telegram banner across your cutoff shot means a reshoot. |
| **Battery** | Charged. The status bar is in every frame. |

Rehearse once without recording. The cutoff sequence has a waiting beat in it and you want
to know how long it feels.

---

## 1 · Opening — the problem (0:00–0:30)

Home screen, Demo Floor visible.

> "This is a smart home monitoring and control app. Four floors of devices, live from
> Firestore. The part I want to show you first isn't the controls — it's what happens when
> somebody leaves an iron on."

Point at the summary tiles: devices, active, errors, warnings. Tap into **Demo Floor**.

---

## 2 · The floor plan (0:30–1:15)

> "Every device sits on the floor plan where it physically is. State is carried by fill and
> border, not colour alone — an amber border means drawing power, a dashed border means
> unreachable."

Show, without narrating each one:

- The **2/3 badge** on the switch unit — one physical unit, several addressable switches
- The **dashed camera** — offline
- Pinch to zoom, double-tap to fit

> "The grid is drawn as dots rather than lines, so the architecture reads through it."

---

## 3 · The cutoff — the centre of the demo (1:15–3:00)

**This is the shot. Keep the terminal in frame.**

Tap the **Bedroom Iron** marker → hazard sheet opens.

> "This is an appliance with a maximum on-time. Sixty seconds, for the demo."

Turn it **ON**. The ring appears and starts depleting.

> "That countdown is the app's local view. The thing that will actually switch it off is a
> worker running server-side, on a sixty-second sweep."

**Wait.** Let the ring run. Narrate over it:

- At the final tenth: *"It crosses into red and pulses — the only pulse in the app, spent
  here deliberately."*
- At zero: *"It says 'Switching off' rather than nought-nought. The worker sweeps every
  sixty seconds, so the cutoff lands up to a minute after the countdown expires, and
  claiming otherwise would be a lie the user could catch."*

When the terminal prints `cutoff: Bedroom Iron was on 63s of a 60s limit`:

> "There it is — server-side."

The sheet transitions on its own: ring fades, card drops to **OFF**, the Critical line
slides in reading *Switched off automatically — maximum on time reached*, and **Auto
cutoffs** increments.

> "The app didn't do that. It was told."

---

## 4 · Alerts — the evidence (3:00–3:40)

Bottom bar → **Alerts**. The badge already shows the new count.

> "Every cutoff accumulates here. This is the record that the safety system fired."

- The new alert sits at the top of TODAY with its unacknowledged dot
- Filter to **Cutoffs**
- Swipe one row right to acknowledge — *"the row stays put. Nothing on this screen deletes
  an alert, because the history is the evidence."*

---

## 5 · The device sheets (3:40–4:30)

Back to the floor. Tap through two, briefly:

**Switch unit** — *"One physical gang plate, three independently addressable channels. The
unit is on if any channel is on."* Tap **All on**, then show the stacked timeline: *"you can
see the fan runs afternoons and the lights run evenings."*

**Hall Light** — *"A schedule as a 24-hour clock face. The arc is the window, the hand is
now, and it's dimmed because we're outside it."* Read the next-event line aloud: *"Turns on
in seven hours."*

> "A window crossing midnight draws as one arc here. On a linear bar it would split in two,
> and evening-to-morning is the commonest lighting schedule there is."

---

## 6 · Placement (4:30–5:15)

Tap the **pencil**.

> "Configuration is a separate mode, and it looks different on purpose — the grid switches
> from dots to lines, because here the cells are targets rather than scaffolding. No gesture
> in this mode can switch anything on."

- Arm **Outlet**, tap a cell — the device appears with a default name
- Point at the **amber dot** by the title: *"nothing has been written yet."*
- Drag a marker onto an occupied cell — it springs back and the cell flashes red
- Tap **undo**, then **Save**

> "Edits are staged and commit in one go. Dragging a marker across a grid would otherwise
> fire dozens of writes, and undo would be impossible."

---

## 7 · Camera and reports (5:15–5:50)

**Camera** — tap the Front Door marker.

> "Live HLS. The badge says LIVE because the player is actually playing — if the stream
> failed we'd fall back to a still and the badge would say SNAPSHOT. It describes what's on
> screen, not what the database offers."

Enter fullscreen briefly, exit.

**Reports** — bottom bar.

> "Three questions and no more: what ran the most, how it compares, and whether the safety
> system is firing more often than it should."

Point at **Automatic cutoffs**: *"a device appearing here repeatedly means its limit is too
short or it's genuinely being left on. Both are actionable — tapping it goes straight to
that device."*

---

## 8 · Close (5:50–6:00)

> "Twelve screens, a shared design system, and a safety guarantee that holds even with the
> phone off — because the worker runs server-side."

---

## If something goes wrong on camera

| Problem | What to do |
|---|---|
| Cutoff doesn't fire | Check the worker terminal is still running. It logs every sweep. |
| Countdown sits at `Switching off…` | Correct behaviour — the worker sweeps once a minute. Wait. Say so. |
| Camera shows `SNAPSHOT` not `LIVE` | The HLS stream is unreachable. Say the fallback is deliberate and move on; it is a feature, not a stumble. |
| A row shows "Firebase isn't set up" | Wrong build installed. Reinstall the debug APK. |
| Notification banner lands mid-shot | Reshoot that section. Do not try to edit around it. |

---

## What this video cannot show yet

Be honest about it in your report rather than hoping nobody asks:

- **No hardware simulator.** Fault injection isn't available, so `ERROR` and `DISCONNECTED`
  states are only visible where they were seeded. Bidirectional sync between phone and
  simulator is not demonstrated.
- **The worker in `worker/` is a stand-in.** It implements the contract, but Dev B's
  implementation is the real deliverable.
