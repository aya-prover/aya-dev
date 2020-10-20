What does a mathematician want from a proof assistant? [draft]
===

##### user-friendly design for proof assistants

I will list some problems I encountered while formalizing mathematics and discuss possible design choices from a user's perspective.  <u>Disclaimer</u>: I know virtually nothing about implementations, so some of these suggestions might be unrealistic / hard to implement, so I shall restrict myself to only suggestions and not decisions.  When this happens we can discuss and find an acceptable compromise.

## Some Algebra

Abstract algebra provides an ample supply of examples for testing various language features, since the definition of new algebraic structures often boils down to adding new operations and laws to old ones.  I'll list the concepts in abstract algebra that will be useful in the following discussion.  If you know the definition of a concept, it's safe to skip the paragraph; if you have taken a course in graduate-level abstract algebra then it's safe to skip the entire section.  As they are primarily used as examples, we won't dive into their mathematical meaning here.  This [wikipedia page](https://en.wikipedia.org/wiki/Algebraic_structure) contains useful information.

The algebraic structures here are set-based, i.e., they are `\Set`s equipped with additional structures.

~~this part is ideally written in literate arend, if there is such a thing.~~

###### Structures with one operation (group-like structures)

*Semigroups* are sets equipped with an associative binary operation.

````arend
\class Semigroup (E : \Set)
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
\class G-Set (G : Group) (E : \Set)
  | \infix 8 ** : G -> E -> E
  | compatibility (x y : G) (z : E) : (x * y) ** z = x ** (y ** z)
````

where the `**` is often called the group action.  Here G is the base structure and E is the primary structure.  The typical example is Aut(X) acting on X by application: `app : Aut X -> X -> X`.

This definition assumes left-actions; right-actions can also be defined:

````arend
\class Right-G-Set (G : Group) (E : \Set)
  | \infix 8 ** : E -> G -> E
  | compatibility (x : E) (y z : G) : (x ** y) ** z = x ** (y * z)
````

Note that this is different from a `G-Set` even after reordering the arguments of `**` and renaming the arguments in `compatibility`.  `Right-G-Set G` is the same as `G-Set (G.op)`, where `G.op` is the group obtained by reversing the arguments in `G.*`.  For abelian groups G and `G.op` are isomorphic, hence `G-Set G` and `Right-G-Set G` are the same.

*Modules* are abelian groups equipped with a linear ring action.  An implementation of modules can be found [here](https://github.com/tonyxty/Galois/blob/0f79e23eee9363a5f64fadcaf8a03ac60a8255de/src/Module.ard#L53-L58).  Examples include $R^n$ as an R-module by elementwise operations, and $R^n$ as an $M_n(R)$-module, where $M_n(R)$ is the $n \times n$ matrix with entries in R.  Modules also come in two flavors: left and right. [^R-mod]

[^R-mod]: The convention is to write `R-mod` for left modules over R and `mod-R` for right modules.  In a same vein we should have `Set-G` for right G-sets; however I've never seen anyone use this notation.

## The Class System

The class system plays a funny role in mathematics.  No one pays any attention to it; in fact I wasn't even aware of it before I started formalizing mathematics.  But once you look for it, it's actually **ubiquitous**, and the class system being used (implicitly) is quite **sophisticated**!  It is often hidden behind innocent-looking phrases like "abuse of notations", but proof assistants, as natural members of the International Notation Rights Organization, must refrain from such inhumane treatments of notations.  That's why class systems were invented.

###### The ring problem

I don't know if this problem already has a name in general OO programming like diamond problem does, but here is the description:

Each ring carries two group structures with them: additive group (which is a monoid) and multiplicative monoid.  This poses an obvious problem: if we try to add operations by `\extend`ing classes, the two monoids will clash.

The requirements are:

1. Natural representation of complex expressions, like `(zro + ide) * x + y`.
2. Apply known constructions and results about monoids to both monoids.  For example, we can say "apply the Lagrange's theorem to the additive group of R to get ..." or "S is a submonoid of the multiplicative monoid of R".

Possible solutions are

* The arend solution --- multiple inheritance.  Define `Monoid` and `AddMonoid` as two isomorphic but separate classes, the only difference being the operator used (* and +, respectively).  Then a ring can `\extend` both `AddGroup` and `Monoid` without problem.
  * Pros
    1. Easy to write expressions.
    2. Automatic inference as groups / monoids.
  * Cons
    1. The diamond problem.  This does not happen here, but for division rings, we have to say "the _non-zero_ elements form a group under multiplication", and the underlying set of the additive group and the multiplicative group will differ.
    2. It must be possible to specify `R as Monoid` and `R as AddMonoid` to satisfy the second requirement; relying on automatic inference may not be sufficient.  And even with this additional syntactic structure it is not very readable.
    3. Need additional classes.  To use results proved for `Monoid`s we also have to define coercions.  Even with the help of univalence (`Monoid = AddMonoid`, so that transferring results proved for one structure to the other is made easy), this still requires a lot of boilerplate code, which is always bad.  Perhaps we can provide some degree of language-level or tactic-level support:

    ````arend
    \class AddMonoid \renaming Monoid
      | + => *
      | zro => ide
      | +-assoc => *-assoc
      ...
      \where {
        \use \univalence AddMonoid=Monoid : AddMonoid = Monoid	-- auto-generated equality
      }
    ````

* The has-a solution.  Which one of the following is more true, a ring "is-a" multiplicative monoid and "is-a" additive group, or "has-a" multiplicative monoid and "has-a" additive group?  This consideration leads to the following definition:
  ````arend
  \class Ring (E : \Set)
     | mult : Monoid E
     | add : AbGroup E
     | distr (x y z : E) : x mult.* (y add.* z) = (x mult.* y) add.* (x mult.* z)
  ````
  * Pros
    1. No additional efforts on the type system are needed.
    2. Easy to satisfy the second requirement, in a very readable way: just write `Lagrange R.add` or `S : SubMonoid (R.mult)`.

  * Cons
    1. Terrible readability for expressions.  (`(zro + ide) * x + y` becomes `((add.ide + mult.ide) mult.* x) add.* y`)
    2. Also terrible "writablity" for expressions.

* Some mix between these two.  What I have in mind is based on the "has-a" solution, with some form of renaming / aliasing:
  ````arend
  \class Ring (E : \Set)
    | mult : Monoid E
    | add : AbGroup E
    \use mult.\all
    \use add (* \as +, *-assoc \as +-assoc, ...)
  ````
  
  with a more clever design (for example, call it `assoc` instead of `*-assoc` in the definition of groups), we can write things like `goal (R : Ring) (x : R) : (zro + ide) + x = zro + (ide + x) => add.assoc _ _ _`, which looks concise and natural.
  
  This also requires minimal support from the typechecker --- `\use add (* \as +)` can just be syntactic sugar for `+ => add.*`, etc.