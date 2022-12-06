```aya
open data ImNat | O | S {ImNat}

def infixl + (m n : ImNat) : ImNat
| O, n => n
| S {m}, n => S {m + n}

def moreComplex {x : ImNat} (y : ImNat) : ImNat
| {O} as x, O as y => x + y
| {O as x}, S {_} as y => x + y
| {S {x' as x}}, O as y => S {x} + y
| {S {x'} as x}, S {y' as y} => x + S {y}
```
