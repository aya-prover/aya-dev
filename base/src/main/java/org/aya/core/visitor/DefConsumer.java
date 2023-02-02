// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.visitor;

import kala.collection.immutable.ImmutableSeq;
import org.aya.core.def.*;
import org.aya.core.pat.Pat;
import org.aya.core.term.Term;
import org.aya.guest0x0.cubical.Partial;
import org.aya.util.Arg;
import org.jetbrains.annotations.NotNull;

public interface DefConsumer extends TermConsumer {
  private void tele(@NotNull ImmutableSeq<Term.Param> tele) {
    tele.forEach(param -> this.accept(param.type()));
  }

  private void visitDef(@NotNull GenericDef def) {
    if (def instanceof Def dd) {
      tele(dd.telescope());
      this.accept(def.result());
    }
  }

  default void visitMatching(@NotNull Term.Matching matching) {
    matching.patterns().forEach(x -> visitPat(x.term()));
    this.accept(matching.body());
  }

  default void visitArgPat(@NotNull Arg<Pat> pat) {
    visitPat(pat.term());
  }

  default void visitPat(@NotNull Pat pat) {
    switch (pat) {
      case Pat.Ctor ctor -> ctor.params().forEach(this::visitArgPat);
      case Pat.Tuple tuple -> tuple.pats().forEach(this::visitArgPat);
      default -> {}
    }
  }

  default void accept(@NotNull GenericDef def) {
    switch (def) {
      case FnDef fn -> {
        visitDef(fn);
        fn.body.forEach(
          this,
          matchings -> matchings.forEach(this::visitMatching));
      }
      case DataDef data -> {
        visitDef(def);
        data.body.forEach(this::accept);
      }
      case StructDef struct -> {
        visitDef(def);
        struct.fields.forEach(this::accept);
      }
      case CtorDef ctor -> {
        ctor.pats.forEach(this::visitArgPat);
        tele(ctor.selfTele);
        accept(ctor.result);
        partial(ctor.clauses);
      }
      case FieldDef field -> {
        tele(field.selfTele);
        this.accept(field.result);
        field.body.forEach(this);
      }
      case PrimDef prim -> visitDef(prim);
      default -> {}
    }
  }
  default void partial(Partial<Term> clauses) {
    clauses.termsView().forEach(this);
  }
}
