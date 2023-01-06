# Agda's sort system

Comments:

+ `ISet` behaves like `Set`, but functions into `Type` land in `Type`.
+ Supports `Inf`, `LockUniv`, and `SizeUniv`.
+ `Prop` is predicative because of the termination checker.
+ Reference: https://github.com/agda/agda/issues/5667

Related rules:

| Input        | Output       | Sort             |
|--------------|--------------|------------------|
| `Prop` a     | `Type` b     | `Type` a ∨ b     |
| `Type` a     | `Prop` b     | `Prop` a ∨ b     |
| `? a`        | `? b` (same) | `?` a ∨ b (same) |
| not `Prop` a | not `Prop` b | `Set` a ∨ b      |

# Regarding impredicativity

Gaëtan Gilbert, Jesper Cockx, Matthieu Sozeau, and Nicolas Tabareau. _Definitional Proof-Irrelevance withouth K_. [PDF](https://hal.inria.fr/hal-01859964v2/document)

Page 1:10 mentions:

> _An impredicative variant_.
> We will see in section 4 that we can also allow
> an impredicative version of `sProp`, which amounts
> to just ignoring the indices on `sProp` throughout.
