# Locally Nameless Aya draft

We rewrite Aya using locally nameless with the following additional foci:

## Already finished, internal
* _Better modularity_: decomposed `base` into `syntax`, `producer`, and `base`
* _Better concrete syntax tree_, decompose source info from `Expr`
* _Only do weak-head normalization in type checking_, implement the full normalizer based on top of it instead of having a single normalizer parametrized by how much you want to normalize
* Instead of using inheritance, we (actually @HoshinoTented) use a design pattern to imitate type classes to organize type checking monad
* Less test-only APIs in the type checker since we're more confident now
* Redesign the testing infrastructure, group the failing cases for a similar reason together
* Get rid of trace builders and "codifiers" because the developers never used them anyway except me for a few times
* Run internal tests using the "Orga" type checker (non-stopping & deal with mutual recursion correctly) instead of the silly sequential type checker

## Already finished, user-visible
* _Mutual recursion inference_ now work differently, smaller SCCs in the dependency graph, rely more on the order that users write the definitions
* Reimplement _serialization with JIT-compiled_ code for better efficiency, actually make use of the benefits of a VM-based host language
* Replace extension types with `PathP` since we no longer have interesting higher homotopies
* Replace boundaries in constructors with an equality in the return type
* Implement _the `elim` keyword in Arend_, so much less pain when dealing with long pattern matching functions
* _Remove first-class implicit arguments_ like in Agda/Lean, replace with the design similar to Coq. Pi types are only parametrized by the domain type and codomain closure
* Improve pattern matching coverage checking error report

## To be done, planned in this draft
* Serialization of modules, with proper mangling and deserialization
* _Library system_, with a small standard library

## To be done, not planned in this draft
* Implement _boundary separation_, `hcomp`, and correct computation of `coe` on type formers
* Redesign classes, make `do` and idiom brackets based on classes
* _Java interop_: tactics, etc.

Since late 2023, I was blessed with the privilege to talk to a number of students of Professor Avigad and learned a lot about Lean and some set-theoretic proof assistants, including Mizar and Metamath Zero. This is eye-opening since I only know Agda/Arend and was too much into the idea of univalent foundation of mathematics (instead of having HoTT as an internal language of higher topoi). This motivated the transition to a set-level type theory with good handling of propositional equality. I will try to weave my new understanding and thinking into this brand-new version of Aya. I am really grateful for @HoshinoTented for helping me out on this project.

Once we're done with everything that exists in the original repo, we will create a huge pull request signed by everyone contributed to this prototype and work with the aya-dev repo instead. The version number will be `0.x` still until we've figured out Java interop (aka tactics).
