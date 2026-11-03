# Blackmagic Camera launch failure investigation

**State:** Open. The runtime root cause is not yet confirmed. This note records evidence from 2026-11-03; no changes from this investigation have been pushed or published.

## Reported behavior

The owner reports that the original Blackmagic Camera installed from Google Play worked on the same phone. After Morphe successfully installed the patched build, launch showed:

> Get this app from Play
> To continue using Blackmagic Camera, get it on Google Play.

This is user-reported evidence; the device was not connected during this investigation, so the screen and device logs could not be independently captured.

## Morphe patching log shared by the owner

The owner later provided the Morphe log excerpt for the failed install. It records Manager `2.4.0-dev.5`, patcher `2.2.0-dev.3`, Android API 37 on a Samsung SM-S918B, Blackmagic Camera `3.5.1.0044`, `split=false`, ARM64, and `patches=2`. Both named patches succeeded. The patcher reports `Changed 3 of 23492 classes to mutable`, wrote new DEX, saved an 88,209,279-byte patched APK, and signed it successfully.

This confirms the launch failure occurred after Morphe successfully applied both patches and signed the output. It does **not** identify the resulting certificate, installer package/source, or the reason launch showed the Play prompt: the excerpt ends at patching success and contains no launch/logcat lines. The one run with both patches cannot distinguish shared repackaging from either patch.

## Owner-confirmed Morphe installer settings

The owner checked Manager's settings without changing them: **System Installer (Android Package Installer)** is the default installer; **Install as Play Store** is off; auto-install after patching is on; the installer selection prompt is off; Shizuku is not configured; and root mount is unavailable. The owner confirms the stock Play-installed app worked on this same phone before patching, while the Morphe-installed v1.0.0-dev.5 build displays “Get this app from Play.”

The owner separately reports that enabling **Install as Play Store** allowed the patched app to launch, although the camera UI still showed no added photo controls. The later settings check reported that the option was off while the Play prompt was present. These reports describe different test attempts/settings states; neither includes Android's recorded installer package, the installed patched certificate, or launch logs. The changed launch outcome makes the install path relevant evidence, but does not establish installer attribution as the sole cause or prove that Play Store mode is a safe or durable fix.

These are selected Manager options, not Android's recorded package `installingPackageName`. The exact installer provenance for the failed install remains unknown without device package data. Auto-install means Manager proceeds through its configured Android installer; it does not imply that Android records Google Play as installer.

### Reassessment with the latest settings check

The owner has now confirmed the settings observed on the device at the time of the latest report: **System Installer**, **Install as Play Store off**, **auto-install on**, **installer prompt off**, **Shizuku not configured**, and **root mount unavailable**. This strengthens the inference that Morphe initiated an ordinary PackageInstaller install; it still does not reveal the value Android recorded in `InstallSourceInfo`. The prompt-off and auto-install switches only control Manager's install flow, not the app's Play licensing verdict. Shizuku/root being unavailable also means there is no alternative privileged install path in this run.

The local APK decompilation gives a concrete, non-bypass explanation consistent with the symptom. The app's `Application.attachBaseContext()` invokes PairIP `LicenseClient.checkLicense()` before Blackmagic's camera activity. On this Android 17 device (API 37), `performLocalInstallerCheck()` asks Android for `getInstallSourceInfo(...).getInstallingPackageName()` and accepts only `com.android.vending`; the normal Morphe internal session path is expected to record Morphe or an OEM-mediated installer, but that final value is not observed. A failed local check is not by itself proof of the visible screen: the code then contacts Play's licensing service, and its `NOT_LICENSED` response (code 2) forwards Play's `PAYWALL_INTENT` to `LicenseActivity`, which sends it and closes the app. This is a direct static code path matching “Get this app from Play,” but no supplied launch log proves it executed.

Re-signing is a second, independent incompatibility signal. The available APKM base is Google-signed (certificate SHA-256 above). Morphe's shared patch log says it signed the modified output, and Morphe Manager documents signing patched APKs with its Manager keystore/different certificate. However, that log provides no certificate digest, and the locally generated validator APK is unsigned, not the 88,209,279-byte device-installed signed APK. Thus the exact installed signer mismatch has not been measured from the failed install; the available facts make it expected, while Play's exact integrity verdict remains unknown. The source manifest and PairIP code are unchanged by the camera patch output inspected locally, so there is no evidence the new camera hooks themselves disable licensing.

The earlier report that switching **Install as Play Store** on allowed a launch is relevant but not conclusive: it is a separate reported attempt, conflicts with the current check showing that option off during the described failure, and has no recorded Android installer, signer, or launch trace. It does not establish that attribution alone caused or fixed the result, and it does not rule out binary-integrity or full licensing checks. No setting change is recommended as a workaround. The present assessment is therefore: **PairIP/Play licensing or integrity rejection is the strongest explanation class; installer provenance is a strong contributing candidate under the confirmed settings; re-signing is independently expected to matter; none is confirmed as the exact runtime trigger without on-device evidence.**

The exact Manager `v2.4.0-dev.5` source narrows that inference. `InstallerManager` maps the System installer preference to `Token.Internal`, and `InstallViewModel` dispatches that plan to `performStandardInstall()`. That calls Morphe's `SessionInstaller.installInternal()` and creates a `PackageInstaller` session. That path does not set the installer package name to Google Play. Android's `SessionParams` API documents that, by default, the installer is the app that created the `PackageInstaller` object; the Morphe process package is `app.morphe.manager` (also shown in the owner's patch-log file path). So “System Installer” describes the Android confirmation/install UI, not necessarily the package Android records as installer-of-record. If the session is killed and Manager falls back to `ACTION_INSTALL_PACKAGE`, the same tagged Manager source explicitly supplies its own `app.packageName` as `EXTRA_INSTALLER_PACKAGE_NAME`. Thus, with the confirmed settings, the best-supported inference is that Android records Morphe (or an OEM-mediated equivalent), not `com.android.vending`. Only the device's `InstallSourceInfo` can confirm the final recorded value.

## Local artifacts examined

- `C:\Users\walid\AppData\Local\Temp\blackmagic-camera-3.5.1.0044-apkm\base.apk` is Blackmagic Camera package `com.blackmagicdesign.android.blackmagiccam`, version `3.5.1.0044`, version code `26101004`.
- The base APK passes `apksigner verify`. Its signer certificate SHA-256 is `8a889ddc2cac452968c5f2c62d339dd2f7f9dab526864b48ab53ee0a3e140cd9`; it also carries a Google Source Stamp (SHA-256 `3257d599a49d2c961a471ca9843f59d341a405884583fc087df4237b733bbd6d`). The APKM came from APKMirror, not from the user's Play installation; it is not proof of the exact binary installed on the phone.
- Local outputs exist for Photo Mode only, Take Photos While Recording only, and both. All report the same package, version and version code. They are static patch-applier outputs, not Morphe Manager's installed/signed package. `apksigner` reports `DOES NOT VERIFY` / `Missing META-INF/MANIFEST.MF` for these outputs because the validation helper does not sign them.
- The decoded Android manifest XML tree of the base APK and Photo-only output is identical. The patch declarations alter DEX hooks and add the extension; they do not edit manifest installer or licensing metadata.
- A fresh offline comparison confirms the raw `AndroidManifest.xml` entry SHA-256 is identical (`A6A2BD5F7000C21459FF3FF18EB505A9654C3F51B4F1063005EFA6A1B1D0E0E5`) between the APKMirror APKM's `base.apk` and the locally generated BOTH-patches APK. The local output adds `classes5.dex` and `classes6.dex`; no ZIP entries were removed. This static output is 29,594,158 bytes and is unsigned (`apksigner`: missing `META-INF/MANIFEST.MF`), so it is **not** Morphe Manager's 88,209,279-byte signed output from the phone log and cannot stand in for it at runtime.
- The only original app APK available locally is the APKM `base.apk` (32,251,500 bytes) plus separate ABI/resource splits, not the exact 88,057,611-byte monolithic input named in the Morphe log. `aapt dump badging` confirms package/version/version code `com.blackmagicdesign.android.blackmagiccam` / `3.5.1.0044` / `26101004`. The base APK's V3 signer certificate SHA-256 is `8a889ddc2cac452968c5f2c62d339dd2f7f9dab526864b48ab53ee0a3e140cd9` and it has a Google Source Stamp. This APKMirror archive is not a dump of the exact Play-installed phone package.
- JADX 1.5.6 decompilation of the original APKM base shows `com.pairip.licensecheck.LicenseClient` and `LicenseActivity`. The manifest names `com.pairip.application.Application` as the app `Application` class. Its `attachBaseContext()` calls `LicenseClient.checkLicense()` before the camera `MainActivity` feature hooks run.
- `aapt dump permissions` confirms the original manifest declares `com.android.vending.CHECK_LICENSE`, and `aapt dump xmltree` confirms the PairIP application class and `com.pairip.licensecheck.LicenseActivity`. Google documents that Automatic Integrity Protection can prompt users to get the app from Google Play when its installer check fails, and that a modification-check failure prevents the app from running. This exact protection is a plausible match for the observed message and the confirmed System Installer setup, but its enablement in this app's Play release is not visible in the local artifact.
- `LicenseClient.performLocalInstallerCheck()` reads `PackageManager.getInstallSourceInfo(packageName).getInstallingPackageName()` and accepts only `com.android.vending`. A different installer logs `Local install check failed due to wrong installer.` The client connects to `com.android.vending.licensing.ILicensingService`; response code `2` (`NOT_LICENSED`) starts the Play-provided `PAYWALL_INTENT`, the Play “Get this app” flow. This code path is present in the original app APK.
- In the same decompilation, `LicenseActivity.showPaywallAndCloseApp()` reads the supplied `paywallintent`, sends that `PendingIntent`, then closes the app; the visible Play screen is supplied by the pending intent, not text composed by the camera feature UI. This gives the PairIP license path a direct static route to a Play storefront screen, but it does not prove this route ran on the phone.
- Single-class JADX comparisons of the original base versus the local BOTH-patches output found the decompiled `LicenseClient`, `LicenseActivity`, and `com.pairip.application.Application` identical. The decoded manifest is also identical. The patched output is unsigned static validation output, so these comparisons establish unchanged licensing code/manifest, not the final Morphe signature or installer source on the phone.
- Patch fingerprints target `MainActivity`, `PipelineSurfaceManager`, and the video recorder-state class; neither `com.pairip.application.Application` nor the PairIP licensing classes are patch targets. The owner's two-patch log reports three changed app classes, matching the camera/runtime targets. This strongly points away from a feature hook causing the Play prompt.
- The file `uptodown-com.blackmagicdesign.android.blackmagiccam.apk` at the repository root is actually package `com.uptodown` (Uptodown's installer app), signed by Uptodown. It is not a Blackmagic Camera APK and was excluded from this analysis.
- `adb devices -l` returned no devices. No package install-source data, on-device signing certificate, logcat, or patched APK pulled from the phone is available.

## Assessment

**Strongest explanation supported offline: Play's app licensing / integrity protection rejects the repackaged install, with installer provenance as the leading trigger and re-signing as an additional integrity mismatch.** In the recovered `LicenseClient`, `Application.attachBaseContext()` starts licensing. On API 30+, `performLocalInstallerCheck()` accepts only `installingPackageName == "com.android.vending"`. For the owner's confirmed System Installer settings, Morphe v2.4.0-dev.5 source and Android's PackageInstaller API make app.morphe.manager the likely installer-of-record, though the device-reported value has not been captured. That makes a local installer-check failure highly likely. A failed local check does not itself display the prompt: the code proceeds to the full Play licensing service, whose response handler starts the Play-provided `PAYWALL_INTENT` on response code `2` (`NOT_LICENSED`). Separately, Google's Automatic Integrity Protection documentation says a failed installer check prompts users to get the app from Play; it also describes a modification check that prevents launch. The visible prompt is consistent with either route. The APK contains PairIP code and the license permission, but static strings/classes cannot establish whether Play Automatic Integrity Protection is enabled or identify which Play response produced this exact screen.

Changing the selected installer alone is not an established fix and is not recommended as a way to bypass these checks. Even if Android recorded a different installer, a Morphe-re-signed, modified binary may still fail application-integrity or full-license evaluation. The user's successful unmodified Play install on the same device makes a general device/account problem less likely, but does not provide the verdict for the patched package.

The patch code changes camera `MainActivity`, the preview output hook, and recorder-state behavior; it does not target the manifest, `com.pairip.application.Application`, or `LicenseClient`. In the local static comparison, the raw manifest entry is byte-identical and the JADX outputs for `Application` and `LicenseClient` are byte-identical. This weighs strongly against either camera feature hook directly creating the Play paywall. The user has tested only both patches together, however, and the actual signed installed APK is unavailable, so a no-feature Morphe-signed control and feature-isolated runtime tests remain unperformed.

A bounded class/string search of the decompiled APK found the PairIP licensing path but no direct Play Integrity API client references. Google's Automatic Integrity Protection is distinct from an app-integrated Play Integrity API client, and its protected checks may not appear as ordinary API client references in the inspected decompilation. The exact root cause remains unconfirmed because there is no Android-recorded install source, Morphe signer certificate, licensing response/logcat, or controlled runtime comparison.

### Evidence references

- [Google: Automatic integrity protection](https://developer.android.com/security/fraud-prevention/environment) — describes installer checks, the “get your app on Google Play” prompt, and modification checks.
- [Google: Play Integrity remediation dialogs](https://developer.android.com/google/play/integrity/remediation) — describes Play dialogs for `UNLICENSED` and `UNRECOGNIZED_VERSION` app verdicts.
- [Google: Play licensing overview](https://developer.android.com/google/play/licensing/overview) — describes the Play client licensing service and signed license responses.
- [Morphe Manager: patching process](https://github.com/MorpheApp/morphe-manager/blob/main/docs/patching-process.md) — says patched output is signed with the Manager keystore.
- [Morphe Manager: patching in simple mode](https://github.com/MorpheApp/morphe-manager/blob/main/docs/patching-simple-mode.md) — says the patched app is signed with a different certificate from the original.

- [Morphe Manager v2.4.0-dev.5 installer flow](https://github.com/MorpheApp/morphe-manager/blob/v2.4.0-dev.5/app/src/main/java/app/morphe/manager/domain/installer/SessionInstaller.kt) and [install dispatch](https://github.com/MorpheApp/morphe-manager/blob/v2.4.0-dev.5/app/src/main/java/app/morphe/manager/ui/viewmodel/InstallViewModel.kt) — the default session path and fallback attribution described above.
- [Morphe Manager v2.4.0-dev.5 installer preference mapping](https://github.com/MorpheApp/morphe-manager/blob/v2.4.0-dev.5/app/src/main/java/app/morphe/manager/domain/installer/InstallerManager.kt) — maps the System preference to `Token.Internal` and the Play Store preference separately to `Token.PlayStore`.
- [Android PackageInstaller SessionParams](https://developer.android.com/reference/android/content/pm/PackageInstaller.SessionParams#setInstallerPackageName(java.lang.String)) — by default, the installer is the app that created the PackageInstaller object.

## Controlled test status

| Test | Current evidence | Result |
| --- | --- | --- |
| Original installed from Google Play | Owner reports it opened on the same device; no captured APK, install-source record, or logcat | Reported pass, not independently verified |
| Re-signed build with no Blackmagic patches | Not built with Morphe's signer and not installed/launched; Manager setting check is not a zero-patch runtime test | Not tested |
| Photo Mode only | Static APK generated; package/version and decoded manifest checked; no device install/launch | Runtime not tested |
| Take Photos While Recording only | Static APK generated; package/version and decoded manifest checked; no device install/launch | Runtime not tested |
| Both patches | Owner's log confirms both applied and output signed; owner reports install succeeded but launch showed Play prompt | Runtime launch failed (user-reported); Play licensing / installer-integrity rejection is the leading cause class |
| Logcat and install provenance | Owner cannot currently connect the phone to a PC; supplied patching log and settings screen report contain no launch data, exact signer, or Android-recorded installer package | Still needed |

## Deferred device retest sequence

The owner currently cannot connect the phone to a PC and has asked not to reinstall or change settings. No install or settings change has been made or requested. When ADB is available, collect the read-only provenance and launch evidence below first. A no-patch Morphe-signed control and the three feature-selection controls would distinguish shared repackaging from a camera feature hook, but are deferred until the owner explicitly authorizes controlled installation tests. Keep the same Manager keystore and original source APK for those tests; never enable “Install as Play Store” as a way to mask the provenance check. Stop if Android reports a signature conflict, and back up camera settings, LUTs and local media before any future replacement.

## Safe next evidence

With the affected phone connected and USB debugging authorized, collect read-only evidence before changing the installation:

```powershell
$adb = 'C:\Users\walid\AppData\Local\Android\Sdk\platform-tools\adb.exe'
& $adb devices -l
& $adb shell pm path com.blackmagicdesign.android.blackmagiccam
& $adb shell pm list packages -i | Select-String 'com.blackmagicdesign.android.blackmagiccam'
& $adb shell dumpsys package com.blackmagicdesign.android.blackmagiccam | Select-String -Pattern 'InstallSource|installingPackage|initiatingPackage|originatingPackage' -Context 1,2
& $adb logcat -v threadtime -d -t 6000 | Select-String -Pattern 'LicenseClient|LicenseActivity|pairip|vending|blackmagiccam|Integrity|PlayCore|AndroidRuntime'
```

Do not clear logs or uninstall/replace the app during this first evidence capture. A no-patch Morphe-signed control and the three feature-selection controls require device launches. Replacing the package may erase app data because its signer differs; do those only on a backed-up or spare test device with the owner's explicit approval.

## Supported outcome

Do not remove, disable, spoof, or bypass Play licensing, installer checks, signature verification, or app-integrity checks. If the publisher's checks reject modified packages, the supported choices are the unmodified Play build or an authorized test build/distribution supplied by Blackmagic Design. The two camera features cannot be called usable until a patched build opens and works on a device. LUT matching and uninterrupted video/audio capture remain separate runtime acceptance tests after launch is resolved.
