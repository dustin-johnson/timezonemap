1.  Merge release branch to `master`.
2.  Ensure `map.version` in the parent pom's properties is [up to date](https://github.com/evansiroky/timezone-boundary-builder/releases/).
3.  Update `TimeZoneMapTest.java:getMapVersion()` to use the new version and `map.version`.
4.  `./mvnw versions:set -DgenerateBackupPoms=false -DnewVersion=4.3`
5.  `./mvnw versions:display-dependency-updates`
6.  Update maven and gradle import examples in `README.MD`.
7.  Delete local maven cache: `rm -rf ~/.m2/repository/us/dustinj/timezonemap`
8.  `./mvnw clean install`
9.  `git commit -am "Update version to v4.3"`
10. `git push`
11. `git tag -a v4.3 -m "Release v4.3"`
12. `git push origin v4.3`