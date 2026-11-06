# Model registry

Each training run writes `models/<model_version>/` containing:

- `model.json` — the serialised model: weights, feature config, calibration temperature, the
  `model_version`, and the `data_hash` of the snapshot it was trained on.
- `card.md` — the metrics card: precision / recall / false-positive rate / F1 / ECE on dev and
  holdout, with a per-language breakdown and the list of missed samples.

Model directories are git-ignored (they are reproducible from a snapshot + `config.yaml`). What goes
into a PR is the **metrics card contents** and the `data_hash`, not the binary.

## Production path (Phase 1+)

When the baseline is replaced by the distilled-MuRIL → LiteRT model, this directory also holds the
exported `.tflite` and its **SHA-256**, which is checksum-pinned where the Android app loads it — the
same tamper-evidence approach the repo uses for the Gradle wrapper. A model is only bundled in the APK
after it clears the enforce-mode gate on the real holdout.

## Versioning

`model_version` is semver with a build suffix (e.g. `0.1.0-baseline`, later `1.0.0-muril-int8`). Bump
it on any change to data, features, or architecture so a shipped APK can always report which model it
carries.
