Some preload definitions:

```aya
open data Nat | zero | suc Nat
open data Unit | unit
```

Lately, **Daylily** told me an interesting problem about Aya's _pattern unification_.
Consider this function (some parameters renamed from the test suites):

```aya
def wow-fun {U : Type} {T : U -> Type} (A B : U) (x : T A) (y : T B) : Nat => zero
```

In order to make it irreducible, we make it a constructor of an inductive type:

```aya
open data Wow : Type 2
| wow {U : Type 1} {T : U -> Type} (A B : U) (x : T A) (y : T B)
```

Here, `wow` has two implicit parameters,
and note that the second one is a higher one (it is of a function type).\
Consider the following example:

```aya
def test1 {A B : Type 0} {a : A} {b : B} => wow A B a b
```

Observe the elaborated term of `test1`: `test1`{show=core, mode=nf}\
Consider another example:

```aya
def test2 {A B : Type 0} {a : A} {b : B} => wow A B a a
```

Observe the elaborated term of `test2`: `test2`{show=core, mode=nf}\
Showing the implicit arguments: `test2`{show=core, mode=nf, implicitArgs=true}
Showing the implicit patterns: `test2`{show=core, mode=nf, implicitPats=true}
Showing the lambda types: `test2`{show=core, mode=nf, implicitArgs=true, lambdaTypes=true}
Default: `test2`{show=core, mode=nf}
