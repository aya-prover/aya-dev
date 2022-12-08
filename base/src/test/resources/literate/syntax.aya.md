```aya
open data Nat | O | S Nat
open data List (A : Type) | nil | infixl :< A (List A)
open data Vec (n : Nat) (A : Type)
| O, A => vnil
| S n, A => vcons A (Vec n A)

def dependent (x : Nat) : Type
| x as x' => Nat

def justId : Pi (a b : Nat) -> Sig (e : dependent a) ** (dependent e) => 
  \ a b => let
    | c := a
    | d := b
    in (c, d)
```
