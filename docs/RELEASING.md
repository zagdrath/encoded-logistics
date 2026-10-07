# Releasing

A release is a pushed `vX.Y.Z` tag. `.github/workflows/release.yml` builds that commit, runs the game tests and
publishes a GitHub Release with `encodedlogistics-X.Y.Z+26.3.jar` and the version's section of `CHANGELOG.md` as its
notes. `build.yml` runs the same build and game tests on every push, so a release shouldn't be the first place a
failure shows up.

## Cutting a release

1. Pick the version from the rules at the top of `CHANGELOG.md` (the Unreleased section suggests one).
2. In `CHANGELOG.md`:
   - rename `## [Unreleased]` to `## [X.Y.Z] - YYYY-MM-DD` and delete its "Suggested version" line;
   - add a new, empty `## [Unreleased]` above it;
   - at the bottom, point `[Unreleased]` at `https://github.com/zagdrath/encoded-logistics/compare/vX.Y.Z...HEAD`,
     and add `[X.Y.Z]: https://github.com/zagdrath/encoded-logistics/compare/vPREVIOUS...vX.Y.Z`
     (for the first release, `[1.0.0]: https://github.com/zagdrath/encoded-logistics/commits/v1.0.0`).
3. Set `mod_version=X.Y.Z` in `gradle.properties`.
4. Check it all before tagging:

   ```sh
   bash .github/scripts/release-notes.sh X.Y.Z      # the same checks release.yml runs; writes release-notes.md
   ./gradlew build runGameTestServer
   ```

5. Commit ("Release X.Y.Z: ..."), then tag and push:

   ```sh
   git tag -a vX.Y.Z -m "Encoded Logistics X.Y.Z"
   git push origin main vX.Y.Z
   ```

6. Watch the Release run on the Actions tab. When it's green, the release is on the Releases page with the jar.

A tag with a pre-release part (`v1.1.0-beta.1`, with `mod_version=1.1.0-beta.1`) is published as a pre-release.

## If the release run fails

The tag is already pushed but no release exists. Fix the problem, commit, then move the tag:

```sh
git push origin :refs/tags/vX.Y.Z
git tag -f -a vX.Y.Z -m "Encoded Logistics X.Y.Z"
git push origin vX.Y.Z
```

## Arcforge's API

The Arcforge integration compiles against `net.zagdrath.arcforge:arcforge-api`, kept in `libs/maven` so local and
CI builds need no Arcforge checkout. Each version is the `arcforge-api-<version>.jar` attached to the Arcforge
release that ships it (1.1.0 from Arcforge v2.5.0). To move to a new API version:

1. Download that jar from the Arcforge release.
2. Put it in `libs/maven/net/zagdrath/arcforge/arcforge-api/<version>/`, with a pom like the 1.1.0 one (change the
   version).
3. Raise `arcforge_api_version` in `gradle.properties`.
4. If players need a newer Arcforge, say so in the changelog.
