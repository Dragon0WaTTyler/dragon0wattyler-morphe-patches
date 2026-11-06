# Spotify native ART probe

This optional probe loads a private **patched 9.1.94.1929 ARM64 base APK** on an
Android runtime without starting Spotify or contacting its services. It checks
native settings row construction, menu constructors/provider interfaces, server
filter identity and parceling, native album/artist row/page metadata, and all eight
mapped primitive theme color paths. It does not validate login, catalog access,
playback, downloads, or Android 17 behavior.

Compile `SpotifyArtProbe.java` with Java 21 against an installed Android SDK
`android.jar` (API 35 was used), targeting Java 17. Convert the class with SDK D8
using minimum API 24. Keep the generated class, DEX and APK outside the repository.
Use an isolated emulator and specify its serial explicitly for every ADB command.
Copy the probe DEX and patched APK under `/data/local/tmp/spicetify-probe/`, make
both files read-only, and create an `opt` directory there. Run:

```sh
adb -s emulator-5554 shell 'CLASSPATH=/data/local/tmp/spicetify-probe/probe.dex app_process -Xverify:all /system/bin SpotifyArtProbe /data/local/tmp/spicetify-probe/probe.apk /data/local/tmp/spicetify-probe/opt'
```

The probe exits with an exception if an assertion or native constructor fails.
It intentionally uses no account/session material and does not launch an Activity
screen. Its Activity instance is a local constructor fixture for the settings DI
bridge. No hidden-API policy changes, native library loading, or attestation bypass
are needed. The APK and compiled probe must not be committed or distributed.
