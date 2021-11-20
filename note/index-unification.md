This is a compilation of the summaries of a few pull requests on index unification merged in v0.13.
These features are sophisticated, so I don't want to lose these implementation notes.

[#193]: https://github.com/aya-prover/aya-dev/pull/193
[#194]: https://github.com/aya-prover/aya-dev/pull/194
[#198]: https://github.com/aya-prover/aya-dev/pull/198

Here's an explanation of the notion of "forced patterns" I copied from Andras Kovacs' discord server
(starting from [this message](https://discord.com/channels/767397347218423858/767397347218423861/910644131586527282)):

> Here's an example.
> Consider the obvious `Vec (n : Nat) (A : Type)` definition:
> ```aya
> len : ∀ {A} -> (n : Nat) -> Vec n A -> Nat
> len [I'm a forced pattern] vnil = 0
> len [I'm another forced pattern] (vcons _ x) = suc (len _ x)
> ```
> Here's a cleaned version
> ```
> len : ∀ {A} -> (n : Nat) -> Vec n A -> Nat
> len a vnil = 0
> len a (vcons _ x) = suc (len _ x)
> ```
> Technically, if you tyck the patterns in your head, you run thru the first clause.
> The first pattern is fine, obviously `a` is a valid pattern for `Nat`, and now you split on `vnil`.
>
> But `vnil` is not a constructor for `Vec a A`! It's only a constructor for `Vec zero A`.
> So you need to somehow transform the pattern `a` into `zero`, so there are states.
>
> Introducing the ability to infer forced patterns makes DT programming much more convenient, but it's like a stateless -> stateful change.
> What's worse: consider the second clause, you'll turn `a` into `suc a'`, where you have to add `a'`, a binding, to the context...
>
> The thing is you not only generate a substitution but also starts to modify the local context during constructor filtering.
> Without forced pattern you don't have to.
> So it's a rather big refactoring.
>
> Btw, I am using the "simpler indexed types" from TyDe 21 (apologize for accidental self-promotion),
> so there are some technical differences from Agda. The thing turns out to be (kinda) simple tho.
> Much better than Agda's implementation

## [#193] Enhance something

+ 🍒-picked @imkiva's commit from #191 for projection tycking
+ Added hint for index unification failure
+ Simplified grammar
+ Added a TODO for index unification failure in pat classifier.
  + Basically, if we are splitting with clauses present and we cannot
    decide if we want to split on one of the constructors, we report an error.

## [#194] Enhance error report for rbTree

+ Now index unification failure in constructor selection (in coverage checker) is taken carefully:
  if the failure is due to a stuck case and there aren't any catch-all patterns, we report an error.
  (in case there are no patterns, we do not report error, as this will eventually become either an impossible case or a missing case)
+ Added indexed redblack tree (I blame @dramforever for pushing me to do this, it took me the entire afternoon!!
  But I also appreciate it as this helped me discovering all these bugs)
+ Since `ownerTele` in `CtorDef` isn't equal to the corresponding `DataDef.telescope()`
  (so in case core of a concall is missing, we can't retrieve `ownerTele`!! This is impossible yet, but may once become possible).
  To address this problem, we now store `ownerTele` into `CtorDecl.patternTele`.

https://github.com/aya-prover/aya-dev/blob/54c167ba509d63114df816440fb7e70796b13689/base/src/main/java/org/aya/tyck/StmtTycker.java#L194

This line of code constructs `CtorDef.ownerTele`, which is certainly not always the dataParams.

## [#198] Implement "meta patteriables"

Split into five sections.

### Refactorings
+ Inlined some functions, like `CoreDistiller::visitMaybeCtorPatterns`.
  There are some others, I can't remember.
+ For `BaseDistiller::visitCalls`, added a new boolean parameter for implicitness option.
  This is because sometimes it's `ShowImplicitArgs`, sometimes it's `ShowImplicitPats`.
+ I replaced  all visitors of `Pat` with pattern matching (JEP 406) and
  deleted `Pat.Visitor` the abstract visitor along with all the `doAccept` methods.

### Behavioural improvements
Now we also `zonk` the types of the patterns after `elabClauses`.
We didn't do this before, but it so far didn't cause any problem.
I conjecture that patterns' types are never used after their type checking,
but I'm not exactly sure (I'm 95% sure). If they really aren't used,
we probably don't have to zonk them, and we probably want to remove them for spatial efficiency.

### New features
+ Added new `Pat.Meta` for "unknown patterns", which are translated into `RefTerm.MetaPat` (also new)
  in `PatToTerm`. The idea behind these two structures are similar to "meta variables" but for patterns.
  + The `zonk` for meta patteriables is `Pat::inline` -- they inline the solutions of meta patteriables.
    When no solution, we turn them into bind patterns.
    So, `Pat::inline` is total, thus no need to have an error reporter.
  + In `PatMatcher`, we may encounter problems like "matching a pattern `p` with an instance of
    `RefTerm.MetaPat`". In this case, we "solve" the meta patteriable as a renamed version of `p`.
    Yes, the renamer for patterns is also added.
  + After all these, we `inline` the patterns, which is like freezing the meta patteriables.
    Solved meta patteriables are inlined as their solutions.
+ `CalmFace` patterns are now tycked as meta patterns.
  They will cause `UnsupportedOperationException` before.
+ Implicitly-generated patterns are now tycked as meta patterns if their types are data types
  (to avoid `MetaPat` appearing in the types of unifications).
  They were generated as bind patterns before.

### Assumptions
+ We will only see `RefTerm.MetaPat` in `PatMatcher` in `PatTycker`.
  Other invocations to `PatMatcher` will not. Therefore, we made the new `LocalCtx` parameter
  of `PatMatcher` nullable, because we need to modify the `localCtx` in the type checking of patterns.
+ Meta patteriables will only be solved once. Unsure what to do when they are solved more than once.
  Maybe unify the patterns to refine them further? 🤔

### Tests
Modified the redblack tree showcase with all manual implicit refinement removed. It works perfectly.
Now the exact Agda code can be translated to Aya, with every omittable pattern omitted.
One exception is that I discovered a case when you match on two `{black}` patterns,
you don't need to split a `rbBlack a x b` pattern.
I decided to keep this code to make the code shorter after all.

Sorry for my English.
