```aya
open data Nat | O | S Nat
open data List (A : Type) | nil | infixl :< A (List A)
open data Vec (n : Nat) (A : Type)
| O, A => vnil
| S n, A => vcons A (Vec n A)

def dependent (x : Nat) : Type
| x as x' => Nat

// yes, just id
def justId : Fn (a b : Nat) -> Sig (e : dependent a) ** (dependent e) => 
  \ a b => let
    | c := a
    | d := b
    in (c, d)
```

For the following `code block`, you'll never see it:

```aya-hidden
example def foo => justId
```

This is a math block, and it is saying $\frac{1}{2} = \frac{1}{2}$:

$$
\begin{align*}
  \frac{1}{2} &= \frac{1}{2} \\
  \frac{1}{2} &= \frac{1}{2} \\
  \frac{1}{2} &= \frac{1}{2} \\
\end{align*}
$$
