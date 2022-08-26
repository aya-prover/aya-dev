// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.visitor;

import kala.collection.mutable.MutableMap;
import org.aya.core.term.*;
import org.aya.generic.util.InternalException;
import org.aya.ref.LocalVar;
import org.aya.util.distill.DistillerOptions;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

/**
 * A convenient interface to obtain an endomorphism on `Term`.
 * This is desirable when you need to transform a `Term` into another `Term`.
 * One can specify the `pre` and `post` method which represents a recursive step in pre- and post-order respectively.
 * The overall operation is obtained by recursively transforming the term in pre-order followed by a post-order transformation.
 * Note that the derived `accept` attempts to preserve object identity when possible,
 * hence the implementation of `pre` and `post` can take advantage of this behavior.
 *
 * @author wsx
 */
public interface EndoFunctor extends Function<Term, Term> {
  default @NotNull Term pre(@NotNull Term term) {
    return term;
  }

  default @NotNull Term post(@NotNull Term term) {
    return term;
  }

  default @NotNull Term apply(@NotNull Term term) {
    return post(pre(term).descent(this));
  }

  /** Not an IntelliJ Renamer. */
  record Renamer(@NotNull Subst subst) implements EndoFunctor {
    public Renamer() {
      this(new Subst(MutableMap.create()));
    }

    private @NotNull Term.Param handleBinder(@NotNull Term.Param param) {
      var v = param.renameVar();
      subst.addDirectly(param.ref(), new RefTerm(v));
      return new Term.Param(v, param.type(), param.explicit());
    }

    @Override public @NotNull Term pre(@NotNull Term term) {
      return switch (term) {
        case IntroTerm.Lambda lambda -> new IntroTerm.Lambda(handleBinder(lambda.param()), lambda.body());
        case FormTerm.Pi pi -> new FormTerm.Pi(handleBinder(pi.param()), pi.body());
        case FormTerm.Sigma sigma -> new FormTerm.Sigma(sigma.params().map(this::handleBinder));
        case RefTerm ref -> subst.map().getOrDefault(ref.var(), ref);
        case RefTerm.Field field -> subst.map().getOrDefault(field.ref(), field);
        case Term misc -> misc;
      };
    }
  }

  /**
   * Performs capture-avoiding substitution and applies beta reduction.
   */
  record Substituter(@NotNull Subst subst) implements EndoFunctor {
    @Override public @NotNull Term post(@NotNull Term term) {
      return switch (term) {
        case ElimTerm.App app && app.of() instanceof IntroTerm.Lambda lam -> apply(CallTerm.make(lam, app.arg()));
        case ElimTerm.Proj proj && proj.of() instanceof IntroTerm.Tuple tup -> {
          var ix = proj.ix();
          assert tup.items().sizeGreaterThanOrEquals(ix) && ix > 0 : proj.toDoc(DistillerOptions.debug()).debugRender();
          yield apply(tup.items().get(ix - 1));
        }
        case RefTerm ref && ref.var() == LocalVar.IGNORED -> throw new InternalException("found usage of ignored var");
        case RefTerm ref -> subst.map().getOption(ref.var()).map(Term::rename).getOrDefault(ref);
        case RefTerm.Field field -> subst.map().getOption(field.ref()).map(Term::rename).getOrDefault(field);
        case Term misc -> misc;
      };
    }
  }

  /** A lift but in American English. */
  record Elevator(int lift) implements EndoFunctor {
    @Override public @NotNull Term post(@NotNull Term term) {
      return switch (term) {
        case FormTerm.Univ univ -> new FormTerm.Univ(univ.lift() + lift);
        case CallTerm.Struct struct -> new CallTerm.Struct(struct.ref(), struct.ulift() + lift, struct.args());
        case CallTerm.Data data -> new CallTerm.Data(data.ref(), data.ulift() + lift, data.args());
        case CallTerm.Con con -> {
          var head = con.head();
          head = new CallTerm.ConHead(head.dataRef(), head.ref(), head.ulift() + lift, head.dataArgs());
          yield new CallTerm.Con(head, con.conArgs());
        }
        case CallTerm.Fn fn -> new CallTerm.Fn(fn.ref(), fn.ulift() + lift, fn.args());
        case CallTerm.Prim prim -> new CallTerm.Prim(prim.ref(), prim.ulift() + lift, prim.args());
        case CallTerm.Hole hole -> new CallTerm.Hole(hole.ref(), hole.ulift() + lift, hole.contextArgs(), hole.args());
        case Term misc -> misc;
      };
    }
  }
}
