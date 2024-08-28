Releasing
========

 1. Update `VERSION_NAME` in `gradle.properties` to the release (non-SNAPSHOT) version.
 2. Update `CHANGELOG.md` for the impending release.
 3. Update the `README.md` with the new version.
 4. `git commit -am "Prepare for release X.Y.Z"` (where X.Y.Z is the new version)
 5. `git tag -a X.Y.Z -m "X.Y.Z"` (where X.Y.Z is the new version)
 6. Update `VERSION_NAME` in `gradle.properties` to the next SNAPSHOT version.
 7. `git commit -am "Prepare next development version"`
 8. `git push && git push --tags`

This will trigger a GitHub Action workflow which will upload the release artifacts to Maven Central.
