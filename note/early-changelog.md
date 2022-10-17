# Early changelog

This file contains the changelog of the Aya language 0.x.

## 0.22

## 0.21

Considered size-change in termination checker and reimplemented call graph completion,
fixed a termination checker bug and supported interleaved arguments on recursion,
disabled commit-check on main, updated bors message to lowercase, updated guest,
added IntelliJ theme, added `fixl`, `fixr`, distinguished unary from binary, removed `bind` keyword,
made `I` and `Partial` primitives instead of keywords, separated delta and beta reduction, added `DeltaExpander` and `BetaExpander`,
added Pat inline with context, replaced cofibration syntax with interval expressions,
reorganized problems by their type, removed more visitors, cleaned up unused classes & methods,
added some utilities to `Tycker`, added workaround for a lsp4j bug, removed planned pattern synonym,
enhanced generalized path type (fixed many bugs), introduced a sort system, overhauled the library
with path type, deleted the old coe function from Arend, deleted records-with-conditions,
supported explicitly raising errors in reporter (hence Aya no longer says 🐄🍺 when it fails),
upgraded build JDK to JDK 19, fixed concrete patterns storing incorrect types (contains `MetaPat`),
split `DefEq` into `TermComparator` and `Unifier`, fixed some pretty printing bugs.

Replaced lsp4j with javacs (a java language server using javac API)'s self-contained protocol
implementation, with JPMS support added by us.

The sort system consists of:

+ An impredicative universe of strict propositions `Prop`
+ A predicative universe of external strict sets `Set` (in the sense of 2ltt)
+ A universe for the interval type `ISet` similar to Agda's `IUniv`

## 0.20

This is going to be the last release before we switch to IntelliJ parsing infrastructure.

Bumped many dependencies, added `universe` for checking types, support
changing precedences of imported operators, fixed LSP crashes,
fixed `./gradlew jlink` does not respect dependency updates,
improved tracing, refactored code based on IDEA inspections.

Initial support for cubical type theory (based on [Guest0x0]),
with partial elements and the generalized path type ("extension type").

[Guest0x0]: https://github.com/ice1000/Guest0x0

## 0.19

Updated to jdk 18, rewrote major version of classes to 61,
used `graalvm/setup-graalvm@v1` in github actions script, updated kala to 0.44.0,
shrank strip preview messages, improved publishing and versioning, published snapshots automatically,
updated Tesla's name in copyright and developers list,
rewrote Serializer with pattern matching,
added repl, Serialization, Distiller, Test of literals of java integer backed literals,
added string literal with primitive concatenation,
normalizer re-committed lambda application, substed before normalizing,
implemented do notations, array syntax and list comprehension, and idiom brackets with parse time desugar,
moved `AyaDocile` to base, optimized `PatClassifier` for literals, especially for big integer literals,
upgrade lsp4j to 0.14 and added some new features to lsp, added inlay type hints for bind patterns, definition usages count, code folding and search everywhere for symbols in VSCode, improved JPMS and build script,
improved classable api and flatted concrete definitions, changed lsp to a pure analyzer,
added a new check-only mode option for libraries, made some lsp api public,
fixed false-positive first match domination warning by putting bind patterns in separate leaf nodes,
fixed primitive reload and polished some error messages, reduced expensive graph operations and needless lsp protocol calls,
fixed a problem where distilling `{? expr ?}` may cause NPE by passing `accessibleLocal`,
implemented new `Term` traversal api, disallowed universe lifting for local bindings.

Deleted core visitors (and all the other visitors)!

## 0.18

Implemented full normalization with TermView,
moved primitive related states from global variables to TyckState,
fixed "no rule" reporting and handled inference of error term,
thrown `InternalException` instead of `IllegalxxxException`, checked the hole's solution in the little typer,
fixed a tyck order bug `def a a`, detected circular signature dependency, fixed library compiler's error reporting,
built GraalVM Native images and fixed github actions, used adaptive-cli style outputting to a terminal,
fixed bugs on repl and added tests, implemented nat code shape matcher as a part of the implementation of java integer backed literals,
added api for classable def, improved pretty and syntax for named applications.

Deleted concrete visitors (including for decl and for expr)!!

+ Changes to the language:
  + `I` is now a keyword instead of prim and `left` and `right` are now `0` and `1`.
  + Named application is changed from `=>` to `:=`
  + `\/` and `/\` are reserved but unused yet

## 0.17

Moved some ANTLR4 helpers and MCT to `tools-repl`/`tools`, fixed REPL parsing and color styles,
introduced some lazy term/expr pattern matching frameworks which will eventually replace visitors,
migrated some visitors to this API.

## 0.16

Supported `import as` and renaming open and renaming as infix,
added `Primitives.aya` and more tests, removed the api module, supported qualified projection on struct,
supported `open struct`, enhanced projection error message, allowed empty `new` block,
supported mutual recursion on constructors and fields, restored `LittleTyper`,
fixed many bugs on simple functions and structs and pattern inference and eta contraction,
report error on missing pattern for non-splittable types,
improved `Signatured::toString`, upgraded gradle and codecov reports,
highlighted `NewExpr` and resolved position in `NewExpr`'s fields and bindings in the language server,
stored field resolving result in `NewExpr`, ensured pi body normalized.

+ Added the support for mutual recursion on constructors and fields
  + Header and body orders are generated at the same time, which is done by recording the part (header or body) where the resolver is working on.
  + Constructors and fields are treated as top-level definitions during resolving and tyck order inference.
  + Expr Resolver now has a hierarchical structure since we will treat constructors' and fields' dependencies as the body dependencies of the corresponding data and struct.
+ Overhauled the level system
  + remove universe polymorphism and its things such as `lmax`, `lsuc` in concrete and surface
  + implement McBride universe and its liftings
+ Resolved projection early
  + Struct fields are resolved in the resolver instead of the type checker.
  + Structs can be `open`ed like `data`s.
  + Fields can be projected by qualified names like `p.Paths::at`


## 0.15

Supported command line module path and load libraries, set `resolveInfo` in module loader,
created `GenericAyaParser` as an interface for `AyaProducer` where the implementation
becomes `AyaParserImpl` (so we can decouple `parser` with `base`), used adaptive terminal
style in CLI, fix jlink builds, added more unit tests, refactored unify (see below),
generalized `LocalCtx` over implementation of the variable mapping,
improved `visitDataCall` on normalization, normalize when building subst for
constructors, supported `"` in prompts, renamed `impossible` with `()`, improved
code in lib, , nuked `BinOpCollector`, deleted `Pat::type`, `Tuple::type`, `Prim::type`,
`Absurd::type`, skip `DefVar`s from skipped modules,
rewrote distillers with pattern matching.

+ Built the foundation of termination checking (thanks to @imkiva).
+ New features in the language server:
  + Show type on hover, many UX improvements on error messages.
  + Handle file creation and deletion events.
  + Search definition in the whole library.
  + Added goto def. for module and import commands.
  + Highlight occurrences based on resolution.
+ Tools: distinguish `suc` and `sucMut` in mutable graph.
+ Unification improvements:
  + Avoid creating new local variables, which might be introduced into the solutions.
  + `freezeHoles` in meta solutions to avoid misleading scope-errors.
  + Delay solution on flex-flex cases where there might be ill-scoped solutions.
+ Changes to the test library:
  + Refactored many things.
  + Conversion between `List` and `Vec`.
  + Renamed `cons` to `:<`.
  + Added cubical "square".

There are also many changes in the WIP branches.

## 0.14

Upgraded some dependencies, start using our repackaged commonmark-java with jpms,
added several error reports, implemented the library system and replaced the success
test cases with a library test suite, extracted a `common` library as a prototype of
a standard library, extracted `tools-repl` subproject with the REPL command system
generalized, added prim id to `PrimCall`, added `--verbosity` flag to control the
least severe level of errors reported, removed `RefTerm::type` (and we plan to remove
the types of core patterns too) (this means we now use a lot of `LocalCtx` instances,
even in every `TyckState.Eqn`), removed `LittleTyper` because we seem to be fine even
without it (we have sufficient universe level equations), refactored the pattern matching
elaboration to be checking _all_ patterns at once and _then_ check all bodies at once,
refactored the classifier to generate a tree of classifications (instead of a flattened list),
allow mutual recursion in conditions, start using the user-configured pretty printing options
in the CLI pretty printer, insert more obvious implicit arguments.

The library system makes use of the serialization framework
and supports file-level incremental type checking.
The language server is updated to work with the library system.

## 0.13

Upgraded gradle, resolve pattern matching in the resolver instead of the type checker,
moved some code (source locations and generalized tree builder) to `tools`,
generalized the binop parser into `tools`, implemented binop in patterns,
several bug fixes of the distiller, actually use the `opaque` (renamed from `erased`)
and `inline` modifiers in unfolder, improved the parser for literate inline codeblocks,
disable overlapping patterns in function definitions by default (still enabled for conditions),
use first-match semantics on non-overlapping patterns, enabled overlapping patterns with `overlap`
modifier, improved index unification, added support for forced patterns (see [index-unification.md]),
replaced a lot of visitors with pattern matching, inline `as` in patterns during type checking,
added experimental `variable` keyword which is similar to the one in Agda (it lacks several
desirable features such as referring to a generalized variable from a generalized variable),
use `ErrorExpr` instead of `null` for unspecified result type of primitive definitions.

The most notable improvement would be the generalized binary operator parser in `tools`.
It should be _very useful_ to PL implementers using Java 17.

[index-unification.md]: index-unification.md

## 0.12

Actually implemented the inference of type checking ordering,
extracted some tools from `base` to `tools`, allow emission of `Type 0`
in data declarations, suppressed some unnecessary errors, improved
'unresolved meta' error message with a description of their location,
enhanced pretty printing (for `new` expressions, binary operators,
reduced unnecessary parentheses and added necessary parentheses,
eta-contract def-calls), added error message for unknown fields,
make the latex backend automatically insert `\noindent`,
removed jimgui-based tracer (moved to test) for smaller fatJars,
fixed no-arg constructor pattern matching, improved coverage checker
(fixed a divergence case, added fuel for impossible cases elimination),
fixed pattern checker when there are more implicit parameters omitted
in the patterns, added `compareApprox` in `compareUntyped`,
removed `abusing` syntax, refactor `bind` syntax, use the REPL for
pretty-printing options configuration.

Publish the `lsp` module.

## 0.11

Enhanced repl: completion, command system, command parser based on antlr,
tests, cd/pwd/load/etc. commands, redefinitions, highlighting, etc.

Tycking now stores state (term/level equations, solved metas)
in `TyckState` so in the future we may save/restore it.

Some LSP bug fixes, a new 'domination' warning suggested by [@nobodxbodon],
avoid tycking bodies when patterns are malformed, finished all easy
pattern matching to visitor replacements, enhance binop parser with section support,
generate Aya syntax highlight YML during build, Kala-relevant refactoring.

[@nobodxbodon]: https://github.com/nobodxbodon

## 0.10

Performance improvements¸ prototypical repl, binary operator
enhancements, relicense as MIT, substitution bug fixes, minor level
solver improvements, code of conduct & contribution guidelines.

Internal refactoring: `Problem::describe` is now parameterized by
`DistillationOptions`, remove infinity level in universe polymorphism

## 0.9

Some bug fixes, eta rule in pattern unification and `new` components,
renaming in concall and fieldcall, enhance level solver and level
substitution and level syntax, refactor some visitors into pattern
matching, fix comment syntax, enhanced coverage checker with automatic
detection of impossible patterns, concrete expression refactoring,
goal error message enhancements.

## 0.8.3

Initial (de)serialization, some implicits/levels/normalization/unification
relevant bug fixes, removed builtin hlevel, improved error report
(some metas are now inlined), migrated to java 17, replaced some
visitors with pattern matching, tycker is finally bidirectional,
removed tgbot subproject.

## 0.8.2

Some cosmetic improvements to Aya, such as literature mode,
type theoretical enhancements, test refactorings, etc.

## 0.8.1

Pretty printing (including the framework) enhancements,
level solver enhancements, publish to mavenCentral.

## 0.8

This is a quick release after 0.7,
with some improvements on implemented features.

## 0.7

Goal: everything else we need to prepare for being used by other people.

Roadmap: TDD -> library system -> user goals and VSCode LSP ->
packaging Aya as an application that can be installed on a Java-free
machine -> dependency management -> ...

Work done: insertion of implicit at tail, make `RefTerm` typed,
remove termdsl test framework, implement level hierarchy, its inference
and a solver properly, initial `GetTypeVisitor`, make lsp work on
Windows, simplify defeq, multi-location error report, fat jar,
initial binop, unify `new` terms, jlink, remove backslashes in keywords,
delayed error report, chained projection.

## 0.6

Primitive definitions, intervals and Arend coercion, confluence for
conditions, proper absurd patterns, proper holes and pattern unification,
new expressions for records (support a very naive binding technique),
records with conditions, LaTeX backend, internal refactoring.

## 0.5

Coverage and confluence check, prepare for local modules, records
(incomplete), indexed types, styled Doc with HTML backend,
simple unification, qqbot as an optional subproject, upgrade to Java 16 and Gradle 7.0-RC-1,
tons of bug fixes including `LocalVar` comparison bug and term comparison bug.

## 0.4

Definition by pattern matching, conditions, removed `\elim` keyword,
telegram bot, internal refactorings (most notably patterns and decls and defs).

## 0.3

Inductive types resolve/tycking, fncall, improved CLI, tracing for tycking
(both markdown and imgui based visualization), license changed to GPLv3,
desugar refactoring, file-based test framework, tons of bug fixes,
rename mzi -> aya.

## 0.2

Module system, declaration resolution, definition tycking,
simple CLI that just works, simple pattern unification.

## 0.1

Expression tycking, simple DTLC+pi+sigma, expression resolution,
simple conversion check.
