# Proposal of "abuse of notation" feature

This was a thread discussed originally on [a commit](https://github.com/ice1000/mzi/commit/c57a7c75882da5023772903b8f4decbca90ef598#r43459719).

[The existing proposal on class system design](mathmaticians-expectation-of-pa/01-class-system.md),
has one more disappointing aspect: it makes `Ring` no longer a `Monoid` or an `AbGroup`.

On master branch Arend, you can do

```arend
\class Ring (E : \Set)
  | \coerce mult : Monoid E
  | \coerce add : AbGroup E
```

which, can be used as you can guess from the syntax. Also, there's still a distinction between `Group`
and `AddGroup`, and other similar victim structures.

I have an idea, to address the "ring problem". In my mind, Arend has these problems:

+ Distinction of `Monoid`/`AddMonoid` -- you proposed `\use \univalence`,
  but these two structures are equivalent in an even stronger way. They are equivalent up to α conversion!
+ Not being able to `\open` a `\class` within a `\class`, this is exactly what you want `\use mult.\all`
  to do, but I think `\open` is a better keyword here. This is solved by using distinct classes for `+`
  and `*` in arend-lib, but if we remove such distinction, un train peut en cacher un autre.

## The proposal

First, let's use a new set of notations.

+ `\use` -> `abuse`
+ `\class` -> `struct`
+ `\data` -> `union`
+ `\where` -> `where`

Also, I'll invent some new keywords.
We introduce the notion of "abuse notation", which allows you to rename a union or a structure:

```
union Int
 | pos Nat
 | neg Nat
  where {
    abuse coerce fromNat (n : Nat) : Int => pos n
    abuse notation Z | + | -
  }

struct Semigroup
 | classify A : Set
 | infixl * : A -> A -> A
 | *-assoc : blabla
 where {
   abuse notation AddSemigroup | A | + | +-assoc
 }
```

Abuse of notation creates structurally identical data types, but using a different set of names.
These types will be treated as different types (nominally), but can be implicitly casted back and forth in
_every possible use cases_ (maybe there are some holes awaiting us to jump in, but I don't see any yet),
and such cast is free!
Then, we allow multiple inheritance, and for diamonded fields we enforce a re-implementation.

Apart from that, I propose another (maybe unnecessary) feature:
abuse of notation is preferred to only be used when you have to. So, if you abuse notation unnecessarily
(say, without clashing with the canon), the compiler should threat you that if you continue to do this
I'll refuse to typecheck your code. But in fact it just warns.
