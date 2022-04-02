// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.visitor;

import org.aya.core.term.*;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

public interface TermOps extends TermView {
  @NotNull TermView view();
  @Override default @NotNull Term initial() {
    return view().initial();
  }

  record Mapper(
    @Override @NotNull TermView view,
    @NotNull Function<@NotNull Term, @NotNull Term> pre,
    @NotNull Function<@NotNull Term, @NotNull Term> post
  ) implements TermOps {
    @Override public TermView preMap(Function<Term, Term> f) {
      return new Mapper(view, pre.compose(f), post);
    }

    @Override public TermView postMap(Function<Term, Term> f) {
      return new Mapper(view, pre, f.compose(post));
    }

    @Override public Term pre(Term term) {
      return view.pre(pre.apply(term));
    }

    @Override public Term post(Term term) {
      return post.apply(view.post(term));
    }
  }

  record Subster(@NotNull @Override TermView view, Subst subst) implements TermOps {
    @Override public TermView subst(Subst subst) {
      return new Subster(view, subst.add(subst));
    }

    @Override public Term post(Term term) {
      return switch (view.post(term)) {
        case RefTerm ref -> subst.map().getOption(ref.var()).map(Term::rename).getOrDefault(ref);
        case RefTerm.Field field -> subst.map().getOption(field.ref()).map(Term::rename).getOrDefault(field);
        case Term misc -> misc;
      };
    }
  }

  /** A lift but in American English. */
  record Elevator(@NotNull @Override TermView view, int ulift) implements TermOps {
    @Override public TermView lift(int shift) {
      return new Elevator(view, ulift + shift);
    }

    @Override public Term post(Term term) {
      // TODO: Implement the correct rules.
      return switch (view.post(term)) {
        case FormTerm.Univ univ -> new FormTerm.Univ(univ.lift() + ulift);
        case ElimTerm.Proj proj -> new ElimTerm.Proj(proj.of(), proj.ulift() + ulift, proj.ix());
        case CallTerm.Struct struct -> new CallTerm.Struct(struct.ref(), struct.ulift() + ulift, struct.args());
        case CallTerm.Data data -> new CallTerm.Data(data.ref(), data.ulift() + ulift, data.args());
        case CallTerm.Con con -> {
          var head = con.head();
          head = new CallTerm.ConHead(head.dataRef(), head.ref(), head.ulift() + ulift, head.dataArgs());
          yield new CallTerm.Con(head, con.conArgs());
        }
        case CallTerm.Fn fn -> new CallTerm.Fn(fn.ref(), fn.ulift() + ulift, fn.args());
        case CallTerm.Access access ->
          new CallTerm.Access(access.of(), access.ref(), access.ulift() + ulift, access.structArgs(), access.fieldArgs());
        case CallTerm.Prim prim -> new CallTerm.Prim(prim.ref(), prim.ulift() + ulift, prim.args());
        case CallTerm.Hole hole -> new CallTerm.Hole(hole.ref(), hole.ulift() + ulift, hole.contextArgs(), hole.args());
        case Term misc -> misc;
      };
    }
  }
}
