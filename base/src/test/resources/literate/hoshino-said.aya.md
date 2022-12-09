# Hoshino Tented

```aya
variable Hoshino : Type
```

So, `Hoshino -> Hoshino`{} is a thing.

```aya
open data Nat | O | S Nat
prim I prim intervalInv
def inline ~ => intervalInv
def infix = {A : Type} (a b : A) => [| i |] A { ~ i := a | i := b }
```

Example: `1 = 1`{mode=NF}.
