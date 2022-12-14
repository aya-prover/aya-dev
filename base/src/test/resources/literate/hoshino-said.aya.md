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
