# Agda's sort system

Comments:

+ `ISet` behaves like `Set`, but functions into `Type` land in `Type`.
+ Supports `Inf`, `LockUniv`, and `SizeUniv`.
+ `Prop` is predicative because of the termination checker.
+ Reference: https://github.com/agda/agda/issues/5667
+ `SSet` is paraphrased as `Set` below to be consistent with Aya.

Related rules on the $\Pi$-type:

| Input sort      | Output sort  | Result           |
|-----------------|--------------|------------------|
| `Prop`/`Type` a | `Type` b     | `Type` a ∨ b     |
| `ISet`          | `Type` b     | `Type` b         |
| `Prop`/`Type` a | `Prop` b     | `Prop` a ∨ b     |
| `?` a           | `?` b (same) | `?` a ∨ b (same) |
| `?` a           | `¿` b        | `Set` a ∨ b      |

# Notes

+ Codomain's sort:
  + If result is not `Set`: just `<=` the result sort and should have the same kind.
  + If result is `Set`, then can be any kind with suitable levels.
+ Domain's sort:
  + If result is `Prop`/`Type`, then can be `Prop`/`Type` with suitable levels.
  + If result is `Type`, then can be `ISet`.
  + If result is `Set`, then can be anything with suitable levels.

# Regarding impredicativity

Gaëtan Gilbert, Jesper Cockx, Matthieu Sozeau, and Nicolas Tabareau. _Definitional Proof-Irrelevance without
K_. [PDF](https://hal.inria.fr/hal-01859964v2/document)

Page 1:10 mentions:

> _An impredicative variant_.
> We will see in section 4 that we can also allow
> an impredicative version of `sProp`, which amounts
> to just ignoring the indices on `sProp` throughout.
