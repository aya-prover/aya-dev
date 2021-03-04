// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.term;

import org.aya.api.ref.DefVar;
import org.aya.api.ref.Var;
import org.aya.concrete.Decl;
import org.aya.core.def.DataDef;
import org.aya.core.def.FnDef;
import org.aya.core.visitor.Substituter;
import org.aya.generic.Arg;
import org.aya.util.Decision;
import org.glavo.kala.collection.Seq;
import org.glavo.kala.collection.SeqLike;
import org.glavo.kala.collection.SeqView;
import org.glavo.kala.collection.mutable.Buffer;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 * @see AppTerm#make(Term, Arg)
 */
public sealed interface AppTerm extends Term {
  @NotNull Term fn();
  @NotNull SeqLike<@NotNull ? extends @NotNull Arg<? extends Term>> args();

  @Contract(pure = true) static @NotNull Term make(@NotNull Term f, @NotNull Arg<Term> arg) {
    if (f instanceof HoleApp holeApp) {
      holeApp.argsBuf().append(arg);
      return holeApp;
    }
    if (!(f instanceof LamTerm lam)) return new Apply(f, arg);
    var param = lam.param();
    return lam.body().subst(new Substituter.TermSubst(param.ref(), arg.term()));
  }

   @Contract(pure = true) static @NotNull Term make(@NotNull Term f, @NotNull SeqLike<Arg<Term>> args) {
    if (args.isEmpty()) return f;
    if (f instanceof HoleApp holeApp) {
      holeApp.argsBuf().appendAll(args.view());
      return holeApp;
    }
    if (!(f instanceof LamTerm lam)) return make(new Apply(f, args.first()), args.view().drop(1));
    return make(make(lam, args.first()), args.view().drop(1));
  }

  record FnCall(
    @NotNull DefVar<FnDef, Decl.FnDecl> fnRef,
    @NotNull SeqLike<Arg<Term>> args
  ) implements AppTerm {
    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitFnCall(this, p);
    }

    @Override public <P, Q, R> R doAccept(@NotNull BiVisitor<P, Q, R> visitor, P p, Q q) {
      return visitor.visitFnCall(this, p, q);
    }

    @Contract(pure = true) @Override public @NotNull Decision whnf() {
      // TODO[xyr]: after adding inductive datatypes, we need to check if the function pattern matches.
      return Decision.NO;
    }

    @Contract(value = " -> new", pure = true)
    @Override public @NotNull Term fn() {
      return new RefTerm(fnRef);
    }
  }

  record DataCall(
    @NotNull DefVar<DataDef, Decl.DataDecl> dataRef,
    @NotNull SeqLike<Arg<Term>> args
  ) implements AppTerm {
    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitDataCall(this, p);
    }

    @Override public <P, Q, R> R doAccept(@NotNull BiVisitor<P, Q, R> visitor, P p, Q q) {
      return visitor.visitDataCall(this, p, q);
    }

    @Contract(pure = true) @Override public @NotNull Decision whnf() {
      return Decision.YES;
    }

    @Contract(value = " -> new", pure = true)
    @Override public @NotNull Term fn() {
      return new RefTerm(dataRef);
    }
  }

  record ConCall(
    @NotNull DefVar<DataDef.Ctor, Decl.DataCtor> conHead,
    @NotNull SeqLike<Arg<Term>> dataArgs,
    @NotNull SeqLike<Arg<Term>> conArgs
  ) implements AppTerm {
    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitConCall(this, p);
    }

    @Override public <P, Q, R> R doAccept(@NotNull BiVisitor<P, Q, R> visitor, P p, Q q) {
      return visitor.visitConCall(this, p, q);
    }

    @Override public @NotNull SeqView<Arg<Term>> args() {
      return dataArgs.view().concat(conArgs.view());
    }

    @Contract(pure = true) @Override public @NotNull Decision whnf() {
      // TODO[ice]: conditions
      return Decision.YES;
    }

    @Contract(value = " -> new", pure = true)
    @Override public @NotNull Term fn() {
      return new RefTerm(conHead);
    }
  }

  record Apply(
    @NotNull Term fn,
    @NotNull Arg<Term> arg
  ) implements AppTerm {
    @Contract(pure = true) @Override public @NotNull Decision whnf() {
      if (fn() instanceof LamTerm) return Decision.NO;
      return fn().whnf();
    }

    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitApp(this, p);
    }

    @Override public <P, Q, R> R doAccept(@NotNull BiVisitor<P, Q, R> visitor, P p, Q q) {
      return visitor.visitApp(this, p, q);
    }

    @Contract(" -> new")
    @Override public @NotNull Seq<@NotNull Arg<? extends Term>> args() {
      return Seq.of(arg());
    }
  }

  /**
   * @author ice1000
   */
  record HoleApp(
    @NotNull Var var,
    @NotNull Buffer<@NotNull Arg<Term>> argsBuf
  ) implements AppTerm {
    public HoleApp(@NotNull Var var) {
      this(var, Buffer.of());
    }

    @Override public @NotNull Seq<Arg<Term>> args() {
      return argsBuf;
    }

    @Contract(" -> new") @Override public @NotNull Term fn() {
      return new RefTerm(var);
    }

    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitHole(this, p);
    }

    @Override public <P, Q, R> R doAccept(@NotNull BiVisitor<P, Q, R> visitor, P p, Q q) {
      return visitor.visitHole(this, p, q);
    }

    @Contract(pure = true) @Override public @NotNull Decision whnf() {
      return Decision.MAYBE;
    }
  }
}
