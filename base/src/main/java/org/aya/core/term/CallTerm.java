// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.term;

import org.aya.api.ref.DefVar;
import org.aya.api.ref.Var;
import org.aya.concrete.Decl;
import org.aya.core.def.DataDef;
import org.aya.core.def.FnDef;
import org.aya.core.def.StructDef;
import org.aya.core.pat.PatMatcher;
import org.aya.core.visitor.Substituter;
import org.aya.generic.Arg;
import org.aya.util.Decision;
import org.glavo.kala.collection.Seq;
import org.glavo.kala.collection.SeqLike;
import org.glavo.kala.collection.SeqView;
import org.glavo.kala.collection.mutable.Buffer;
import org.glavo.kala.tuple.Tuple;
import org.glavo.kala.tuple.Tuple2;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 * @see CallTerm#make(Term, Arg)
 */
public sealed interface CallTerm extends Term {
  @NotNull Var ref();
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
    @NotNull DefVar<FnDef, Decl.FnDecl> ref,
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
      if (ref.core == null) return Decision.YES;
      // TODO[xyr]: after adding inductive datatypes, we need to check if the function pattern matches.
      return Decision.NO;
    }
  }

  record Data(
    @NotNull DefVar<DataDef, Decl.DataDecl> ref,
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

    public @NotNull SeqView<Tuple2<DataDef.@NotNull Ctor, @NotNull ConHead>> availableCtors() {
      return ref.core.body().view().mapNotNull(t -> {
        var ctor = t.body().ref();
        if (t.patterns().isEmpty()) return Tuple.of(ctor.core, new ConHead(ref, ctor, contextArgs, args));
        var matchy = PatMatcher.tryBuildSubst(t.patterns(), args);
        if (matchy == null) return null;
        // TODO[ice]: apply subst
        return Tuple.of(ctor.core, new ConHead(this.ref, ctor, contextArgs, args));
      });
    }
  }

  /**
   * @author kiva
   */
  record Struct(
    @NotNull DefVar<StructDef, Decl.StructDecl> ref,
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
  }

  record ConHead(
    @NotNull DefVar<DataDef, Decl.DataDecl> dataRef,
    @NotNull DefVar<DataDef.Ctor, Decl.DataCtor> ref,
    @NotNull SeqLike<Arg<@NotNull Term>> contextArgs,
    @NotNull SeqLike<Arg<@NotNull Term>> dataArgs
  ) {
    public @NotNull Data underlyingDataCall() {
      return new Data(dataRef, contextArgs, dataArgs);
    }
  }

  record Con(
    @NotNull ConHead head,
    @NotNull SeqLike<Arg<Term>> conArgs
  ) implements CallTerm {
    public Con(
      @NotNull DefVar<DataDef, Decl.DataDecl> dataRef,
      @NotNull DefVar<DataDef.Ctor, Decl.DataCtor> ref,
      @NotNull SeqLike<Arg<@NotNull Term>> contextArgs,
      @NotNull SeqLike<Arg<@NotNull Term>> dataArgs,
      @NotNull SeqLike<Arg<@NotNull Term>> conArgs
    ) {
      this(new ConHead(dataRef, ref, contextArgs, dataArgs), conArgs);
    }

    @Override public @NotNull DefVar<DataDef.Ctor, Decl.DataCtor> ref() {
      return head().ref;
    }

    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitConCall(this, p);
    }

    @Override public <P, Q, R> R doAccept(@NotNull BiVisitor<P, Q, R> visitor, P p, Q q) {
      return visitor.visitConCall(this, p, q);
    }

    @Override public @NotNull SeqView<Arg<Term>> args() {
      return head.dataArgs.view().concat(conArgs.view());
    }

    @Contract(pure = true) @Override public @NotNull Decision whnf() {
      var core = head.ref.core;
      if (core == null) return Decision.YES;
      if (!core.clauses().isEmpty()) return Decision.NO;
      return Decision.MAYBE;
    }
  }

  /**
   * @author ice1000
   */
  record Hole(
    @NotNull Var ref,
    @NotNull Buffer<@NotNull Arg<Term>> argsBuf
  ) implements CallTerm {
    public Hole(@NotNull Var var) {
      this(var, Buffer.of());
    }

    @Override public @NotNull Seq<Arg<Term>> args() {
      return argsBuf;
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
