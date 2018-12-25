1. Merge release branch to `master`
2. Update one `version` element for each `pom.xml`
3. Update maven and gradle import examples in `README.MD`
4. Delete local maven cache for `us.dustinj.timezonemap`
5. `mvn clean install`
6. `git commit -am "Update version to vXXX"`
7. `git push`
8. `git tag -a vXXX -m "Release vXXX"`
9. `git push origin vXXX`