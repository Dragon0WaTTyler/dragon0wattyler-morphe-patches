# PocketTube Groups V8.1 verification

## Source and bundle checks

- Candidate: `patches-2.0.0-groups.8.1.mpp` (15,606,364 bytes; SHA-256 `EFE695F3A58A5790F17B3F9062E00D9865848DBD342FF6014614106586D60A2D`).
- `:extensions:youtube:test`: 421 tests, 0 failures, 0 errors, 0 skipped.
- `:patches:checkStringResources`: 159,174 strings verified, no issues.
- `:patches:buildAndroid`: successful. The candidate bundle passed ZIP/JAR listing validation.
- `git diff --check`: clean.

The layout fixture verifies that the matched carousel row is detached, the mock feed begins directly below the toolbar after the native carousel/filter rows are removed, and the row is restored to its original parent when Groups exits. Existing feed integration coverage verifies long-press PocketTube watched actions without reloading RSS.

## Runtime boundary

`adb devices` returned no connected device. There is no target APK available in the workspace. The screenshot-specific blank region and native menu reuse on the Samsung device cannot yet be claimed visually verified; run [ANDROID_CHECKLIST_V8_1.md](ANDROID_CHECKLIST_V8_1.md). Native fallback eligibility is specified in [GROUPS8_1_LIMITATIONS.md](GROUPS8_1_LIMITATIONS.md).
