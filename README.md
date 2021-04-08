[![actions]](https://github.com/aya-prover/aya-dev/actions/workflows/gradle-check.yml)
[![](https://jitpack.io/v/aya-prover/aya-dev.svg)](https://jitpack.io/#aya-prover/aya-dev)
[![gitter]](https://gitter.im/aya-prover/community?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge)
[![codecov]](https://codecov.io/gh/aya-prover/aya-dev)
[![](https://tokei.rs/b1/github/aya-prover/aya-dev?category=code)](https://github.com/XAMPPRocky/tokei)

You need Java SE 16 to set this project up. This also means if your choice of IDE is IntelliJ IDEA, you need version
2021.1 or higher. If you have problems downloading dependencies (like you are in China), check out [how to][proxy] let
gradle use proxies.

```bash
# create a runnable jar at the root directory of the repo
./gradlew copyJarHere
# run tests and generate coverage report
./gradlew mergeJacocoReports
# do both
./gradlew githubActions
```

+ Ask [@ice1000] to get access to the development repository.
  + Do not create pull requests directly. Ask before doing anything.
+ Questions opened as issues will be answered.
+ Aya is under active development. Nothing guaranteed, but we can share something here:
  + Dependent types, including pi-types, sigma types, etc. You could write a [type-safe interpreter][gadt].
  + Overlapping patterns. Very [useful][oop] in theorem proving.
  + Arend-ish interval type which is used to define the HoTT [path type][path].

[@ice1000]: https://github.com/ice1000
[actions]: https://github.com/aya-prover/aya-dev/actions/workflows/gradle-check.yml/badge.svg
[codecov]: https://codecov.io/gh/aya-prover/aya-dev/branch/main/graph/badge.svg?token=Z4CDKG8VSX
[gitter]: https://badges.gitter.im/aya-prover/community.svg
[oop]: tester/src/test/aya/success/add-comm.aya
[path]: tester/src/test/aya/success/cong-sym-trans.aya
[proxy]: https://docs.gradle.org/current/userguide/build_environment.html#sec:accessing_the_web_via_a_proxy
[gadt]: tester/src/test/aya/success/type-safe-norm.aya
