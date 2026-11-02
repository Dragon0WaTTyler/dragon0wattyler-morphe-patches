# PocketTube Groups V8 candidate

- Replace the horizontal group chip strip and optional Spinner with one compact current-group button and a vertical Android popup menu.
- Keep “All groups” as the first choice regardless of the legacy `show_all` setting; preserve pinned/order sorting and the existing remembered group, default, and content-filter behavior.
- Keep group management and import/create actions in the existing options/manager entry points.
- Reduce selector row height to 44dp with small vertical margins. The custom feed filter row remains separate.
- Remove appearance settings that no longer affect the selector (`style`, `spacing`, `show_all`, and selector scroll position) from the settings list. Their persisted values remain accepted for backup compatibility.
- Recheck native video-menu reuse. The existing bridge still reopens only a live, validated native command captured from a YouTube-bound handler. RSS videos without that context continue to use the custom fallback; no native menu is synthesized.

## Candidate

`patches-2.0.0-groups.8.mpp`, built locally from the `dev` source at version `2.0.0-groups.8`.
