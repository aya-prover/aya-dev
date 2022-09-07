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
We'll talk 'bout this later.

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

## `org.aya.util.Decision`

So, we will not introduce any "feature" flags, and will have only one base library.
This language will be one language, its features are its features.
If we decide on removing a feature, then we remove it from the language
(not going to keep it as an optional flag). If we decide on adding a feature, we add it
and it should be available without any options.

It should still be encouraged to add some fancy, experimental features, but I think
they should stay in branches or forks and will be either enlisted to the language or abandoned
eventually.

However, the "parameters" part is not as bad.
For example, it is very easy to allow type-in-type in the type checker -- we just disable the level solver.
This is useful when the level solver prevents us from experimenting something classical using our
language features but unfortunately the level solver is just unhappy with something minor.
We can also like tweak the conversion checking algorithm we use, like we can use a simpler one that
only solves first-order equations or we can enable the full-blown pattern unification algorithm.
Verbosity levels, can also be seen as such parameter, and it's extremely useful for debugging the compiler.
So we can apply that.

### Safe flag?

To be honest, it's hard to decide on a semantic of the word "safe", and relate that to the Agda pragma `--safe`.
To me, it means "consistency", and if we can set `--safe` as the last argument of an Agda file,
it should be guaranteed by Agda that it cannot provide you a proof of false.
There [are](https://github.com/agda/agda/issues/3564#issuecomment-464102606)
many [related](https://github.com/agda/agda/issues/4560#issuecomment-609001957)
discussion [in](https://github.com/agda/agda/issues/3315#issuecomment-436905598)
the Agda [issue](https://github.com/agda/agda/issues/4450#issuecomment-586891969)
tracker [that](https://github.com/agda/agda/issues/3626#issuecomment-472159808)
talks 'bout how should `--safe` behave.
Sometimes it fits my guess (consistency), sometimes it implies more stuffs.
Well, let's forget 'bout Agda for a bit, and focus on the one thing: a flag for consistency.
This flag looks important to me.

For disabling or enabling some checks, if we disable a check that is required to be consistent,
then it should break `--safe`. I think we will of course enable all of these checks by default,
so exploiting the disabledness of a check can lead to inconsistency eventually.
So, we can use an "unsafe" flag to ensure that our language is only unsafe when we want it to be.
It is quite meaningful to have an "unsafe" mode, from a real-world programming perspective.
We should be able to skip some proofs, write some loops (or some non-structural recursion)
and stuffs in unsafe mode.

However, keeping the language consistent is also important, as we wanna to some extent guarantee
that the theorems in the math-oriented libraries are not pseudo-proved.
I suggest reporting a warning whenever the unsafe flag is enabled, so it's very explicit that you're
programming, not proving theorems. This can also encourage people to write safe programs.

### Conclusion

We'll have a language, with some flags that tweaks the parameters of some algorithms (which are no-harm),
and some flags for disabling some checks (which will lead to an error at the end of tycking),
and an unsafe flag that enables a set of features such as
[`sorry`](https://twitter.com/GaloisNeko/status/1305891671440158720) and suppresses the error of
disabling checks.

## Library Design

Speaking of the base library design, I have some vague ideas in mind.
I'd like it to be split into three parts (not sure if we're gonna make it three modules inside one stdlib
or three standalone libraries):

+ The base part, for basic definitions like lists, trees, sorting, rings, categories, path lemmas, simple tactics like rewrites, etc.
+ The programming part, for I/O, effects, unsafe operations, FFI, etc.
+ The math part, like arend-lib or Lean's mathlib.

Then, we can use these libraries on-demand.
