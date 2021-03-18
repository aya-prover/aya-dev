// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.term;

import org.aya.api.ref.DefVar;
import org.aya.api.ref.Var;
import org.aya.concrete.Decl;
import org.aya.core.def.DataDef;
import org.aya.core.def.FnDef;
import org.aya.core.def.StructDef;
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
 * @see CallTerm#make(Term, Arg)
 */
public sealed interface CallTerm extends Term {
  @NotNull Var fn();
  @NotNull SeqLike<@NotNull ? extends @NotNull Arg<? extends Term>> args();

  @Contract(pure = true) static @NotNull Term make(@NotNull Term f, @NotNull Arg<Term> arg) {
    if (f instanceof Hole hole) {
      hole.argsBuf().append(arg);
      return hole;
    }
    if (!(f instanceof LamTerm lam)) return new AppTerm(f, arg);
    var param = lam.param();
    return lam.body().subst(new Substituter.TermSubst(param.ref(), arg.term()));
  }

  @Contract(pure = true) static @NotNull Term make(@NotNull Term f, @NotNull SeqLike<Arg<Term>> args) {
    if (args.isEmpty()) return f;
    if (f instanceof Hole hole) {
      hole.argsBuf().appendAll(args.view());
      return hole;
    }
    if (!(f instanceof LamTerm lam)) return make(new AppTerm(f, args.first()), args.view().drop(1));
    return make(make(lam, args.first()), args.view().drop(1));
  }

  record Fn(
    @NotNull DefVar<FnDef, Decl.FnDecl> fnRef,
    @NotNull SeqLike<Arg<@NotNull Term>> contextArgs,
    @NotNull SeqLike<Arg<@NotNull Term>> args
  ) implements CallTerm {
    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitFnCall(this, p);
    }

    @Override public <P, Q, R> R doAccept(@NotNull BiVisitor<P, Q, R> visitor, P p, Q q) {
      return visitor.visitFnCall(this, p, q);
    }

    @Contract(pure = true) @Override public @NotNull Decision whnf() {
      // Recursive case is irreducible
      if (fnRef.core == null) return Decision.YES;
      // TODO[xyr]: after adding inductive datatypes, we need to check if the function pattern matches.
      return Decision.NO;
    }

    @Contract(value = " -> new", pure = true)
    @Override public @NotNull Var fn() {
      return fnRef;
    }
  }

  record Data(
    @NotNull DefVar<DataDef, Decl.DataDecl> dataRef,
    @NotNull SeqLike<Arg<@NotNull Term>> contextArgs,
    @NotNull SeqLike<Arg<@NotNull Term>> args
  ) implements CallTerm {
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
    @Override public @NotNull Var fn() {
      return dataRef;
    }

    public @NotNull SeqView<DataDef.@NotNull Ctor> availableCtors() {
      return dataRef.core.ctors();
    }
  }

  /**
   * @author kiva
   */
  record Struct(
    @NotNull DefVar<StructDef, Decl.StructDecl> structRef,
    @NotNull SeqLike<Arg<@NotNull Term>> contextArgs,
    @NotNull SeqLike<Arg<@NotNull Term>> args
  ) implements CallTerm {
    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitStructCall(this, p);
    }

    @Override public <P, Q, R> R doAccept(@NotNull BiVisitor<P, Q, R> visitor, P p, Q q) {
      return visitor.visitStructCall(this, p, q);
    }

    @Contract(pure = true) @Override public @NotNull Decision whnf() {
      return Decision.YES;
    }

    @Contract(value = " -> new", pure = true)
    @Override public @NotNull Var fn() {
      return structRef;
    }
  }

  record Con(
    @NotNull DefVar<DataDef.Ctor, Decl.DataCtor> conHead,
    @NotNull SeqLike<Arg<@NotNull Term>> contextArgs,
    @NotNull SeqLike<Arg<Term>> dataArgs,
    @NotNull SeqLike<Arg<Term>> conArgs
  ) implements CallTerm {
    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitConCall(this, p);
    }

    @Override public <P, Q, R> R doAccept(@NotNull BiVisitor<P, Q, R> visitor, P p, Q q) {
      return visitor.visitConCall(this, p, q);
    }

    public @NotNull DefVar<DataDef, Decl.DataDecl> dataRef() {
      return DataDef.fromCtor(conHead);
    }

    @Override public @NotNull SeqView<Arg<Term>> args() {
      return dataArgs.view().concat(conArgs.view());
    }

    @Contract(pure = true) @Override public @NotNull Decision whnf() {
      if (conHead.core == null) return Decision.YES;
      if (!conHead.core.clauses().isEmpty()) return Decision.NO;
      return Decision.YES;
    }

    @Contract(value = " -> new", pure = true)
    @Override public @NotNull Var fn() {
      return conHead;
    }
  }

  /**
   * @author ice1000
   */
  record Hole(
    @NotNull Var var,
    @NotNull Buffer<@NotNull Arg<Term>> argsBuf
  ) implements CallTerm {
    public Hole(@NotNull Var var) {
      this(var, Buffer.of());
    }

    @Override public @NotNull Seq<Arg<Term>> args() {
      return argsBuf;
    }

    @Contract(" -> new") @Override public @NotNull Var fn() {
      return var;
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
