// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.core.term;

import asia.kala.collection.immutable.ImmutableSeq;
import asia.kala.collection.mutable.Buffer;
import asia.kala.control.Option;
import asia.kala.ref.OptionRef;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mzi.api.ref.Var;
import org.mzi.core.def.FnDef;
import org.mzi.core.visitor.SubstFixpoint;
import org.mzi.generic.Arg;
import org.mzi.ref.DefVar;
import org.mzi.util.Decision;

/**
 * @author ice1000
 * @see org.mzi.core.term.AppTerm#make(Term, Arg)
 */
public sealed interface AppTerm extends Term {
  @NotNull Term fn();
  @NotNull ImmutableSeq<@NotNull ? extends @NotNull Arg<? extends Term>> args();

  @Override default @NotNull Decision whnf() {
    if (fn() instanceof LamTerm) return Decision.NO;
    return fn().whnf();
  }

  @Contract(pure = true) static @NotNull Term make(@NotNull Term f, @NotNull Arg<? extends Term> arg) {
    if (f instanceof HoleApp holeApp) {
      holeApp.argsBuf().append(Arg.uncapture(arg));
      return holeApp;
    }
    if (!(f instanceof LamTerm lam)) return new Apply(f, arg);
    var tele = lam.tele();
    var next = tele.next();
    return (next != null ? new LamTerm(next, lam.body()) : lam.body()).subst(new SubstFixpoint.TermSubst(tele.ref(), arg.term()));
  }

  record FnCall(
    @NotNull DefVar<FnDef> fnRef,
    @NotNull ImmutableSeq<@NotNull ? extends @NotNull Arg<? extends Term>> args
  ) implements AppTerm {
    @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitFnCall(this, p);
    }

    @Override public <P, Q, R> R accept(@NotNull BiVisitor<P, Q, R> visitor, P p, Q q) {
      return visitor.visitFnCall(this, p, q);
    }

    @Contract(value = " -> new", pure = true)
    @Override public @NotNull Term fn() {
      return new RefTerm(fnRef);
    }
  }

  record Apply(
    @NotNull Term fn,
    @NotNull Arg<? extends Term> arg
  ) implements AppTerm {
    @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitApp(this, p);
    }

    @Override public <P, Q, R> R accept(@NotNull BiVisitor<P, Q, R> visitor, P p, Q q) {
      return visitor.visitApp(this, p, q);
    }

    @Contract(" -> new")
    @Override public @NotNull ImmutableSeq<@NotNull Arg<? extends Term>> args() {
      return ImmutableSeq.of(arg());
    }
  }

  /**
   * @author ice1000
   */
  record HoleApp(
    @NotNull OptionRef<@NotNull Term> solution,
    @NotNull Var var,
    @NotNull Buffer<@NotNull Arg<Term>> argsBuf
  ) implements AppTerm {
    public HoleApp(@Nullable Term solution, @NotNull Var var,
                   @NotNull Buffer<@NotNull Arg<Term>> args) {
      this(new OptionRef<>(Option.of(solution)), var, args);
    }

    @Override @Deprecated
    public @NotNull ImmutableSeq<@NotNull ? extends @NotNull Arg<? extends Term>> args() {
      return argsBuf.view().collect(ImmutableSeq.factory());
    }

    @Contract(" -> new") @Override public @NotNull Term fn() {
      return new RefTerm(var);
    }

    @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitHole(this, p);
    }

    @Override public <P, Q, R> R accept(@NotNull BiVisitor<P, Q, R> visitor, P p, Q q) {
      return visitor.visitHole(this, p, q);
    }

    @Contract(pure = true) @Override public @NotNull Decision whnf() {
      return Decision.MAYBE;
    }
  }
}
