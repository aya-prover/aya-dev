# Early changelog

This file contains the changelog of the Aya language 0.x.

## 0.10

Performance improvements¸ prototypical repl, binary operator
enhancements, relicense as MIT, substitution bug fixes, minor level
solver improvements, code of conduct & contribution guidelines.

Internal refactoring: `Problem::describe` is now parameterized by
`DistillationOptions`, remove infinity level in universe polymorphism

## 0.9

Some bug fixes, eta rule in pattern unification and `new` components,
renaming in concall and fieldcall, enhance level solver and level
substitution and level syntax, refactor some visitors into pattern
matching, fix comment syntax, enhanced coverage checker with automatic
detection of impossible patterns, concrete expression refactoring,
goal error message enhancements

## 0.8.3

Initial (de)serialization, some implicits/levels/normalization/unification
relevant bug fixes, removed builtin hlevel, improved error report
(some metas are now inlined), migrated to java 17, replaced some
visitors with pattern matching, tycker is finally bidirectional,
removed tgbot subproject.

## 0.8.2

Some cosmetic improvements to Aya, such as literature mode,
type theoretical enhancements, test refactorings, etc.

## 0.8.1

Pretty printing (including the framework) enhancements,
level solver enhancements, publish to mavencentral

## 0.8

This is a quick release after 0.7,
with some improvements on implemented features

## 0.7

Goal: everything else we need to prepare for being used by other people.

Roadmap: TDD -> library system -> user goals and VSCode LSP ->
packaging Aya as an application that can be installed on a Java-free
machine -> dependency management -> ...

Work done: insertion of implicit at tail, make `RefTerm` typed,
remove termdsl test framework, implement level hierarchy, its inference
and a solver properly, initial `GetTypeVisitor`, make lsp work on
Windows, simplify defeq, multi-location error report, fat jar,
initial binop, unify `new` terms, jlink, remove backslashes in keywords,
delayed error report, chained projection

## 0.6

Primitive definitions, intervals and Arend coercion, confluence for
conditions, proper absurd patterns, proper holes and pattern unification,
new expressions for records (support a very naive binding technique),
records with conditions, LaTeX backend, internal refactoring

## 0.5

Coverage and confluence check, prepare for local modules, records
(incomplete), indexed types, styled Doc with HTML backend,
simple unification, qqbot as an optional subproject, upgrade to Java 16 and Gradle 7.0-RC-1,
tons of bug fixes including `LocalVar` comparison bug and term comparison bug

## 0.4

Definition by pattern matching, conditions, removed `\elim` keyword,
telegram bot, internal refactorings (most notably patterns and decls and defs)

## 0.3

Inductive types resolve/tycking, fncall, improved CLI, tracing for tycking
(both markdown and imgui based visualization), license changed to GPLv3,
desugar refactoring, file-based test framework, tons of bug fixes, rename mzi -> aya

## 0.2

Module system, declaration resolution, definition tycking,
simple CLI that just works, simple pattern unification

## 0.1

Expression tycking, simple DTLC+pi+sigma, expression resolution, simple conversion check
