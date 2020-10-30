# Proposal of "let" expressions

Binding local variables in `let`-clauses is a commonly seen feature in functional programming
languages and is also known as the protagonist of let-polymorphism in Hindley-Milner.

`let` is implemented in Arend, but you can only bind an expression to a name
(though there's also parameters support, but I feel like it's more or less a sugar for local lambdas).
It's preferred to allow `let` to introduce a local scope for arbitrary definitions
(a similar issue in Arend can be found [here](https://github.com/JetBrains/Arend/issues/129)).
So, I propose it this way:

```mzi
\let {
  -- an anonymous local module, you can define datatypes,
  -- \open or \import something, or create \class or \instance,
  -- and define recursive functions, etc.
} expr
```
Also, `\where` can be a feature dual to `\let`.
I don't suggest organizing modules in a way Arend does,
but instead we use `\abuse` (or `\abusing` or `\using`)
to create the `\level`, `\coerce` and `\notation` definitions
(but not other arbitrary functions).
