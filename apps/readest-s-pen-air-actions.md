# Readest S Pen Air Actions

> **Status:** Experimental patch published in [v1.0.0-dev.11](https://github.com/Dragon0WaTTyler/dragon0wattyler-morphe-patches/releases/tag/v1.0.0-dev.11) for Readest `0.12.20` (version code `12020`). Build and stock-APK checks passed. The maintainer reported successful S Pen operation on a physical Galaxy S23 Ultra on **2026-11-06**; other devices and versions are not certified.

## Compatibility and behavior

- **App:** Readest for Android
- **Package:** `com.bilingify.readest`
- **Supported original APK:** `0.12.20` (`12020`), ARM64 APK
- **Patch:** `Readest S Pen Air Actions`
- **Actions:** S Pen click and left swipe send `PAGE_DOWN`; double click and right swipe send `PAGE_UP`.

Readest `0.12.20`'s Android activity is `com.bilingify.readest.MainActivity`. Its Android key dispatch passes ordinary keys through the Activity/WebView path, and Readest's default keyboard shortcuts already map Page Down to Next Page and Page Up to Previous Page. The shortcut handler ignores input fields and content-editable targets. This reuses Readest's logical navigation, including its existing book-direction handling, without a separate native event bridge. The bytecode fingerprint checks that the Activity key-dispatch method is present before the manifest declaration is applied.

Samsung's RemoteActions declaration is attached to that activity and uses one action set with two unique action IDs and priorities. Both actions permit a button and a motion gesture, as Samsung requires for combined click/swipe preferences. Repeated key events are disabled.

## Install

1. In Morphe Manager, add `Dragon0WaTTyler/dragon0wattyler-morphe-patches` as a patch source and enable **Pre-release** to receive the development release. Alternatively, for development use a locally built `patches-<version>.mpp` from `patches/build/libs/`.
2. Select the original Readest `0.12.20` ARM64 APK as the input and select **Readest S Pen Air Actions**.
3. Build and install the patched APK through Morphe Manager.
4. In Samsung Settings, open **Advanced features → S Pen → Air actions** (the exact labels can vary by One UI version), enable Air actions, open **App actions**, and select Readest. Set Next page to click and swipe left; set Previous page to double click and swipe right.

The patch does not set Samsung's `enable_key` attribute. Samsung documents that value as app-specific and issued internally; users enable Readest in Samsung's Air actions settings.

## Signing and backups

Android will not install a Morphe-signed APK as an update over a Readest APK signed with the developer's key. If Android reports a signature conflict, do not uninstall the original until you have backed up what matters. Uninstalling the original app can delete its local books, settings, annotations, and progress. Export or copy local data where possible, and verify that cloud sync has completed before removing the original. Reinstalling the official Readest APK may also require removing the Morphe-signed build first.

## Physical-device feedback (2026-11-06)

After installing the patched Readest build on a Samsung Galaxy S23 Ultra, the maintainer reported that the S Pen page-turning functionality worked as expected. This is **user-reported device validation**, not an independent hardware test or a guarantee for every reading mode. Gesture-by-gesture logs, other Galaxy models, other Readest versions, and EPUB/PDF-specific behavior have not been separately documented.

## Verification and limits

- The official `0.12.20` ARM64 APK was inspected: package `com.bilingify.readest`, version code `12020`, and activity `com.bilingify.readest.MainActivity` were confirmed.
- The tagged Readest source confirms Page Up/Down use the existing reader navigation path and that its Android activity passes ordinary keys to the WebView.
- The repository's Morphe patcher successfully applied the `.mpp` to the original APK. AAPT inspection of the output confirmed the activity registration, both distinct action labels, gesture preferences, Page Up/Down key codes, and disabled repeats.
- Repository unit tests, `.mpp` compilation, and applying the patch to the stock APK are build checks only. They do not prove Samsung Air Actions delivery, direction behavior across EPUB/PDF modes, or installation on a Galaxy device.
- This remains **experimental**. A Galaxy S23 Ultra user report confirms working behavior, but broad device compatibility and every reader mode remain unverified; do not treat the patch as Samsung-certified.
