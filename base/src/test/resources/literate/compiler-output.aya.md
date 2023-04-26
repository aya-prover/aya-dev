```aya-hidden
prim I prim coe
prim intervalInv
prim intervalMax
prim intervalMin
inline def ~ => intervalInv
variable A B : Type
def Path (A : I -> Type) (a : A 0) (b : A 1)
   => [| i |] A i { i := b | ~ i := a }
def infix = {A : Type} => Path (fn x => A)
def idp {a : A} : a = a => fn i => a
def pmap (f : A -> B) {a b : A} (p : a = b) : f a = f b => \i => f (p i)

def isProp (A : Type) => ∀ (a b : A) -> a = b
def isSet (A : Type) : Type => ∀ (a b : A) -> isProp (a = b)
```

### Goal info

The natural number ($\mathbb{N}$):

```aya
open data Nat | zero | suc Nat
overlap def infixl + : Nat -> Nat -> Nat
  | 0, a => a
  | a, 0 => a
  | suc a, b => suc (a + b)
  | a, suc b => suc (a + b)
  tighter =
```

Try to prove the following goal:

```aya
overlap def +-comm (a b : Nat) : a + b = b + a
  | 0, a => idp
  | a, 0 => idp
  | suc a, b => pmap suc (+-comm _ {??})
  | a, suc b => pmap suc (+-comm b a)
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

$$
\cfrac{114}{514}
$$
