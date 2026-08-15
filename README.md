# smart-home

Smart Home Monitoring & Control System — Kotlin app (Dev A).

The Firestore data contract shared with the React simulator and the safety worker is
`SCHEMA.md`, currently circulated between the two developers rather than committed here.
It is the seam between the three codebases: none of them calls the others, they only read
and write the documents it describes. Update it *before* changing code.

Code comments in `data/` cite it by section number, so a section that moves should be
re-checked against those references. Committing the contract to one of the three repos —
so the two sides cannot drift onto different copies — is worth agreeing with Dev B.

## Firebase setup

The data layer compiles without any of this. Running the app against a real project needs
three things:

1. **`app/google-services.json`** — download it from the Firebase console into `app/`,
   then uncomment `alias(libs.plugins.google.services)` in `app/build.gradle.kts`. The
   plugin fails the build when the file is missing, which is why it ships disabled.
2. **Composite indexes** — `firebase deploy --only firestore:indexes` using
   `firebase/firestore.indexes.json`. Firestore refuses the affected queries without them
   and the error appears only at runtime, so do this before the demo.
3. **Security rules** — `firebase deploy --only firestore:rules` using
   `firebase/firestore.rules`.

## Layout

| Path | What lives there |
|---|---|
| `app/src/main/java/com/smarthome/control/ui/` | Design system: theme, components, gallery |
| `app/src/main/java/com/smarthome/control/data/` | Firestore models, mappers and repositories |
| `firebase/` | Rules and index definitions, deployed with the Firebase CLI |
| `app/src/test/` | Unit tests for the contract logic that carries no Firestore dependency |

Repositories are reached through `SmartHomeData`, which constructs them lazily so nothing
touches Firebase before `FirebaseApp` has initialised.
