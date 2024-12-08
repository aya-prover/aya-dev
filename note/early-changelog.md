# Early changelog

## v0.35

Breaking changes:

- Σ types are now binary rather than n-ary, and compilation is correctly implemented now

Major new features:

- Pattern matching expressions are now supported (since the locally nameless rewrite), in its most basic form (no dependent pattern matching yet).
- Add `--no-prelude` flag to disable loading the prelude.
- JIT-compiled binaries are used right after being generated, even in the same library.
- Projection expressions are now type-checked (yes, we forgor).

Minor new features & user-visible bug fixes:

- When JIT-compiling a constant function, substitution of that function will be a constant function too.
- Now clauses and return types of functions are correctly pretty printed, no more de Bruijn indices shown to the user.
- Self-recursive function signatures are now correctly reported as errors.
- Cubical `coe` on pi, sigma, un-parameterized inductive types, and sorts are correctly normalized.
- Fix a long-standing bug of binary application term normalization.
- Libraries with user goals in them will not be JIT-compiled.
- The standard library has sum type and a couple of new lemmas on vectors now.
- A bunch of error reports are implemented.
- The "unsolved meta" error now is reported only once per meta.
- Improved type checker to handle more meta-typed terms.

Internal changes:

- No longer replace `MatchException` with `RuntimeException` because we are on Java 21 now.
- Update the Nix flake file.
- Code from jit-class branch is mostly cherry-picked to main.
- Pi and Sigma core terms are now combined and lots of code is shared.
- The APIs for implementing `hcom` (interval equality) is ready.

## v0.34

Semi-breaking changes:

- Revise the behavior of ambiguous exports -- we report errors on ambiguous exports now.
  Before, Aya will export all candidates, and error will occur only when you try to use one.
  But there is no way to resolve such ambiguity, so we now report errors earlier
- Kotlin stdlib is now in the binary dependencies of Aya due to transitive dependency
- Upgrade many dependencies

New features:

- Add `:debug-show-shapes` in REPL to show currently recognized shapes in the REPL context
- Preliminary support for classes, adding type checking for class declarations,
  class specializations, JIT-compilation, etc., but not yet for class instance resolution
- Add `:info` (alias: `:i`) command in REPL to show information about a name, similar to GHCi

Improvements to existing features including bug fixes:

- Correctly display emojis in Windows Terminal
- Improve error messages for unsolved metas thanks to @utensil and @HoshinoTented
- The jlink release will include compiled version of the std library
- Coercive subtyping now works from path to functions
- Better type checking for list literals with the presence of ambiguity
- Correctly pretty print `RuleReducer.Con`

## v0.33

We've started working on classes.

New features:

- Implement flex-flex unification
- Thorsten Altenkirch's QIIT is now correctly type-checked
- Now REPL and literate can in most cases display types of variables under
  the `NULL` normalization mode.
- The type of path constructors are now correctly displayed as equalities
- A more clever, context-based name generation for fresh variables
- JIT compiled code is substantially faster, we lazily compute the default value
- Share some instances in JIT compiled code
- `prelude` is now a thing, it will load basic stuff like naturals
- Quotients and `FMSet` in the library

Bug fixes:

- Modifiers of `def` are now serialized
- Indexed constructor return types are now correctly indexed
- Empty inductive types are now correctly treated in various places
- Submodules JIT now works

## v0.32

Breaking changes:

- Renamed the `data` keyword to `inductive`

New features:

- Implemented some missing features from previous Aya, including:
  - Pushing telescope for fn and constructors, not for data yet
- `partial` definitions will not be termination-checked and will not be unfolded,
  definitions that fail termination check will be marked as `partial`
- `ListTerm` now uses a persistent list, which should be more efficient

Bug fixes:

- Countless bugs related to `ownerArgs`/`dataArgs` issues, de Bruijn indices in
  patterns, 
- Resolving for `elim` now happens in resolving, not parsing
- Literate mode is not fully compatible with the old version
- Quotient inductive type fixes
- Some completely misguided error messages are now fixed
