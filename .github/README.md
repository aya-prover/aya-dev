[![actions]](https://github.com/aya-prover/aya-dev/actions/workflows/gradle-check.yml)
[![maven]][maven-repo]
[![gitter]](https://gitter.im/aya-prover/community?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge)
[![codecov]](https://codecov.io/gh/aya-prover/aya-dev)
[![tokei]](https://github.com/XAMPPRocky/tokei)
[![Bors enabled](https://bors.tech/images/badge_small.svg)](https://app.bors.tech/repositories/37715)

[**Website**](https://www.aya-prover.org) contains:

+ Development blogs which are written for general audience
+ [Installation](https://www.aya-prover.org/guide/install.html)
  instructions (basically telling you what to download in [GitHub Releases])

Aya is under active development, so please expect bugs, usability or performance issues
(please file issues or create threads in discussions!).
However, we can share some cool stuffs here:

+ Dependent types, including pi-types, sigma types, etc.
  You could write a [type-safe interpreter][gadt].
+ De Morgan cubical type theory with generalized path types
  similar to a bounded cubical subtype.
  + Implementation prototype: [Guest0x0].
+ Pattern matching with first-match semantics.
  We can implement [red-black tree][rbtree] (without deletion) elegantly.
+ Overlapping and order-independent patterns.
  Very [useful][oop] in theorem proving.
+ A literate programming mode with inline code fragment support.
  We already have a prototype, but we plan to revise it before sharing demos.
+ Binary operators, with precedence specified by a partial ordering
  (instead of a number, such as in Haskell or Agda)
  which is useful for [equation reasoning][assoc].
+ Termination checker inspired by Andreas Abel's foetus.
  We adapted some code from Agda's implementation to accept
  [more definitions][foetus] (which are rejected by, e.g. Arend).
+ Inference of type checking order. That is to say,
  no syntax for forward-declarations is needed for [mutual recursions][mutual].

See also [use as a library](#use-as-a-library).

[GitHub Releases]: https://github.com/aya-prover/aya-dev/releases/tag/nightly-build
[Java 19]: https://jdk.java.net/19

## Build

The minimum required version of Java is [Java 19].

Since you need [Java 19] to set this project up, in case your choice
of IDE is IntelliJ IDEA, version 2022.3 or higher is required.
If you have problems downloading dependencies (like you are in China),
check out [how to][proxy] let gradle use a proxy.

```bash
# build Aya and its language server as applications to lsp/build/image
# the image is usable in Java-free environments 
./gradlew jlink --rerun-tasks
# build Aya and its language server as executable
# jars to <project>/build/libs/<project>-<version>-fat.jar
./gradlew fatJar
# build a platform-dependent installer for Aya and its language
# server with the jlink artifacts to lsp/build/jpackage
# requires https://wixtoolset.org/releases on Windows
./gradlew jpackage
# run tests and generate coverage report to build/reports
./gradlew testCodeCoverageReport
# (Windows only) show the coverage report in your default browser
./gradlew showCCR
```

## Contributing to Aya

+ Questions or concerns are welcomed in the discussion area.
  We will try our best to answer your questions, but please be nice.
+ We welcome nitpicks on error reporting! Please let us know anything not perfect.
  We have already implemented several user-suggested error messages.
+ Before contributing in any form, please read
  [the contribution guideline](https://github.com/aya-prover/aya-dev/blob/master/.github/CONTRIBUTING.md) thoroughly
  and make sure you understand your responsibilities.
+ Please follow [the Code of Conduct](https://github.com/aya-prover/aya-dev/blob/master/.github/CODE_OF_CONDUCT.md) to
  ensure an inclusive and welcoming community atmosphere.
+ Ask [@ice1000] to become an organization member.
  + If you want to contribute, ask before doing anything.
    We will tell you about our plans.

[@ice1000]: https://github.com/ice1000
[actions]: https://github.com/aya-prover/aya-dev/actions/workflows/gradle-check.yml/badge.svg
[codecov]: https://img.shields.io/codecov/c/github/aya-prover/aya-dev?logo=codecov&logoColor=white
[gitter]: https://img.shields.io/gitter/room/aya-prover/community?color=cyan&logo=gitter
[tokei]: https://img.shields.io/tokei/lines/github/aya-prover/aya-dev?logo=java
[maven]: https://img.shields.io/maven-central/v/org.aya-prover/base?logo=gradle
[oop]: ../base/src/test/resources/success/common/src/Arith/Nat/Core.aya
[proxy]: https://docs.gradle.org/current/userguide/build_environment.html#sec:accessing_the_web_via_a_proxy
[gadt]: ../base/src/test/resources/success/src/TypeSafeNorm.aya
[regularity]: ../base/src/test/resources/success/common/src/Paths.aya
[funExt]: ../base/src/test/resources/success/common/src/Paths.aya
[rbtree]: ../base/src/test/resources/success/common/src/Data/RedBlack.aya
[assoc]: ../base/src/test/resources/success/src/Assoc.aya
[foetus]: ../base/src/test/resources/success/src/FoetusLimitation.aya
[mutual]: ../base/src/test/resources/success/src/Order.aya
[maven-repo]: https://repo1.maven.org/maven2/org/aya-prover
[Guest0x0]: https://github.com/ice1000/Guest0x0

## Use as a library

It's indexed in [mvnrepository](https://mvnrepository.com/artifact/org.aya-prover),
and here are some example build configurations:

```xml
<!-- Maven -->
<dependency>
    <groupId>org.aya-prover</groupId>
    <artifactId>[project name]</artifactId>
    <version>[latest version]</version>
</dependency>
```

```groovy
// Gradle
implementation group: 'org.aya-prover', name: '[project name]', version: '[latest version]'
```

+ `[project name]` specifies the subproject of Aya you want to use, and the options are `pretty`, `base`, `cli`, `parser`, etc.
  + The type checker lives in `base` and `parser`.
  + The generalized pretty printing framework is in `pretty`.
  + The generalized binary operator parser, generalized tree builder, generalized mutable graph,
    and a bunch of other utilities (strings, files, etc.) are in `tools`.
  + The command and argument parsing framework is in `tools-repl`.
    It offers an implementation of ANTLR4-based jline3 parser and relevant facilities.
+ `[latest version]` is what you see on this badge ![maven] .
