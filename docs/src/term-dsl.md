# The core language DSL

For quick testing, there is a lisp dialect used to create core terms.
We call it the 'term DSL', or the 'core language DSL'.
Its syntax definition could be found [here](../../buildSrc/src/main/antlr/org/mzi/parser/Lisp.g4),
which is a very straight-forward lisp syntax.

It is parsed using the Java code [here](../../base/src/main/java/org/mzi/core/TermDsl.java),
and I'll explain the specification of the DSL in this document.

The DSL is designed to reflect the exact shape of (a supported subset of) the core language,
and will be extended when needed.
The compiler CLI doesn't support parsing expressions like this.
Instead, this whole thing is used for testing the tycker.
Thus, it does not have any error handling when parsing failed.

## Scoping rules

For convenience, there's no scoping rules. Every term shares the same global context,
and the names will be classified by their, well, names.

The core language does not use any deBruijn indices or levels.
Instead, it creates a unique id for each reference (as a solution to capture-avoiding-substitution).
This fact is precisely reflected by the way we treat names in the term DSL.

## Syntax definition

Directly writing an identifier will be seen as a `RefTerm`, which is the term for references
(and is not the head of an application).
Using lisp function call syntax `(f a b ...)` could produce more complicated terms,
and the form of the term depends on the head `f`.

In the following list, `a` and `b` will represent expressions, and `n` will represent numbers,
other symbols denote keywords.

+ `(U)` -- the omega universe
+ `(app a b)` -- application (to references), applying argument `b` to term `a` as explicit application
+ `(fncall a b ...)` -- function application, applying the rest of the terms `b ...` to `a`
+ `(iapp a b)` -- same as `app` but as implicit application
+ `(tup a b ...)` -- tuple introduction, from all of `a b ...`
+ `(proj a n)` -- tuple elimination, projecting the `n`-th element from tuple `a`
  + `n` is 1--indexed

These structures don't involve bindings (`Term.Param` in the code) and are simple.
Here's the syntax for singular bindings, we will call it `bind` later on:

+ `(x a i)` where `x` is a name, `a` is a term, `i` is `im` or `ex` -- the
  explicitness of the binding.

We also have plural bindings, which will be called `binds` later on:

+ `(x a i binds)` -- symbols are the same as in `bind`
+ `null` -- empty list

With these we define the rest of the terms:

+ `(lam bind a)` -- lambda terms, with a binding `bind`, and body `a`
+ `(Pi bind a)` -- pi type terms, with domain `bind`, and codomain `a`
+ `(Sigma binds a)` -- sigma type terms, with domain `bind`, and codomain `a`

## Examples

The type of `uncurry` is:

```
(Pi (A (U) ex)
 (Pi (B (U) ex)
  (Pi (C (U) ex)
   (Pi (f (Pi (a A ex)
           (Pi (b B ex) C)) ex)
    (Pi (p (Sigma (a A ex null) B) ex) C)))))
```
