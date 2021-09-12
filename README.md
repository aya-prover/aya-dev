[![actions]](https://github.com/aya-prover/aya-dev/actions/workflows/gradle-check.yml)
[![jitpack]](https://jitpack.io/#aya-prover/aya-dev)
[![maven]](https://repo1.maven.org/maven2/org/aya-prover)
[![gitter]](https://gitter.im/aya-prover/community?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge)
[![codecov]](https://codecov.io/gh/aya-prover/aya-dev)
[![tokei]](https://github.com/XAMPPRocky/tokei)

You need Java SE 17 (release-candidates available at <https://jdk.java.net/17/>) to set this project up.
This also means if your choice of IDE is IntelliJ IDEA, you need version 2021.2 or higher.
If you have problems downloading dependencies (like you are in China), check out [how to][proxy] let
gradle use proxies.

```bash
# build Aya and its language server as applications which
# can be used in Java-free environments to lsp/build/image
./gradlew jlink
# build Aya, its language server, and the telegram bot as
# executable jars to <project>/build/libs/<project>-<version>-fat.jar
./gradlew fatJar
# build a platform-dependent installer for Aya and its language
# server with the jlink artifacts to lsp/build/jpackage
# requires https://wixtoolset.org/releases on Windows
./gradlew jpackage
# run tests and generate coverage report to build/reports
./gradlew mergeJacocoReports
# do mergeJacocoReports *and* jlink
./gradlew githubActions
```

+ Ask [@ice1000] to get access to the development repository.
  + If you want to contribute, ask before doing anything.
    We will tell you about our plans.
+ Questions or concerns are welcomed in the discussion area. 
  We will try our best to answer your questions, but please be nice.
+ Aya is under active development. Nothing guaranteed! However, we can share some cool stuffs here:
  + Dependent types, including pi-types, sigma types, etc. You could write a [type-safe interpreter][gadt].
  + Arend-ish interval type which is used to define the HoTT [path type][path]
    and prove [regularity by computation][regularity] thanks to Arend's type theory.
    We also have the classic cubical-flavored [funExt].
  + Overlapping patterns. Very [useful][oop] in theorem proving.
  + A literate programming mode with inline code fragment support.
    We already have a prototype, but we plan to revise it before sharing demos.

[@ice1000]: https://github.com/ice1000
[actions]: https://github.com/aya-prover/aya-dev/actions/workflows/gradle-check.yml/badge.svg
[codecov]: https://img.shields.io/codecov/c/github/aya-prover/aya-dev?logo=codecov&logoColor=white
[gitter]: https://img.shields.io/gitter/room/aya-prover/community?color=cyan&logo=gitter
[jitpack]: https://img.shields.io/jitpack/v/github/aya-prover/aya-dev?logo=github
[tokei]: https://img.shields.io/tokei/lines/github/aya-prover/aya-dev?logo=java
[maven]: https://img.shields.io/maven-central/v/org.aya-prover/base?logo=gradle
[oop]: base/src/test/resources/success/add-comm.aya
[path]: base/src/test/resources/success/cong-sym-trans.aya
[proxy]: https://docs.gradle.org/current/userguide/build_environment.html#sec:accessing_the_web_via_a_proxy
[gadt]: base/src/test/resources/success/type-safe-norm.aya
[regularity]: base/src/test/resources/success/regularity.aya
[funExt]: base/src/test/resources/success/funExt.aya
