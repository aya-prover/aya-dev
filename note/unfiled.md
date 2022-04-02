### Global states

There are two global states:

+ `org.aya.util.error.Global`, controlling whether to equalize all
  `SourcePos` and whether to avoid generating random names.

### `StmtTycker`

`doTyck` visits a declaration. Take function as an example.
- Call `visitHeader`.
  - Check the current telescope of the function and the result type are well-typed.
  - Build a signature.
- Check the definition body.
  - If it is defined by direct definition, zonk the body.
  - If it is defined by pattern matching...
    - Elaborate every clause and filter out absurd ones.
    - Check for pattern confluence.
- Return new definition with the checked body and signature
  - Note: currently `ensureConfluent` should be called after constructing the to-be-returned `Def`.

Primitives: we assume that primitive functions have <= 1 universal level parameter.

### Flexible `Term` traversal with `TermView`

`TermView` aims to provide generic term traversal builder similar to that provided by the iterator combinator style. It
can be created as `term.view()`, extended with `.mapPre(f).mapPost(g).map(f, g)`, and executed by `.commit()`. However,
there are important semantic differences compared to the iterator interface which we discuss as follows.

Previously, we use either the visitor pattern or direct pattern matching to describe a term traversal function.
Specifically, for a fixpoint operation `Term -> Term`, the traversal typically consists at most three parts:
pre-processing, recursive operation on sub-terms, and post-processing. We wish to extract the common logic of recursive
traversal into `TermView`
and only require providing the pre- and post-processing logic to describe a full traversal.

First, We need to introduce some notations and definitions for the sake of this discussion.

0. We use `;` to denote function composition such that `(f ; g) x = g (f x)`. This is similar to the pipeline operator.
1. For a function `f : Term -> Term`, we form `f' : Term -> Term` which for an input term `t`, applies `f` to the
   sub-terms of `t` and return the result. If `t` has no sub-terms, then `f'` is trivially the identity function. Notice
   that we have `(f ; g)' = f' ; g'`.
2. The function `f↓ : Term -> Term` takes an input `t` and applies `f` from the root of `t`, proceeding to structurally
   smaller sub-terms. We define it formally as `f↓ = f ; f↓'`
   If we continue to expand the definition, we can see `f↓ = f ; f' ; f'' ; f''' ; ...`, and intuitively this will
   terminate after we reach the most nested sub-term. This captures applying `f` in a pre-order traversal.
3. Dually, the function `f↑ : Term -> Term` takes an input `t` and applies `f` from the leaves of `t`, proceeding to
   structurally larger super-terms. We can define it as `f↑ = f↑' ; f`. This captures applying `f` in a post-order
   traversal.

Now suppose we have `f` as the pre-order operation and `g` as the post-order one, the induced traversal function is
then `f↓ ; g↑`. Expanding out gives a recursive relation `f↓ ; g↑ = f ; (f↓ ; g↑)' ; g`. This is the semantics of
the `traverse` helper method which in turns implements `.commit()`.

Sometimes we wish to compose multiple pre-/post-order operations while still wish to only traverse the term once.
Unfortunately, `f↓ ; g↓` and `(f ; g)↓` are not the same in general. But once we have `f↓' ; g = g ; f↓'`, the above two
operations are the same. Dually, the condition for composing post-order operation is `f ; g↑' = g↑' ; f`. Please be
aware that the `preMap`, `postMap`, and `map` all assumes the appropriate conditions to be met in order to fuse
operations and avoid unnecessary traversals. If this is undesired, use `.commit()` to force a traversal eagerly.
