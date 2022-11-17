# The standard library candidate

This "common" directory contains the standard library candidate for the Aya prover.
It is organized as follows:

+ Root package contains the basics of the basics, such as path type related stuff,
  interval type related stuff, `funExt`, and such.
+ `Arith` package contains the basic arithmetic sets, such as `Nat` and `Int`. `Rat` and `Real` are planned.
+ `Data` package contains some types useful for programming (inspired by Haskell), such as lists, booleans, finite sets, vectors, red black trees, etc.
+ `Spaces` package contains some interesting topological spaces (defined as higher inductive types in the sense of homotopy type theory), such as $n$-spheres, $n$-tori, etc.
+ `Logic` contains logic stuffs, such as decidable types, h-levels, falsehood, etc.

Principle: for a particular area of mathematics, the corresponding package name should
either be the plural form of the objects of interest (spaces, sets, etc.), or the subject
name itself (maybe short-handed, such as arithmetic becomes `Arith`).
We expect the former to be countable nouns, and the latter be uncountable nouns.

## Subpackages

For "very interesting" (i.e. with lots of structures/theorems/properties) types,
we separate them into a `Core` and `Properties` (and maybe more) subpackages,
where `Core` contains the type definition and very (very-very-very) basic derived operations.
The complicated stuff will be separated. Example:

+ `Arith::Nat::Core` defines natural numbers, `+`, `*`, with some results on commutativity/associativity/distributivity.
+ `Arith::Nat::Order` defines an ordering on natural numbers.
+ `Arith::Nat` imports the subpackages and define the most important and biggest structure on it. For example, `Nat` is a linearly ordered commutative semiring, and we expect the structure to be defined there.
