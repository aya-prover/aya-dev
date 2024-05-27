```aya-hidden
prim I : ISet
prim Path (A : I -> Type) (a : A 0) (b : A 1) : Type
prim coe
variable A : Type
def infix = (a b : A) => Path (\i => A) a b
def idp {a : A} : a = a => \i => a
def pmap {A B : Type} {a b : A} (f : A -> B) (p : a = b) : f a = f b => \i => f (p i)
def funExt (A B : Type) (f g : A -> B) (p : Fn (a : A) -> f a = g a) : f = g =>
  \ i => \ a => p a i
def pinv {a b : A} (p : a = b) : b = a => coe 0 1 (\i => p i = a) idp
```

### Goal info

The natural number ($\mathbb{N}$):

```aya
open data Nat | zero | suc Nat
overlap def infixl + Nat Nat : Nat
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
| suc a, b => pmap (\x => suc x) (+-comm _ {??})
| a, suc b => pmap (\x => suc x) (+-comm b a)
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
//| trunc : isSet (SetTrunc A)
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
