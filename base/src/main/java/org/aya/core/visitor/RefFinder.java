// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.visitor;

import kala.collection.SeqLike;
import kala.collection.SeqView;
import kala.tuple.Unit;
import org.aya.core.Matching;
import org.aya.core.def.*;
import org.aya.core.term.Term;
import org.aya.ref.DefVar;
import org.aya.ref.Var;
import org.jetbrains.annotations.NotNull;

/**
 * @author re-xyr, ice1000
 * @see RefFinder#HEADER_ONLY
 * @see RefFinder#HEADER_AND_BODY
 */
public record RefFinder(boolean withBody) implements
  Def.Visitor<Unit, SeqView<Def>>,
  MonoidalVarFolder<@NotNull SeqView<Def>> {
  public static final @NotNull RefFinder HEADER_ONLY = new RefFinder(false);
  public static final @NotNull RefFinder HEADER_AND_BODY = new RefFinder(true);

  @Override public SeqView<Def> var(Var usage) {
    return usage instanceof DefVar<?, ?> ref && ref.core instanceof Def def ? SeqView.of(def) : SeqView.empty();
  }

  @Override public SeqView<Def> visitFn(@NotNull FnDef fn, Unit unit) {
    return tele(fn.telescope)
      .concat(apply(fn.result()))
      .concat(withBody
        ? fn.body.fold(this, clauses -> clauses.view().flatMap(this::matchy))
        : SeqView.empty());
  }

  @Override public SeqView<Def> visitCtor(@NotNull CtorDef def, Unit unit) {
    return tele(def.selfTele).concat(withBody ? def.clauses.flatMap(this::matchy) : SeqView.empty());
  }

  @Override public SeqView<Def> visitStruct(@NotNull StructDef def, Unit unit) {
    return tele(def.telescope()).concat(withBody ? def.fields.flatMap(f -> visitField(f, unit)) : SeqView.empty());
  }

  @Override public SeqView<Def> visitField(@NotNull FieldDef def, Unit unit) {
    return tele(def.telescope())
      .concat(def.body.foldLeft(SeqView.empty(), (rs, body) -> apply(body)))
      .concat(apply(def.result()))
      .concat(withBody ? def.clauses.flatMap(this::matchy) : SeqView.empty());
  }

  @Override public SeqView<Def> visitPrim(@NotNull PrimDef def, Unit unit) {
    return tele(def.telescope());
  }

  @Override public SeqView<Def> visitData(@NotNull DataDef def, Unit unit) {
    return tele(def.telescope())
      .concat(apply(def.result()))
      .concat(withBody ? def.body.flatMap(t -> visitCtor(t, unit)) : SeqView.empty());
  }

  private SeqView<Def> matchy(@NotNull Matching match) {
    return apply(match.body());
  }

  private SeqView<Def> tele(SeqLike<Term.Param> telescope) {
    return telescope.view().map(Term.Param::type).flatMap(this);
  }

  @Override public @NotNull SeqView<Def> e() {
    return SeqView.empty();
  }

  @Override public @NotNull SeqView<Def> op(@NotNull SeqView<Def> a, @NotNull SeqView<Def> b) {
    return a.concat(b);
  }

  @Override public @NotNull SeqView<Def> ops(@NotNull SeqLike<SeqView<Def>> as) {
    return as.view().flatMap(a -> a);
  }
}
