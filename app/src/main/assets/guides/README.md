# Offline Exercise Guides

The catalog is intentionally empty. Do not publish unreviewed demonstrations or
invent movement instructions from exercise names. Existing database cues remain
the fallback until authorized, reviewed materials are supplied.

Each entry in `exercise_guides.json` has these fields:

- `exerciseId`: exact program exercise ID, unique in the catalog.
- `steps`: nonempty array of short, reviewed instructions.
- `images`: nonempty array of objects with `assetPath` and `description`.
- `source`: attribution and provenance of instructions and images.
- `license`: permission covering distribution of the materials in the APK.
- `reviewedBy`: competent reviewer responsible for checking the demonstrated exercise and instructions.
- `verified`: false until provenance, rights and review have been confirmed.

Image paths must match `guides/images/<filename>.png`, `.jpg` or `.webp`.
Use bounded-size bitmap images, including starting and execution positions where
needed. Give each image a useful description; do not mirror laterality-sensitive
demonstrations automatically. All assets must work without network access.

The catalog validator rejects duplicate IDs and incomplete verified entries.
Only verified entries are displayed. A catalog, decode or missing-file failure
falls back to database cues and does not interrupt playback preparation.

Before marking the visual requirement complete, validate every exercise enabled
in every phase and the minimal prescription, all referenced files, offline
rendering, framing, large fonts and TalkBack on the physical device. Catalog
metadata is not a substitute for human review.