# Plan: independent per-app Morphe repositories

## Goal

Keep [Dragon0WaTTyler Morphe Patches](https://github.com/Dragon0WaTTyler/dragon0wattyler-morphe-patches) as the discovery/catalog entry point while giving each app its **own** GitHub repository, `.mpp` release stream, changelog, and source-update metadata.

## Current state

- The existing repository publishes a **shared** Morphe bundle and existing PocketTube-specific candidate .mpp assets in GitHub Releases.
- PocketTube Groups V8.1 is available as a development candidate in [v1.0.0-dev.4](https://github.com/Dragon0WaTTyler/dragon0wattyler-morphe-patches/releases/tag/v1.0.0-dev.4).
- Blackmagic Camera has **no** patch bundle or release published in this repository.
- A folder or a separate Git tag **does not** create a separately managed GitHub Releases page. That requires a different repository.

## Repositories to create

The following names are proposed; these GitHub URLs must not be advertised as active until the repositories exist:

1. `Dragon0WaTTyler/dragon0wattyler-pockettube`
2. `Dragon0WaTTyler/dragon0wattyler-blackmagic`

Create each repository under the same GitHub account, initially without a README/license/.gitignore, and grant the GitHub connector access to it if the connector is limited to selected repositories.

## Migration checklist — PocketTube

1. Establish the new repository from the appropriate source history, **preserving license/NOTICE and relevant upstream attributions**.
2. Audit whether the standalone project should bundle only the custom PocketTube patch or additionally carry pinned base patches. Make the contents clear to users; do not just rename a combined .mpp.
3. Update project metadata (`patches.about`), package/group identifiers if needed, README, add-source URL, and `.releaserc` for the new repository.
4. Retain the existing, known release workflow pattern, independently build and validate the new `.mpp`, and verify that release JSON links resolve to that exact asset.
5. Create the first new **development prerelease** using the repository's actual release workflow. Verify GitHub Releases, SHA/checks, Morphe Manager source import and the desired APK/app compatibility.
6. Update the catalog and only then recommend switching users from the existing shared patch source. Avoid patching the same app with both sources simultaneously.
7. Keep old release assets available for rollback.

## Migration checklist — Blackmagic Camera

1. Bring in the actual Blackmagic Camera patch implementation once source is ready (a project idea alone is not a functional patch).
2. Determine precise APK compatibility and the patch's behavior with static checks, build, patch application, and device validation.
3. Set correct package targeting and independent patch metadata.
4. Configure the standard Morphe release generation and validate the resulting `.mpp` and downloadable `patches-bundle.json`.
5. Publish a release only after a working build; add the verified link to the catalog.

## Safety of the existing source

Until the new repositories have working releases, **keep the original repository's GitHub tags, release assets, and Morphe source metadata intact**. The original `dev`/`main` release workflows and generated patch-list markers are retained in the source repository. Updating the catalog/docs alone does not create independent version streams or move existing installs.
