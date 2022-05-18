// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.visitor;

import kala.collection.immutable.ImmutableSeq;
import kala.tuple.Unit;
import org.aya.core.Matching;
import org.aya.core.def.*;
import org.aya.core.pat.Pat;
import org.aya.core.term.Term;
import org.jetbrains.annotations.NotNull;

public interface DefConsumer<P> extends GenericDef.Visitor<P, Unit>, TermConsumer<P> {
  private void tele(@NotNull ImmutableSeq<Term.Param> tele, P p) {
    tele.forEach(param -> param.type().accept(this, p));
  }

  private void visitDef(@NotNull GenericDef def, P p) {
    if(def instanceof Def defwithTele) tele(defwithTele.telescope(), p);
    def.result().accept(this, p);
  }

  default void visitMatching(@NotNull Matching matching, P p) {
    matching.patterns().forEach(pat -> visitPat(pat, p));
    matching.body().accept(this, p);
  }

  default void visitPat(@NotNull Pat pat, P p) {
    switch (pat) {
      case Pat.Ctor ctor -> ctor.params().forEach(param -> visitPat(param, p));
      case Pat.Tuple tuple -> tuple.pats().forEach(param -> visitPat(param, p));
      default -> {}
    }
  }

  @Override default Unit visitFn(@NotNull FnDef def, P p) {
    visitDef(def, p);
    def.body.forEach(
      body -> body.accept(this, p),
      matchings -> matchings.forEach(m -> visitMatching(m, p)));
    return Unit.unit();
  }

  @Override default Unit visitData(@NotNull DataDef def, P p) {
    visitDef(def, p);
    def.body.forEach(ctor -> visitCtor(ctor, p));
    return Unit.unit();
  }

  @Override default Unit visitStruct(@NotNull StructDef def, P p) {
    visitDef(def, p);
    def.parents.forEach(parent -> visitStructCall(parent, p));
    def.fields.forEach(field -> field.accept(this, p));
    return Unit.unit();
  }

  @Override default Unit visitCtor(@NotNull CtorDef def, P p) {
    def.pats.forEach(pat -> visitPat(pat, p));
    tele(def.selfTele, p);
    def.result.accept(this, p);
    def.clauses.forEach(m -> visitMatching(m, p));
    return Unit.unit();
  }

  @Override default Unit visitField(@NotNull FieldDef def, P p) {
    tele(def.selfTele, p);
    def.result.accept(this, p);
    def.clauses.forEach(m -> visitMatching(m, p));
    def.body.forEach(body -> body.accept(this, p));
    return Unit.unit();
  }

  @Override default Unit visitPrim(@NotNull PrimDef def, P p) {
    visitDef(def, p);
    return Unit.unit();
  }
}
