# Releasing

A release is a pushed `vX.Y.Z+MC` tag, where `X.Y.Z` is `mod_version` and `MC` is `minecraft_version` in
`gradle.properties` (for example `v1.0.0+26.1.2`). `.github/workflows/release.yml` builds that commit, runs the game
tests and publishes a GitHub Release with `encodedlogistics-X.Y.Z+MC.jar` and the version's section of `CHANGELOG.md`
as its notes. Its `curseforge` job then uploads the same jar and notes to
[CurseForge](https://www.curseforge.com/minecraft/mc-mods/encoded-logistics) (project 1731316). `build.yml` runs the
same build and game tests on every push, so a release shouldn't be the first place a failure shows up.

## Minecraft versions

Each supported Minecraft version has its own branch, and releases for it are tagged from that branch:

| Branch | Minecraft | Tags |
|---|---|---|
| `main` | 26.3 (the newest) | `vX.Y.Z+26.3` |
| `mc/26.1` | 26.1.2 | `vX.Y.Z+26.1.2` |

- Each branch has its own `gradle.properties` (Minecraft, NeoForge, JEI and Jade versions), its own `CHANGELOG.md`
  and its own code for whatever the Minecraft versions do differently.
- A version number means the same changes on every Minecraft version: 1.2.0 for 26.1.2 has what 1.2.0 for 26.3
  has. A version that only ships on some branches simply isn't released on the others.
- Make a change on the branch it's for, then carry it to the others with `git cherry-pick`, fixing whatever the
  other Minecraft version needs. Keep code that differs between versions small and in one place (as
  `client/ShapeOutlines.java` is on `mc/26.1`), so cherry-picks apply cleanly.
- Only `main`'s full releases are marked "Latest" on GitHub; releases from other branches and pre-releases aren't.

## Cutting a release

Do this on the branch for the Minecraft version you're releasing.

1. Pick the version from the rules at the top of `CHANGELOG.md` (the Unreleased section suggests one).
2. In `CHANGELOG.md`:
   - rename `## [Unreleased]` to `## [X.Y.Z] - YYYY-MM-DD` and delete its "Suggested version" line;
   - add a new, empty `## [Unreleased]` above it;
   - at the bottom, point `[Unreleased]` at `https://github.com/zagdrath/encoded-logistics/compare/vX.Y.Z+MC...HEAD`,
     and add `[X.Y.Z]: https://github.com/zagdrath/encoded-logistics/compare/vPREVIOUS...vX.Y.Z+MC`, where
     `vPREVIOUS` is the previous tag on this branch (for a branch's first release,
     `[X.Y.Z]: https://github.com/zagdrath/encoded-logistics/commits/vX.Y.Z+MC`).
3. Set `mod_version=X.Y.Z` in `gradle.properties`.
4. Check it all before tagging:

   ```sh
   bash .github/scripts/release-notes.sh X.Y.Z MC   # the same checks release.yml runs; writes release-notes.md
   ./gradlew build runGameTestServer
   ```

5. Commit ("Release X.Y.Z for Minecraft MC: ..."), then tag and push the branch and the tag:

   ```sh
   git tag -a vX.Y.Z+MC -m "Encoded Logistics X.Y.Z for Minecraft MC"
   git push origin <branch> vX.Y.Z+MC
   ```

6. Watch the Release run on the Actions tab. When it's green, the release is on the Releases page with the jar, and
   the file is on CurseForge (it shows there once CurseForge has approved it, usually within minutes).

A tag with a pre-release part (`v1.1.0-beta.1+26.1.2`, with `mod_version=1.1.0-beta.1`) is published as a pre-release.

The first release, 1.0.0 for Minecraft 26.3, was tagged `v1.0.0` before tags carried the Minecraft version.

## CurseForge

The upload is `.github/scripts/curseforge_upload.py`. It sends:

- the jar and the version's changelog section, as Markdown;
- the display name "Encoded Logistics X.Y.Z+MC";
- the game versions: the branch's Minecraft version, NeoForge, Java 25, Client and Server;
- `arcforge`, `jei` and `jade` as optional dependencies;
- the file type: release, or beta / alpha for a pre-release version.

It finds CurseForge's game version ids by name on each upload, and fails if CurseForge doesn't list the Minecraft
version yet.

It needs a CurseForge upload API token in the `CURSEFORGE_TOKEN` repository secret. Make the token at
authors.curseforge.com (account settings, API tokens), then run `gh secret set CURSEFORGE_TOKEN -R
zagdrath/encoded-logistics` and paste it in. Don't commit the token or put it anywhere else. To check the
metadata without uploading, run the script with `--dry-run` and the token in `CURSEFORGE_TOKEN` (the arguments are
in release.yml and at the top of the script).

## If the release run fails

If only the `curseforge` job failed (a bad token, CurseForge down, an unknown game version), the GitHub Release is
already out: fix the cause, for example the secret, and use **Re-run failed jobs** on the run.

Otherwise the tag is already pushed but no release exists. Fix the problem, commit, then move the tag:

```sh
git push origin :refs/tags/vX.Y.Z+MC
git tag -f -a vX.Y.Z+MC -m "Encoded Logistics X.Y.Z for Minecraft MC"
git push origin vX.Y.Z+MC
```

## Arcforge's API

The Arcforge integration compiles against `net.zagdrath.arcforge:arcforge-api`, kept in `libs/maven` so local and
CI builds need no Arcforge checkout. Each version is the `arcforge-api-<version>.jar` attached to the Arcforge
release that ships it (1.1.0 from Arcforge v2.5.0). The API uses Minecraft and NeoForge types, so each branch takes
the jar from Arcforge's release for the same Minecraft version (Arcforge's tags are `vA.B.C+MC` too). To move to a new
API version:

1. Download that jar from the Arcforge release for this branch's Minecraft version.
2. Put it in `libs/maven/net/zagdrath/arcforge/arcforge-api/<version>/`, with a pom like the 1.1.0 one (change the
   version).
3. Raise `arcforge_api_version` in `gradle.properties`.
4. If players need a newer Arcforge, say so in the changelog.
