# Haskell or Agda style extensions

In Haskell, you can do `{-# LANGUAGE TypeFamilies #-}`,
and similarly in Agda you can `{-# OPTIONS --two-levels #-}`.
These "pragma" can also be specified via command line arguments.
Since Haskell is too weak and basic features need extensions,
I'll be avoiding talking about it and stick to Agda.

## Agda's extensions

The purpose of these pragma is threefold:

+ Disable or enable (particularly disable) certain checks or compiler phases
  such as positivity checks, termination checks, deletion rule in unification, etc.
+ Modify the compiler by changing some parameters, such as termination check's recursion depth,
  use call-by-name instead of call-by-need, cumulativity, etc.
+ Disable or enable (particularly enable) certain language features,
  such as cubical features, sized types, custom rewriting rules, etc.

One special pragma is to ensure that no known inconsistent flag
or combination of flags is turned on -- `--safe`.

The current status of Agda libraries, that having separate cubical, HoTT library, and standard library,
implementing the basic features individually, is yelling at us that Agda is far away from a single programming language.
Each flag that enables a certain language feature makes Agda a different language,
and it is difficult in general to make two different language source-compatible (see Kotlin-Java, Scala-Java, etc).

It is good to keep your language evolving like Agda (adding new features aggressively), and indeed Agda is **the** proof
assistant with the richest set of language features I've known so far.
However, this also negatively impacts Agda's reputation to some extent,
that people say it's an experiment in type theory.
Well, maybe it's not a negative impact, but it prevents big customers
(such as Mathematicians looking for a tool to formalize math) from choosing the language.
At least, we don't want this to happen to our language.

## Decision

So, we will not use those flags, and will have only one standard library.

[comment]: <> (TODO: more to write)
