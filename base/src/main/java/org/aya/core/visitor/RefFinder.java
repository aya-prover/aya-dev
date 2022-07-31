// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.visitor;

import kala.collection.SeqLike;
import kala.collection.mutable.MutableList;
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
  Def.Visitor<@NotNull MutableList<Def>, Unit>,
  VarConsumer<@NotNull MutableList<Def>> {
  public static final @NotNull RefFinder HEADER_ONLY = new RefFinder(false);
  public static final @NotNull RefFinder HEADER_AND_BODY = new RefFinder(true);

  @Override public void visitVar(Var usage, @NotNull MutableList<Def> defs) {
    if (usage instanceof DefVar<?, ?> ref && ref.core instanceof Def def) defs.append(def);
  }

  @Override public Unit visitFn(@NotNull FnDef fn, @NotNull MutableList<Def> references) {
    tele(references, fn.telescope());
    fn.result().accept(this, references);
    if (withBody) fn.body.map(
      term -> term.accept(this, references),
      clauses -> {
        clauses.forEach(clause -> matchy(clause, references));
        return Unit.unit();
      });
    return Unit.unit();
  }

  @Override public Unit visitCtor(@NotNull CtorDef def, @NotNull MutableList<Def> references) {
    tele(references, def.selfTele);
    if (withBody) for (var clause : def.clauses) matchy(clause, references);
    return Unit.unit();
  }

  @Override public Unit visitStruct(@NotNull StructDef def, @NotNull MutableList<Def> references) {
    def.result().accept(this, references);
    if (withBody) def.fields.forEach(t -> t.accept(this, references));
    return Unit.unit();
  }

  @Override public Unit visitField(@NotNull FieldDef def, @NotNull MutableList<Def> references) {
    tele(references, def.telescope());
    def.body.forEach(t -> t.accept(this, references));
    def.result().accept(this, references);
    if (withBody) for (var clause : def.clauses) clause.body().accept(this, references);
    return Unit.unit();
  }

  @Override public Unit visitPrim(@NotNull PrimDef def, @NotNull MutableList<Def> defs) {
    tele(defs, def.telescope());
    return Unit.unit();
  }

  @Override public Unit visitData(@NotNull DataDef def, @NotNull MutableList<Def> references) {
    tele(references, def.telescope());
    def.result().accept(this, references);
    if (withBody) def.body.forEach(t -> t.accept(this, references));
    return Unit.unit();
  }

  public void matchy(@NotNull Matching match, @NotNull MutableList<Def> defs) {
    match.body().accept(this, defs);
  }

  private void tele(@NotNull MutableList<Def> references, @NotNull SeqLike<Term.Param> telescope) {
    telescope.forEach(param -> param.type().accept(this, references));
  }
}
