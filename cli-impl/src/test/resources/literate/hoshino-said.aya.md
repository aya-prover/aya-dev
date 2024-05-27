# Hoshino Tented

```aya
variable Hoshino : Type
```

So, `Hoshino -> Hoshino`{} is a thing.

```aya
data Nat | O | S Nat
prim I : ISet
prim Path (A : I -> Type) (a : A 0) (b : A 1) : Type
prim coe
variable A : Type
def infix = (a b : A) => Path (\i => A) a b
def refl {a : A} : a = a => \i => a
def funExt (A B : Type) (f g : A -> B) (p : Fn (a : A) -> f a = g a) : f = g =>
  \ i => \ a => p a i
def pinv {a b : A} (p : a = b) : b = a => coe 0 1 (\i => p i = a) refl
```

Example: `1 = 1`{mode=NF}.

+ Aya module names are separated by `::`, not `.`.
+ Aya infers the module names automagically, using the same rule as of Haskell.
+ Aya imports (`import X`) are qualified by default, use `open import X` to unqualify.
  This is short for `import X` followed by `open X`.
+ Aya supports restricted import `open import X using (x)` 
  (this only imports `x` from `X`) you may also use `open import X hiding (x)` to import everything except `x` from `X`.
+ Aya supports renamed import `open import X using (x as y)` and the meaning is obvious.
+ To re-export, use a `public open`.

This is an image:

![shameimaru](https://www.pixiv.net/artworks/88666068)
