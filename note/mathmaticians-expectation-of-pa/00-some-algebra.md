What does a mathematician want from a proof assistant? [series, draft]
===

##### user-friendly design for proof assistants

In this series, I will list some problems encountered while formalizing mathematics and discuss possible design choices from a user's perspective.  <u>Disclaimer</u>: I know virtually nothing about implementations, so some of these suggestions might be unrealistic / hard to implement, so I shall restrict myself to only suggestions and not decisions.  When this happens we can discuss and find an acceptable compromise.

Part 00.  Some Algebra
---

Abstract algebra provides an ample supply of examples for testing various language features, since the definition of new algebraic structures often boils down to adding new operations and laws to old ones.  I'll list the concepts in abstract algebra that will be useful in the discussion during this series.  If you know the definition of a concept, it's safe to skip the paragraph; if you have taken a course in graduate-level abstract algebra then it's safe to skip the entire section.  As they are primarily used as examples, we won't dive into their mathematical meaning here.  This [wikipedia page](https://en.wikipedia.org/wiki/Algebraic_structure) contains useful information.

The algebraic structures here are set-based, i.e., they are `Set`s equipped with additional structures.

~~this part is ideally written in literate arend, if there is such a thing.~~

###### Structures with one operation (group-like structures)

*Semigroups* are sets equipped with an associative binary operation.

````arend
\class Semigroup (E : Set)
  | \infixl 7 * : E -> E -> E
  | *-assoc (x y z : E) : (x * y) * z = x * (y * z)
````

*Monoids* are semigroups with an identity.  The typical example is $End_C(X) := Hom_C(X,X)$ for a category C and an object X in C.

*Groups* are semigroups with inverses.  The typical example is $Aut(X)$, which is the set of invertible endomorphisms of an object X.  Permutation groups and matrix groups are special cases of automorphism groups.

*Commutative monoids* (or less commonly, *abelian monoids*) are monoids whose binary operation is commutative, i.e., `x * y = y * x`.  Examples include natural numbers.

*Abelian groups* are groups that are also commutative monoids.  The typical example is of course the integers.

###### Structures with two operations (ring-like structures)

~~this is where the fun begins~~

*Ring-like structures*.  There are various ring-like structures.  The definition involves two operations on a set R, usually denoted + and *, such that

1. (R, +) is a commutative monoid, with its identity element usually denoted as 0;
2. (R, *) is a semigroup;
3. (*) is distributive over (+), i.e. `x * (y + z) = x * y + x * z`  and `(x + y) * z = x * z + y * z`;
4. `0 * x = x * 0 = 0`.

*Semirings* or *rigs* are ring-like structures with a multiplicative identity (usually denoted 1), i.e., (R, *) is a monoid.  Examples include the so-called tropical semiring: `max` and `min` are distributive over +.

*Rngs* are ring-like structures with additive inverses or negative elements (usually denoted -x), i.e., (R, +) is a group.  This is useful in analysis: for example, all real integrable functions would form a ring under pointwise addition and multiplication, except the multiplicative identity, the constant function 1, is not integrable.  Thus all such functions only form a rng.

*Rings* are rigs that are also rngs, or rngs that are also rigs. [^ring]

*Commutative rings* are rings with a commutative multiplication.

*Division rings* are rings with multiplicative inverses for all _non-zero_ elements.

*Fields* are commutative division rings.

[^ring]: Some authors call our rngs "rings" and call our rings "unital rings".  The convention we follow here has the advantage of mnemonics: rig = ring - **n**egative and rng = ring - **i**dentity.

###### Structures with two sets (module-like structures)

These structures often involve a structure ("base structure") "acting" on another one ("primary structure" [^primary]), together with various compatibility requirements.  Actions has the signature `base -> primary -> primary`.

[^primary]: The "base" and "primary" terms are not universal; I just invent them here for clarity in further discussions.

_G-sets_ are sets equipped with a group action.

````arend
\class G-Set (G : Group) (E : Set)
  | \infix 8 ** : G -> E -> E
  | compatibility (x y : G) (z : E) : (x * y) ** z = x ** (y ** z)
````

where the `**` is often called the group action.  Here G is the base structure and E is the primary structure.  The typical example is Aut(X) acting on X by application: `app : Aut X -> X -> X`.

This definition assumes left-actions; right-actions can also be defined:

````arend
\class Right-G-Set (G : Group) (E : Set)
  | \infix 8 ** : E -> G -> E
  | compatibility (x : E) (y z : G) : (x ** y) ** z = x ** (y * z)
````

Note that this is different from a `G-Set` even after reordering the arguments of `**` and renaming the arguments in `compatibility`.  `Right-G-Set G` is the same as `G-Set (G.op)`, where `G.op` is the group obtained by reversing the arguments in `G.*`.  For abelian groups G and `G.op` are isomorphic, hence `G-Set G` and `Right-G-Set G` are the same.

*Modules* are abelian groups equipped with a linear ring action.  An implementation of modules can be found [here](https://github.com/tonyxty/Galois/blob/0f79e23eee9363a5f64fadcaf8a03ac60a8255de/src/Module.ard#L53-L58).  Examples include $R^n$ as an R-module by elementwise operations, and $R^n$ as an $M_n(R)$-module, where $M_n(R)$ is the $n \times n$ matrix with entries in R.  Modules also come in two flavors: left and right. [^R-mod]

[^R-mod]: The convention is to write `R-mod` for left modules over R and `mod-R` for right modules.  In a same vein we should have `Set-G` for right G-sets; however I've never seen anyone use this notation.
