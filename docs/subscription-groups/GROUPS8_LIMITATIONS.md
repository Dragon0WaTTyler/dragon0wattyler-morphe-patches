# PocketTube Groups V8 limitations

- The compact selector and popup menu compile and pass the YouTube extension unit suite, but visual spacing and touch behavior have not been checked on the reported Samsung device. There was no connected Android device or target YouTube 21.30.216 APK in this build environment.
- The selector row is 44dp; the content filter chips remain in their own row. The claimed large blank region under the native toolbar could not be confirmed or measured without a target runtime. Do not treat its visual correction as device-verified.
- Native YouTube video menus are reused only when the original app has already bound a valid, live command for that video and current account/activity. Public RSS-only group-feed entries do not carry that native renderer/command context, so their overflow still uses the custom fallback.
- The native menu route does not guarantee that every action is appropriate or works from the custom feed anchor. Signed-in mutations, downloads, casting, feedback, and native-card equivalence remain unverified.
- Existing persisted `style`, `spacing`, `show_all`, and `scroll_x` values are retained in the preference schema for compatibility, but no longer control selector rendering.
