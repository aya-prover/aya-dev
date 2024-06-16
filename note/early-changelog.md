# Early changelog

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
