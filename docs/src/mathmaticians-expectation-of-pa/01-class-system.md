What does a mathematician want from a proof assistant? [series, draft]
===

##### user-friendly design for proof assistants

Part 01.  The Class System
---

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
  
  This also requires minimal support from the typechecker --- `\use add (* \as +)` can just be syntactic sugar for `+ => add.*`, etc.  In fact this can be emulated in Arend:
  
  ````arend
  \import Algebra.Group
  \import Algebra.Monoid

  \class RingInterface (E : \Set)
    | \infixl 7 + : E -> E -> E
    | negative : E -> E
    | zro : E
    | \infixl 8 * : E -> E -> E
    | ide : E
    | ldistr (x y z : E) : x * (y + z) = x * y + x * z
    | rdistr (x y z : E) : (x + y) * z = x * z + y * z
    | lann (x : E) : zro * x = zro
    | rann (x : E) : x * zro = zro
  
  \class Ring \extends RingInterface
    | add : CGroup E
    | mult : Monoid E
    | + => add.*
    | negative => add.inverse
    | zro => add.ide
    | * => mult.*
    | ide => mult.ide
  ````
  See [here](https://github.com/tonyxty/FunWithArend/blob/master/src/RingsDoneRight.ard) for a full version.
