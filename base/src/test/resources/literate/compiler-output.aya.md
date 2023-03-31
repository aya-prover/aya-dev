```aya-hidden
prim I prim coe prim coeFill
prim intervalInv
prim intervalMax
prim intervalMin
inline def ~ => intervalInv
variable A B : Type
def Path (A : I -> Type) (a : A 0) (b : A 1)
   => [| i |] A i { i := b | ~ i := a }
def infix = {A : Type} => Path (fn x => A)
def idp {a : A} : a = a => fn i => a

def isProp (A : Type) => ∀ (a b : A) -> a = b
def isSet (A : Type) : Type => ∀ (a b : A) -> isProp (a = b)
```

### Symbol shadow warnings

```aya
def this-is-a-symbol => Type

module Yes {
  def this-is-a-symbol => Type
}
```

### Early type aliases

```aya
open data SetTrunc (A : Type)
| inj A
| trunc : isSet (SetTrunc A)
```

```aya
def FMSet (A : Type) : Type => SetTrunc (FMSetRaw A)
```

```aya
open data FMSetRaw (A : Type)
| nil
| infixr :< A (FMSet A)
  tighter =
| comm (x y : A) (xs : FMSet A) : x :< inj (y :< xs) = y :< inj (x :< xs)
```
