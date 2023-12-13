# Early changelog

This file contains the changelog of the Aya language 0.x.

## 0.30

Upgraded from Java 19 to Java 21.

New features:

+ Improve `Doc` implementation to account for the nested structure for real
+ Improve stability in pattern matching conversion check, pretty print (thanks @dannypsnl)
+ Clean up something related to `Prop`
+ Introduce the "fake literate" language, which allows one to generate beautifully annotated code using Aya's literate backend with custom, naïve syntax highlighting
+ Literate now works with library system
+ Use Gradle version catalog to replace `deps.properties`
+ Introduce `ij-parsing-wrapper` and use it in the parsing code
+ Improve `let` handling
+ Improve "count usages" in language server
+ The new classifier is in `tools` now
+ Loads of build system improvements

Welcome [AliasQli](https://github.com/AliasQli)!

## 0.29.1

Released for the Anqur project.

+ Find usage in LSP now discards recursive references.
+ Some literate mode improvements.
+ Correctly desugar `let open`.
+ Extract classifier utilities into `tools`.

## 0.29

Major changes:

+ `struct` is replaced with a new, WIP `class` system (@ice1000).
+ `coe` is replaced with a hacked implementation of the Cartesian version (@ice1000).
+ Add literate mode support for library modules (@imkiva).
+ Literate-related code is now moved to a separate subproject (@HoshinoTented).

New features:

+ LSP now supports configurable rendering options.
+ Literate mode now handles math and tikz better.
+ Error messages & warnings are now available in literate output.
+ In literate mode, inline expressions can now refer to generalized variables.
+ Add aarch64 prebuilt binaries.

Major bug fixes:

+ A couple of bugs related to generating literate output.
+ REPL now merges contexts correctly.

Improvements:

+ Library updates (Coq style reflect).
+ Improve `let` expressions type checking.
+ Certain types are now computed lazily during type checking.
+ Parser no longer stores a list of token strings (and we compute the node string on-demand by using the ranges).
+ Our repackage of `ij-parsing-core` has an extracted `ij-util-text` module, which contains some simple utilities and string manipulation functions.

## 0.28

Internal refactorings:

+ Concrete syntax cleanup.
+ Upgraded Gradle to 8.0.
+ The module `cli` is split into `cli-impl` and `cli-console`.
+ We are now using a strongly typed version of module paths/names instead of list of strings.
+ Term visitors now take care of patterns.
+ The generalized `MCT` is now deleted.
+ The modifier parser now takes care of all modifiers.

New features:

+ Reimplementation of the pattern classifier. The old one turns out to have some very deeply hidden bugs,
  and it's uneasy to fix them, and is modified by @imkiva without well-understanding the code.
  The new one is much simpler and closer to the very old implementation (v0.5).
+ Support projection out of expressions that normalize to a constructor call.
+ Functions are now `Fn` instead of `Pi`, `fn` instead of `λ`. The old keywords are no longer reserved,
  so you can define functions called `Π` and `λ` now. This imitates Lean4 and Coq.
+ We have added initial support for a predicative termination checker.
  Right now it only works for path application, but we will support functions in general in the future.
+ Locally opening a module now works, use `let open`.
+ The literate mode now supports `aya-hidden` as a language, and it's hidden in the output.
+ The `inline` keyword is now moved to the front of the definition.
+ Support anonymous examples.
+ Aya now tries to unfold unrelated definitions in the termination checker.

Improvements:

+ Overhauled the module system to support diamond imports, `using`, etc.
+ The LaTeX backend is much better: support KaTeX mode, more symbols, fixed some old ones, etc.
+ Fixed some internal errors.
+ The faithful pretty printer now works with error-ed code, and it has better treatment for EOLs.
+ We've recompiled the website with the latest changes on syntax!
+ Type checking order now respects patterns. It's a miracle that the old code worked at all.
+ `data infix` will have precedence written before the constructors instead of after.

By the way, we are now working on a WIP branch that reimplements `record`s as `class`es!

## 0.27

Happy new year! New features and big changes:

+ Checksums are now available for all releases. You may use them to verify the integrity of the downloaded files.
+ Overall improvements to term-related error messages. There is a uniform way to display the normalized terms now.
+ `using` and `hiding` are reimplemented. Invalid `using` and `hiding` will now be reported as errors.
  This also allows future improvements to allow qualified identifiers in `using`.
+ Confluence (for both cubical HITs and overlapping patterns) check error messages are now more informative.
+ The semantics of `private` and `public` and `open` of data types are now more consistent and `private public open` no longer makes sense.
+ Comments are now highlighted in the pretty printed output.
+ Meta variables are now type checked in a more consistent way. This can eliminate a lot of possible bugs.
+ Let expressions are now available.
+ Improved the implementation of `inS` and `outS`. There is now type-directed reduction for `outS`.
+ The sort system is reimplemented. The PTS rules are written in [this document](/note/sort-system.md).
+ Parsing is now more robust than before. There will be more parsing errors reported and fewer crashes.

Internal refactorings:

+ `ExprTycker` is now split into a package of type checkers. `Unifier` and `StmtTycker` now extend part of them.
+ Lambdas in core are now untyped. The parameter types are expected to be obtainable from a given type.
+ There is a new constraint system for meta variables.
+ `PatTycker` is split into two parts for clauses' checking and pattern checking.
+ Added an implementation of dynamic forest in the `tools` module.
+ `Term` operations are now based on subtyping polymorphism instead of pattern matching.
+ `ExprTycker` are no longer reused.

## 0.26

New features and big changes:

+ The `lsp` module now provides specific code for language server protocol (à la Microsoft) support. The code analysis parts are moved to a module called `ide`.
+ The pretty printer is now aware of "binariness" of paths. Those paths will be printed as `a = b` instead of `[| i |] A ...`.
+ Support pushing constructor telescope. You may now write `| c : isSet D` instead of `| c (i j : I) { i := ... | j := ... }`.
+ The syntax for extension types is now changed to `[| i |] A { ... }` instead of `[| i |] A {| ... |}`.
+ The HTML code generated by the pretty printer now uses CSS classes instead of inline styles.
+ The pretty printer now supports markdown bullet & ordered lists.
+ The syntax for `forall` with implicits is now `∀ {i j}` which is simpler than `∀ {i} {j}`. This is inspired by Agda.
+ Error report now uses unicode characters for ASCII art.
+ Basic support for cubical subtypes.
+ Allow constructor qualification in patterns matching.
+ Reimplement `PrettyError` with `Doc` framework.
+ Module names are now highlighted.

Bug fixes:

+ REPL supports redefining primitives now -- [PR-800](https://github.com/aya-prover/aya-dev/pull/800) by our new contributor @SchrodingerZhu!
+ Aya is now unhappy with non-absurd patterns w/o bodies.
+ The type checking of constructors is now done in a whole instead of header & conditions separately. This also simplifies type checking order.
+ Improved pretty printing with page widths.
+ Unification is now aware of `dataArgs` of constructors.
+ Fix GraalVM native image configs.
+ The name "distill" is no longer used in favor of "prettier".

## 0.25

New features and big changes:

- Aya now has a new faithful pretty printer. Instead of generating strings from terms,
  it uses user's code and put attributes onto the tokens.
- The pretty printer has a markdown backend now.
- The HTML backend now escapes HTML entities.
- The "remark" system is rewritten as a literate mode with markdown + Aya code blocks + Aya inline code.
  The compiler can compile the Aya code into HTML and leave the markdown parts as-is.
  This feature is used to write Aya tutorials now.
- Further reorganization of the library candidate, improved the style guide, added
  ordinal numbers. The style guide now explains what to do if the same structure is
  used in multiple areas of mathematics.
- Module names are now highlighted.
- Allow quantifying lambdas over types (in a synthesis mode).
- Improved coercive subtyping from pi-path mixture to paths.
  From pi-path mixture to pi-path mixture is still not supported.
- The jlink release now has a `lib` folder containing the library candidate.
  The executables from the JDK are now moved to the `jre` folder, so that
  the `bin` folder only contains Aya scripts.
- Allow flexible telescope. If a function returns a pi type, the parameters
  can be matched in the patterns.
  A bind pattern at the end of the pattern matching can be moved into the
  bodies as a lambda parameter.
- `ErasedTerm` is now deleted. Instead, we use `Term` directly.

Bug fixes and improvements:

- The coverage checker now correct handles unmatchable types.
- Reimplemented `as` patterns.
- When patterns are incorrect, the body is no longer checked.
- Fixed a serious bug on HIT constructor scoping.
- Adapted the `:codify` command with the latest core terms.
- The `WithCore::theCore` are frozen after the type checking.

## 0.24

New features and big changes:

- New higher inductive type syntax is finally merged. Conditions are completely gone.
- Reorganization of the library candidate, together with a draft style guide. You have root packages like `Data`, `Sets`, `Spaces`, `Logic`, etc.
- Comment syntax is now `//` instead of `--`.
- `|` is no longer allowed at the beginning of identifiers. This makes `|suc a => ...` work, but `||` is no longer a valid identifier. On the other hand, `<|>` is still valid.
- Aya now tries to guess a qualified name on unresolved references ("did you mean ...").
- Added `group` field in `aya.json`.
- `let` expressions.
- A pure syntax highlighter. This is a preparation for a new pretty printer.

Bug fixes:

- Indexed families "owner tele" scoping is now correctly handled.
- Induction-recursion is now correctly handled.
- `Pat.Meta` in indexed families is correctly handled.
- VS Code language server now recognizes new files in subfolders.
- `private` declarations are now correctly parsed.
- Improved error handling of `fixl` in the parser.
- No longer put ridiculous amounts of duplicate local variables.
- REPL now correctly handles literal patterns.
- Do notations source positions are not correctly generated.

Improvements:

- Improved color scheme handling in the REPL.
- The jlink installation is much better now: there will be two dirs: `bin` with Aya scripts and `jre` with the JRE. This will prevent Aya in the Path from polluting the `java` executable.
- The parser now makes good use of `meta` rules provided by Grammar-Kit.
- Some GraalVM configuration is now done in the build script.
- Refactored `ColorScheme` and `StyleFamily` in REPL.

## 0.23

The core and concrete syntax are renamed. The core syntax now have a flat class structure.

Add `--style` option for pretty printing color scheme, build the lexer automatically,
change `coe` syntax to projection, list representation in the core, fix bind serialization,
fix a couple of bugs, eta-expand in path type checking, introduce `ErasedTerm` in core for
propositions, overhaul unification to generate fresh names, `coe` reduction for n-ary sigma,
reimplement `as` patterns (remove `Expr` subst, introduce internal `let` bindings),
fix constructor of indexed families (`ownerTele` problems), fix pattern matcher, introduce
a new command in REPL `:codify` to generate Aya code from a function name, fix sigma type
elaboration, match expressions in concrete and core, fix nested meta patteriables, refactor
`Pattern` to `Arg<Pattern>` so they no longer have `explicit` method, overhaul literals
(split unsolved literals and solved literals), allow `↑↑ f`.

## 0.22

Improved the shape matcher with normalization, improved performance of `MCT` traversal,
elementary support for list literals (based on resolver, not shape matcher),
fixed a serious bug of pattern classifier with literals and unfolding,
moved the desugaring of `do` and idiom brackets to the desugarer (was in producer before),
migrated from ANTLR4 to GrammerKit (even fewer build dependencies -- no longer need icu4j),
improved tracing, improved pretty printing of `do` and idiom brackets,
updated to Java 19, removed commas from bind clauses, flipped from `isLeft`
to `isOne`, renamed some classes in cubical library.

Welcome [`@HoshinoTented`](https://github.com/HoshinoTented)!

Speaking of upstream dependencies, we have much fewer dependencies now.
The badass-jlink plugin now runs blazingly fast because now we have only a few non-jpms jars.
The only non-jpms dependency is jline3. Oh yes!

The GrammarKit parser has better error recovery than ANTLR4,
but right now we can't use the build script to generate the parser.
This slightly bloats the git repo, but it's not a big deal.
We are planning on generating the lexer in the build script.

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
