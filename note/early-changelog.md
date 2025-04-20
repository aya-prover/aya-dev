# Early changelog

## v0.38

We are now living on Java 24!

Breaking change:

The keyword `partial` is renamed to `nonterminating` to reserve `partial` for partial elements,

New features:

- An incomplete implementation of partial elements. Need overhaul, because there might be absurd cofibrations (`0 = 1`) and the elements should be nullable. It's non-null currently :(
- The `tailrec` modifier, that indicates a function to be tail-recursive, and will produce code using TCO (thanks to @linxuanm),
- Automatic dependent pattern matching in `match` expressions, using the `elim` keyword (thanks to @dark-flames),
- The modifier `inline` now actually has semantics -- any invocation will be call-by-name inlined,
- The symbol `__` for implicit lambda expressions similar to Arend, Scala, and Lean,
- The double checker now checks the boundaries of path lambdas.

Bug fixes and internal improvements:

- Fix that `Signature#descent` didn't handle de-Bruijn indices correctly,
- Replace some usages of stream API with kala views,
- More performance tests for the [PLunch talk](https://www.youtube.com/watch?v=lvwygACgJFk),
- Submodule `tools` no longer depends on `kala-collection-primitive`,
- Fix potential bug in pattern matching unfolding when the arguments are not sufficiently unfolded,
- Improve generated PLCT reports to not use `api` URLs.

## v0.37

Breaking changes:

- The multi-variable version of `elim` now needs comma-separated names,
  like `elim a b c` needs to be `elim a, b, c`.

The JIT compiler now _optimizes_ the generated code, by first generating a Java AST,
optimizes it, and then translate this Java AST into Java source.
There are the following optimizations:

1. For empty `telescope` and trivial `isAvailable`, they are no longer generated, and the default
   implementation of these methods will be used. This should only make the code (much) smaller,
   and generally more hot-code JIT friendly.
2. `CompiledAya` metadata now has default values, and the code generator will use them.
3. For `break` statements at the end of `do while` blocks, they are removed. This should make the
   code faster, but turns out javac already optimizes this, so it only makes the code smaller.
   But this is helpful for our own bytecode generator in the future.
4. For `switch` statements with a `Panic.unreachable` default case, it merges with the last case,
   because the unreachable-ness is guaranteed and very unlikely there's a bug that reaches it.
   This should both make the code smaller and faster.
5. After 3, `switch` with only one case is inlined, and `switch` with only two cases is turned into
   an `if` statement. This should make the code much smaller, but not faster.

In June, the compiled bytecode of the stdlib is 1.12MB, and after all these and some added definitions
during this time, it's even smaller at 806KB. This is amazing.

Other major new features:

- Identifiers can begin with `-` now. This means `-` (minus) and `-->` (long arrow)
  are now valid identifiers.
- No-arg constructor pattern matching, when the constructor is out-of-scope,
  will be understood as variable patterns. This is even more harmful in constructor patterns.
  There will be a warning for this now.
- SIT supports `elim`.

Minor new features:

- The library now has more stuff: `maybe`, more list functions, etc.
- You can now use `--datetime-front-matter` and `--datetime-front-matter-key` to insert date time
  info in the front matter of markdown output. If there is no front matter, Aya will make one,
  and if there is, Aya will insert into it.
- Patterns shadowing telescope will no longer trigger warnings.
- The pattern type checker now handles more cases, see `Pattern.aya` in
  `cli-impl/src/test/resources/success/src`.
- The highlighter now handles `import` and `open` statements more robustly and smartly.
  For `import a::b::c`, the `a::b` part will be considered a reference, and `c` is considered a definition,
  because it introduces a module that can be used locally. If you do `import a::b::c as d` instead,
  then the entire `a::b::c` part will be considered a reference, and `d` is considered a definition.

Bugs fixed:

- `JitCon.selfTele` was wrong.
- `match` type checking.
- `EqTerm` type checking now correctly handles `ErrorTerm`.
- Subtyping from path to pi is now correctly implemented.
- If con patterns have an error, Aya will no longer raise NPE.
- Using implicit pattern with `elim` will no longer crash (but report error instead).

Internal changes:

- Use Markdown doc comments in a few places. Now `gradle javadoc` should no longer throw those warnings.
- Move some code from `pretty` to `tools`. Now code coverage test will check `tools` too, and some unused classes are removed now.
- `registerLibrary` returns registered libraries. This is so the IDE can watch file changes correctly, and changing non-project files will not cause a reload.
- Negative tests are now checked using `git diff` rather than `assertEquals`. This generates much more readable error message.

## v0.36

Major new features:

- Pattern matching expressions are now fully functional, including dependent pattern matching (`match ... as ... returns` in Coq)
  and JIT-compilation of them (which lifts them into global functions with lifted captures).<br/>
  The plugin is updated with supports for them.
- The `aya-lexer` language in `.aya.md` files, which highlights a code block with Aya lexer only.
  This is  good for demonstrating Aya features without having to make it valid Aya code.
- Replace the commonmark library with JetBrains' markdown library, which provides source code information in the AST,
  so we get actual error report for incorrect Aya inline code syntax (we could not have this because commonmark does not
  provide location).<br/>
  It also implements the commonmark spec incorrectly in a way that is IMO better: when it sees an HTML block,
  it will completely ignore it, rather than trying to also parse the parts that are not HTML tags.<br/>
  The old behavior can suffer from code like `<code>aa*bb</code>cc<code>dd*ee</code>ff`, which will be parsed as
  `<code>aa<em>bb</code>cc<code>dd</em>ee</code>ff` according to commonmark. I think this is VERY cringe.
  JetBrains markdown preserves them.
- The Aya inline code syntax is changed from `` `a`{x=y, z=k} `` into `` `a`(x:"y" z:"k") `` because of the limitations
  of the new markdown engine.
- Now writing terms inside a goal will get the type checker to infer the type of the term, and include them in the error message.
  They were totally ignored before.
- The pattern matching errors now have variable names, instead of de Bruijn indices.
- Aya now supports Spanish. In addition to `{? x ?}`, you can also use `{¿ x ?}`.
- Inactive maintainers are removed from related files.
- Aya is now bundled with Java 22.

Minor new features & user-visible bug fixes:

- The same unsolved meta is only reported once.
- `:load` libraries in REPL now correctly imports shapes.
- `:unimport` command in REPL, which unloads a module.
- Error report on classes is slightly more robust.
- `@suppress(Shadowing)` for suppressing name-shadowing warnings.
- Fix a bug in REPL pretty printing that prints constructors twice.
- When type checking a library, if a module takes more than 1.5 seconds, the time is printed.
- Unification side effects is slightly more controlled during lossy-unification.
  I spent an excessive amount of time on trying to implement an advanced version of this,
  but turns out it's completely not worth it.
- Instead of taking a supplier of the default value, the `invoke` methods generated by the JIT compiler
  now _make_ the default value by calling itself. This drastically reduces the size of generated code,
  while having almost the same performance.
- Slightly improve the concrete syntax and IDE-related code to allocate fewer objects.
- Constructor telescope explicitness is now correctly retrieved.
- Plain code blocks are now `<code class="">` instead of `<code class="Aya">`.

Internal changes:

- Instead of higher-order functions, we now use try-with-resources for cleanups (@mio-19),
  including pushing and popping variables into/from the context stack, `let` bindings,
  and pattern types in pattern matching. This can save a lot of memory, because JVM preserves all the stacks.
- The normalizer is now tail-recursion optimized at source level, which should be much more (100 times more) efficient,
  but much harder to debug, because we cannot unwind loops as we unwind stacks.
- The JIT compiler code generator now has an abstraction layer that can be implemented using bytecode generators
  (the classfile library) instead.
- Unused locally nameless operations are removed.
- Slightly fewer repeated normalization.
- JIT-compiled top level classes now have a `@CompiledAya` annotation, though it is not used,
  and we don't know what to do with it.
- When a JIT-compiled Java file failed to compile, we do not delete the file, even when `DELETE_JIT_JAVA_SOURCE` is set to true.
- A new utility to collect all free vars in a term.
- Negative tests output message is combined into one line for each test container class.
- `Jdg` is moved to the `syntax` submodule.

## v0.35

Breaking changes:

- Σ types are now binary rather than n-ary, and compilation is correctly implemented now

Major new features:

- Pattern matching expressions are now supported (since the locally nameless rewrite), in its most basic form (no dependent pattern matching yet).
- Pragma syntax is implemented, with `suppress` for suppressing warnings. You can now suppress only one warning: `@suppress(MostGeneralSolution)` for the "solving with not the most general solution" warning.
- JIT-compiled binaries are used right after being generated, even in the same library.
- Projection expressions are now type-checked (yes, we forgor).

Minor new features & user-visible bug fixes:

- Add `--no-prelude` flag to disable loading the prelude.
- When JIT-compiling a constant function, substitution of that function will be a constant function too.
- Now clauses and return types of functions are correctly pretty printed, no more de Bruijn indices shown to the user.
- Self-recursive function signatures are now correctly reported as errors.
- Cubical `coe` on pi, sigma, un-parameterized inductive types, and sorts are correctly normalized.
- Fix a long-standing bug of binary application term normalization.
- Libraries with user goals in them will not be JIT-compiled.
- The standard library has sum type and a couple of new lemmas on vectors now.
- A bunch of error reports are implemented.
- The "unsolved meta" error now is reported only once per meta.
- Improved type checker to handle more meta-typed terms.

Internal changes:

- No longer replace `MatchException` with `RuntimeException` because we are on Java 21 now.
- Update the Nix flake file.
- Code from jit-class branch is mostly cherry-picked to main.
- Pi and Sigma core terms are now combined and lots of code is shared.
- The APIs for implementing `hcom` (interval equality) is ready.

## v0.34

Semi-breaking changes:

- Revise the behavior of ambiguous exports -- we report errors on ambiguous exports now.
  Before, Aya will export all candidates, and error will occur only when you try to use one.
  But there is no way to resolve such ambiguity, so we now report errors earlier
- Kotlin stdlib is now in the binary dependencies of Aya due to transitive dependency
- Upgrade many dependencies

New features:

- Add `:debug-show-shapes` in REPL to show currently recognized shapes in the REPL context
- Preliminary support for classes, adding type checking for class declarations,
  class specializations, JIT-compilation, etc., but not yet for class instance resolution
- Add `:info` (alias: `:i`) command in REPL to show information about a name, similar to GHCi

Improvements to existing features including bug fixes:

- Correctly display emojis in Windows Terminal
- Improve error messages for unsolved metas thanks to @utensil and @HoshinoTented
- The jlink release will include compiled version of the std library
- Coercive subtyping now works from path to functions
- Better type checking for list literals with the presence of ambiguity
- Correctly pretty print `RuleReducer.Con`

## v0.33

We've started working on classes.

New features:

- Implement flex-flex unification
- Thorsten Altenkirch's QIIT is now correctly type-checked
- Now REPL and literate can in most cases display types of variables under
  the `NULL` normalization mode.
- The type of path constructors are now correctly displayed as equalities
- A more clever, context-based name generation for fresh variables
- JIT compiled code is substantially faster, we lazily compute the default value
- Share some instances in JIT compiled code
- `prelude` is now a thing, it will load basic stuff like naturals
- Quotients and `FMSet` in the library

Bug fixes:

- Modifiers of `def` are now serialized
- Indexed constructor return types are now correctly indexed
- Empty inductive types are now correctly treated in various places
- Submodules JIT now works

## v0.32

Breaking changes:

- Renamed the `data` keyword to `inductive`

New features:

- Implemented some missing features from previous Aya, including:
  - Pushing telescope for fn and constructors, not for data yet
- `partial` definitions will not be termination-checked and will not be unfolded,
  definitions that fail termination check will be marked as `partial`
- `ListTerm` now uses a persistent list, which should be more efficient

Bug fixes:

- Countless bugs related to `ownerArgs`/`dataArgs` issues, de Bruijn indices in
  patterns, 
- Resolving for `elim` now happens in resolving, not parsing
- Literate mode is not fully compatible with the old version
- Quotient inductive type fixes
- Some completely misguided error messages are now fixed
