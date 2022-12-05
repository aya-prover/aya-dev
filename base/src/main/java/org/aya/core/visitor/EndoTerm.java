// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.visitor;

import kala.collection.mutable.MutableMap;
import org.aya.core.term.*;
import org.aya.generic.util.InternalException;
import org.aya.ref.AnyVar;
import org.aya.ref.LocalVar;
import org.jetbrains.annotations.NotNull;

import java.util.function.UnaryOperator;

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
public interface EndoTerm extends UnaryOperator<Term> {
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
  record Renamer(@NotNull Subst subst) implements EndoTerm {
    public Renamer() {
      this(new Subst(MutableMap.create()));
    }

    private @NotNull Term.Param handleBinder(@NotNull Term.Param param) {
      var v = param.renameVar();
      subst.addDirectly(param.ref(), new RefTerm(v));
      return new Term.Param(v, param.type(), param.explicit());
    }

    private @NotNull LocalVar handleBinder(@NotNull LocalVar localVar) {
      var v = localVar.rename();
      subst.addDirectly(localVar, new RefTerm(v));
      return v;
    }

    @Override public @NotNull Term pre(@NotNull Term term) {
      return switch (term) {
        case LamTerm lambda -> new LamTerm(handleBinder(lambda.param()), lambda.body());
        case PiTerm pi -> new PiTerm(handleBinder(pi.param()), pi.body());
        case SigmaTerm sigma -> new SigmaTerm(sigma.params().map(this::handleBinder));
        case RefTerm ref -> subst.map().getOrDefault(ref.var(), ref);
        case RefTerm.Field field -> subst.map().getOrDefault(field.ref(), field);
        case PathTerm path -> new PathTerm(new PathTerm.Cube(
          path.cube().params().map(this::handleBinder),
          path.cube().type(),
          path.cube().partial()));
        case PLamTerm lam -> new PLamTerm(
          lam.params().map(this::handleBinder),
          lam.body());
        case Term misc -> misc;
      };
    }
  }

  /**
   * Performs capture-avoiding substitution.
   */
  record Substituter(@NotNull Subst subst) implements BetaExpander {
    @Override public @NotNull Term post(@NotNull Term term) {
      return switch (term) {
        case RefTerm ref when ref.var() == LocalVar.IGNORED -> throw new InternalException("found usage of ignored var");
        case RefTerm ref -> replacement(ref, ref.var());
        case RefTerm.Field field -> replacement(field, field.ref());
        case Term misc -> BetaExpander.super.post(misc);
      };
    }

    private Term replacement(Term field, @NotNull AnyVar ref) {
      return subst.map().getOption(ref).map(Term::rename).getOrDefault(field);
    }
  }

  /** A lift but in American English. */
  record Elevator(int lift) implements EndoTerm {
    @Override public @NotNull Term apply(@NotNull Term term) {
      if (lift == 0) return term;
      return EndoTerm.super.apply(term);
    }

    @Override public @NotNull Term post(@NotNull Term term) {
      return switch (term) {
        case SortTerm sort -> sort.elevate(lift);
        case StructCall struct -> new StructCall(struct.ref(), struct.ulift() + lift, struct.args());
        case DataCall data -> new DataCall(data.ref(), data.ulift() + lift, data.args());
        case ConCall con -> {
          var head = con.head();
          head = new ConCall.Head(head.dataRef(), head.ref(), head.ulift() + lift, head.dataArgs());
          yield new ConCall(head, con.conArgs());
        }
        case FnCall fn -> new FnCall(fn.ref(), fn.ulift() + lift, fn.args());
        case PrimCall prim -> new PrimCall(prim.ref(), prim.ulift() + lift, prim.args());
        case MetaTerm hole -> new MetaTerm(hole.ref(), hole.contextArgs(), hole.args());
        case Term misc -> misc;
      };
    }
  }
}
