// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.def;

import kala.collection.immutable.ImmutableSeq;
import kala.control.Either;
import org.aya.concrete.stmt.decl.TeleDecl;
import org.aya.core.pat.Pat;
import org.aya.core.term.Term;
import org.aya.generic.Modifier;
import org.aya.ref.DefVar;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;

/**
 * @author ice1000
 */
public final class FnDef extends UserDef<Term> {
  public final @NotNull EnumSet<Modifier> modifiers;
  public final @NotNull DefVar<FnDef, TeleDecl.FnDecl> ref;
  public final @NotNull Either<Term, ImmutableSeq<Term.Matching>> body;

  public FnDef(
    @NotNull DefVar<FnDef, TeleDecl.FnDecl> ref, @NotNull ImmutableSeq<Term.Param> telescope,
    @NotNull Term result,
    @NotNull EnumSet<Modifier> modifiers,
    @NotNull Either<Term, ImmutableSeq<Term.Matching>> body
  ) {
    super(telescope, result);
    this.modifiers = modifiers;
    ref.core = this;
    this.ref = ref;
    this.body = body;
  }

  public static <T> BiFunction<Term, Either<Term, ImmutableSeq<Term.Matching>>, T>
  factory(BiFunction<Term, Either<Term, ImmutableSeq<Term.Matching>>, T> function) {
    return function;
  }

  public @NotNull DefVar<FnDef, TeleDecl.FnDecl> ref() {
    return ref;
  }

  public @NotNull FnDef update(@NotNull ImmutableSeq<Term.Param> telescope, @NotNull Term result, @NotNull Either<Term, ImmutableSeq<Term.Matching>> body) {
    // TODO: Better comparison of `body`?
    return telescope.sameElements(telescope(), true) && result == result() && body.equals(this.body)
      ? this : new FnDef(ref, telescope, result, modifiers, body);
  }

  @Override public @NotNull FnDef descent(@NotNull UnaryOperator<Term> f, @NotNull UnaryOperator<Pat> g) {
    return update(telescope.map(p -> p.descent(f)), f.apply(result), body.map(f, matchings -> matchings.map(m -> m.descent(f, g))));
  }

  // TODO: HACK! Special method to support hooking into the transformation of `Matching`
  //   This allows `CallResolver` to check termination based on the top level pattern clauses.
  //   But we now also support pattern matching within terms, so is this rather fragile?
  public @NotNull FnDef descent(@NotNull UnaryOperator<Term> f, @NotNull UnaryOperator<Pat> g, @NotNull UnaryOperator<Term.Matching> h) {
    return update(telescope.map(p -> p.descent(f)), f.apply(result), body.map(f, matchings -> matchings.map(h)));
  }
}
