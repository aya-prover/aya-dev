# Agda's sort system

Comments:

+ `ISet` behaves like `Set`, but functions into `Type` land in `Type`.
+ Supports `Inf`, `LockUniv`, and `SizeUniv`.
+ `Prop` is predicative because of the termination checker.
+ Reference: https://github.com/agda/agda/issues/5667
+ `SSet` is paraphrased as `Set` below to be consistent with Aya.

Related rules on the $\Pi$-type:

| Input sort | Output sort  | Sort             |
|------------|--------------|------------------|
| `Prop` a   | `Type` b     | `Type` a ∨ b     |
| `Type` a   | `Prop` b     | `Prop` a ∨ b     |
| `?` a      | `?` b (same) | `?` a ∨ b (same) |
| `?` a      | `¿` b        | `Set` a ∨ b      |

# Notes

+ If sort is not `Set`, then cod's sort is just `<=` the result sort and should have the same kind.
+ If sort is `Set`, then cod's sort is `<=` the result sort and can be any kind.

# Regarding impredicativity

Gaëtan Gilbert, Jesper Cockx, Matthieu Sozeau, and Nicolas Tabareau. _Definitional Proof-Irrelevance without
K_. [PDF](https://hal.inria.fr/hal-01859964v2/document)

Page 1:10 mentions:

> _An impredicative variant_.
> We will see in section 4 that we can also allow
> an impredicative version of `sProp`, which amounts
> to just ignoring the indices on `sProp` throughout.
