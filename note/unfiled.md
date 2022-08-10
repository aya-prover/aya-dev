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

### The `Term` traversal API

In general, we wish to concisely express transformation of the type `Term -> R` for some type `R`
(or even the dual case `R -> Term`) without writing out the boring recursive steps in between.
One good approach taken by the functional programming community is to utilize various recursion schemes.
However, the machinery required to set up this way is a bit heavy in Java,
and it turns out not worthy to proceed for our current use cases.

In fact, most of our `Term` traversal operations are endomorphisms `Term -> Term`,
or term consumers `Term -> Unit` that produce side effects.
These two common patterns can be generally expressed as a pre-order traversal followed by a post-order traversal.
See `EndoFunctor` and `TermConsumer` for deriving the "global" operations from the corresponding `pre` and `post` traversal logic.
Notice that there are operations that cannot be strictly expressed by deriving from `pre` and `post`,
but are still implementation of `EndoFunctor`/`TermConsumer` anyway.
For example, to calculate the WHNF of some term, the `WHNFer` skips certain subterms to save calculations.
We achieve this by overriding the default logic in `apply` when implementing `EndoFunctor`.

It is also common to deal with all `Var` that occurred in a `Term` during traversal.
One can utilize the `VarConsumer` interface that in addition to `TermConsumer`,
requires specifying the logic of consuming a general `Var`.
