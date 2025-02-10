[![actions]](https://github.com/aya-prover/aya-dev/actions/workflows/gradle-check.yaml)
[![maven]][maven-repo]
[![codecov]](https://codecov.io/gh/aya-prover/aya-dev)

[**Website**](https://www.aya-prover.org) contains:

+ Development blogs which are written for general audience
+ [Installation](https://www.aya-prover.org/guide/install.html)
  instructions (basically telling you what to download in [GitHub Releases])
+ [Tutorial for functional programming features](https://www.aya-prover.org/guide/haskeller-tutorial.html)
+ [Tutorial for theorem proving features](https://www.aya-prover.org/guide/prover-tutorial.html)

> [!WARNING]
>
> Aya is under active development, so don't be surprised about bugs, usability or performance issues
> (please file issues or create threads in discussions!), but we have the goal to make it as
> user-friendly as we can feasibly do.

## What to expect?

+ Dependent types, including Π-types, Σ-types, indexed families, etc.
  You could write a [sized-vector type][gadt].
+ Set-level cubical type theory (XTT).
  + Demonstration of [quotient-inductive-inductive types][hiir],
    no forward declaration or mutual block needed!
    We infer the type checking order by how definitions use each other.
  + Proof of `funExt` in [paths.aya][funExt].
+ Pattern matching with first-match semantics.
  Checkout the [red-black tree][rbtree] (without deletion yet).
+ A JIT-compiler that translates Aya code to higher-order abstract syntax in Java.
  This makes the interpreter to run tree-sort 10x faster! See [benchmark code][tbtree-bench].
+ Overlapping and order-independent patterns. Very [useful][oop] in theorem proving.
+ A literate programming mode with inline code fragment support, inspired from Agda and [1lab].
  You may preview the features (in Chinese)
  [here](https://blog.kiva.moe/posts/intro-literate-aya.html).
+ Binary operators, with precedence specified by a partial ordering
  (instead of a number like in Haskell or Agda).
+ A fairly good termination checker.
  We adapted some code from Agda's implementation to accept more definitions such as the
  `testSwapAdd` example in [this file][foetus] (which are rejected by, e.g. Arend).

See also [use as a library](#use-as-a-library).

[GitHub Releases]: https://github.com/aya-prover/aya-dev/releases/tag/nightly-build
[1lab]: https://1lab.dev

## Contributing to Aya

See [HACKING.md](HACKING.md) for technical details,
[CONTRIBUTING.md](CONTRIBUTING.md) for social responsibilities,
and [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).

Questions are _always_ welcomed in [Discussion](https://github.com/aya-prover/aya-dev/discussions).
We will try our best to answer your questions. Please be nice to us!
We also welcome nitpicks on any user-interaction issues, especially on error reporting.
Let us know if you have any suggestions.

[actions]: https://github.com/aya-prover/aya-dev/actions/workflows/gradle-check.yaml/badge.svg
[codecov]: https://img.shields.io/codecov/c/github/aya-prover/aya-dev?logo=codecov&logoColor=white
[maven]: https://img.shields.io/maven-central/v/org.aya-prover/base?logo=gradle
[oop]: ../cli-impl/src/test/resources/shared/src/arith/nat/base.aya
[gadt]: ../cli-impl/src/test/resources/shared/src/data/vec/base.aya
[regularity]: ../cli-impl/src/test/resources/shared/src/paths.aya
[funExt]: ../cli-impl/src/test/resources/shared/src/paths.aya
[rbtree]: ../jit-compiler/src/test/resources/TreeSort.aya
[tbtree-bench]: ../jit-compiler/src/test/java/RedBlackTreeTest.java
[hiir]: https://www.aya-prover.org/blog/tt-in-tt-qiit.html
[foetus]: ../cli-impl/src/test/java/org/aya/test/fixtures/TerckError.java
[maven-repo]: https://repo1.maven.org/maven2/org/aya-prover

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

+ `[project name]` specifies the subproject of Aya you want to use,
  and the options are `pretty`, `base`, `cli-impl`, `parser`, etc.
  + The syntax definitions live in `syntax`.
  + The parser lives in `parser` (the generated parsing code) and `producer`
    (transformer from parse tree to concrete syntax tree).
  + The type checker lives in `base`.
  + The JIT compiler lives in `jit-compiler`.
  + The generalized pretty printing framework is in `pretty`.
  + The library system, literate mode, single-file type checker, and basic REPL are in `cli-impl`.
  + The generalized tree builder, generalized termination checker,
    and a bunch of other utilities (files, etc.) are in `tools`.
  + The generalized binary operator parser, generalized mutable graph are
    in `tools-kala` because they depend on a larger subset of the kala library.
  + The command and argument parsing framework is in `tools-repl`.
    It offers an implementation of jline3 parser based on Grammar-Kit and relevant facilities.
  + The literate-markdown related infrastructure is in `tools-md`.
    It offers [JetBrains/markdown] extensions for literate mode of any language with a highlighter.
+ `[latest version]` is what you see on this badge ![maven].

[JetBrains/markdown]: https://github.com/JetBrains/markdown
