// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.term;

import org.aya.api.ref.DefVar;
import org.aya.api.ref.HoleVar;
import org.aya.api.ref.LocalVar;
import org.aya.api.ref.Var;
import org.aya.api.util.Arg;
import org.aya.concrete.Decl;
import org.aya.core.Meta;
import org.aya.core.def.DataDef;
import org.aya.core.def.FnDef;
import org.aya.core.def.PrimDef;
import org.aya.core.def.StructDef;
import org.aya.core.visitor.Substituter;
import org.aya.util.Constants;
import org.aya.util.Decision;
import org.glavo.kala.collection.SeqView;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 * @see CallTerm#make(Term, Arg)
 */
public sealed interface CallTerm extends Term {
  @NotNull Var ref();
  @NotNull ImmutableSeq<@NotNull Arg<Term>> contextArgs();
  @NotNull ImmutableSeq<@NotNull Arg<Term>> args();
  default @NotNull SeqView<@NotNull Arg<Term>> fullArgs() {
    return contextArgs().view().concat(args());
  }

  @Contract(pure = true) static @NotNull Term make(@NotNull Term f, @NotNull Arg<Term> arg) {
    if (f instanceof Hole hole) {
      var ret = hole.ref.core().result;
      if (ret instanceof FormTerm.Pi pi) {
        var paramRef = new LocalVar(Constants.ANONYMOUS_PREFIX);
        hole.ref.core().telescope.append(new Term.Param(
          paramRef,
          pi.param().type(),
          arg.explicit()
        ));
        hole.ref.core().result = pi.body().subst(pi.param().ref(), new RefTerm(paramRef));
      }
      return new Hole(hole.ref(), hole.contextArgs(), hole.args.appended(arg));
    }
    if (!(f instanceof IntroTerm.Lambda lam)) return new ElimTerm.App(f, arg);
    var param = lam.param();
    return lam.body().subst(new Substituter.TermSubst(param.ref(), arg.term()));
  }

  record Fn(
    @NotNull DefVar<FnDef, Decl.FnDecl> ref,
    @NotNull ImmutableSeq<Arg<@NotNull Term>> contextArgs,
    @NotNull ImmutableSeq<Arg<@NotNull Term>> args
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

  record Prim(
    @NotNull DefVar<PrimDef, Decl.PrimDecl> ref,
    @NotNull ImmutableSeq<Arg<@NotNull Term>> args
  ) implements CallTerm {
    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitPrimCall(this, p);
    }

    @Override
    public @NotNull ImmutableSeq<@NotNull Arg<Term>> contextArgs() {
      return ImmutableSeq.of();
    }

    @Override public <P, Q, R> R doAccept(@NotNull BiVisitor<P, Q, R> visitor, P p, Q q) {
      return visitor.visitPrimCall(this, p, q);
    }

    @Contract(pure = true) @Override public @NotNull Decision whnf() {
      if (args.isEmpty()) return Decision.YES;
      return Decision.MAYBE;
    }
  }

  record Data(
    @NotNull DefVar<DataDef, Decl.DataDecl> ref,
    @NotNull ImmutableSeq<Arg<@NotNull Term>> contextArgs,
    @NotNull ImmutableSeq<Arg<@NotNull Term>> args
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

    public @NotNull ConHead conHead(@NotNull DefVar<DataDef.Ctor, Decl.DataCtor> ctorRef) {
      return new ConHead(ref, ctorRef, contextArgs, args);
    }
  }

  /**
   * @author kiva
   */
  record Struct(
    @NotNull DefVar<StructDef, Decl.StructDecl> ref,
    @NotNull ImmutableSeq<Arg<@NotNull Term>> contextArgs,
    @NotNull ImmutableSeq<Arg<@NotNull Term>> args
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
    @NotNull ImmutableSeq<Arg<@NotNull Term>> contextArgs,
    @NotNull ImmutableSeq<Arg<@NotNull Term>> dataArgs
  ) {
    public @NotNull Data underlyingDataCall() {
      return new Data(dataRef, contextArgs, dataArgs);
    }
  }

  record Con(
    @NotNull ConHead head,
    @NotNull ImmutableSeq<Arg<Term>> conArgs
  ) implements CallTerm {
    public Con(
      @NotNull DefVar<DataDef, Decl.DataDecl> dataRef,
      @NotNull DefVar<DataDef.Ctor, Decl.DataCtor> ref,
      @NotNull ImmutableSeq<Arg<@NotNull Term>> contextArgs,
      @NotNull ImmutableSeq<Arg<@NotNull Term>> dataArgs,
      @NotNull ImmutableSeq<Arg<@NotNull Term>> conArgs
    ) {
      this(new ConHead(dataRef, ref, contextArgs, dataArgs), conArgs);
    }

    @Override public @NotNull DefVar<DataDef.Ctor, Decl.DataCtor> ref() {
      return head().ref;
    }

    @Override
    public @NotNull ImmutableSeq<@NotNull Arg<Term>> contextArgs() {
      return head.contextArgs();
    }

    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitConCall(this, p);
    }

    @Override public <P, Q, R> R doAccept(@NotNull BiVisitor<P, Q, R> visitor, P p, Q q) {
      return visitor.visitConCall(this, p, q);
    }

    @Override public @NotNull ImmutableSeq<Arg<@NotNull Term>> args() {
      return head.dataArgs.view().concat(conArgs).toImmutableSeq();
    }

    @Contract(pure = true) @Override public @NotNull Decision whnf() {
      var core = head.ref.core;
      if (core == null) return Decision.YES;
      if (core.clauses().isNotEmpty()) return Decision.NO;
      return Decision.MAYBE;
    }
  }

  /**
   * @author ice1000
   */
  record Hole(
    @NotNull HoleVar<Meta> ref,
    @NotNull ImmutableSeq<Arg<@NotNull Term>> contextArgs,
    @NotNull ImmutableSeq<@NotNull Arg<Term>> args
  ) implements CallTerm {
    public Hole(@NotNull HoleVar<Meta> var, @NotNull ImmutableSeq<Arg<@NotNull Term>> contextArgs) {
      this(var, contextArgs, ImmutableSeq.of());
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
