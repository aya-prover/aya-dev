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
import java.util.function.Consumer;

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

  @Override public void descentConsume(@NotNull Consumer<Term> f, @NotNull Consumer<Pat> g) {
    telescope.forEach(p -> p.descentConsume(f));
    f.accept(result);
    body.forEach(f, matchings -> matchings.forEach(m -> m.descentConsume(f, g)));
  }
}
