1.  Merge release branch to `master`.
2.  Ensure `map.version` in the parent pom's properties is up to date.
3.  `mvn versions:set -DgenerateBackupPoms=false -DnewVersion=XXX`
4.  `mvn versions:display-dependency-updates`
5.  Update maven and gradle import examples in `README.MD`.
6.  Delete local maven cache for `us.dustinj.timezonemap`.
7.  `mvn clean install`
8.  `git commit -am "Update version to vXXX"`
9.  `git push`
10. `git tag -a vXXX -m "Release vXXX"`
11. `git push origin vXXX`