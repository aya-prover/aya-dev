# Early changelog

## v0.33

New features:

- Implement flex-flex unification
- Thorsten Altenkirch's QIIT is now correctly type-checked

Bug fixes:

- Modifiers of `def` are now serialized
- Indexed constructor return types are now correctly indexed

## v0.32

Breaking changes:

- Renamed the `data` keyword to `inductive`

New features:

- Implemented some missing features from previous Aya, including:
  - Pushing telescope for fn and constructors, not for data yet
  - 
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
