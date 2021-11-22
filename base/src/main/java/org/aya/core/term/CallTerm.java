// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import org.aya.api.ref.DefVar;
import org.aya.api.ref.Var;
import org.aya.api.util.Arg;
import org.aya.concrete.stmt.Decl;
import org.aya.concrete.stmt.Signatured;
import org.aya.core.Meta;
import org.aya.core.def.*;
import org.aya.core.sort.Sort;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 * @see CallTerm#make(Term, Arg)
 */
public sealed interface CallTerm extends Term {
  @NotNull Var ref();
  @NotNull ImmutableSeq<@NotNull Sort> sortArgs();
  @NotNull ImmutableSeq<@NotNull Arg<Term>> args();

  @FunctionalInterface
  interface Factory<D extends Def, S extends Signatured> {
    @Contract(pure = true, value = "_,_,_->new") @NotNull CallTerm make(
      DefVar<D, S> defVar,
      ImmutableSeq<@NotNull Sort> sortArgs,
      ImmutableSeq<@NotNull Arg<Term>> args
    );
  }

  @Contract(pure = true) static @NotNull Term
  make(@NotNull Term f, @NotNull Arg<Term> arg) {
    if (f instanceof Hole hole) {
      if (hole.args.sizeLessThan(hole.ref.telescope))
        return new Hole(hole.ref, hole.contextArgs, hole.args.appended(arg));
    }
    if (!(f instanceof IntroTerm.Lambda lam)) return new ElimTerm.App(f, arg);
    return make(lam, arg);
  }

  static @NotNull Term make(IntroTerm.Lambda lam, @NotNull Arg<Term> arg) {
    var param = lam.param();
    assert arg.explicit() == param.explicit();
    return lam.body().subst(param.ref(), arg.term());
  }

  record Fn(
    @NotNull DefVar<FnDef, Decl.FnDecl> ref,
    @NotNull ImmutableSeq<@NotNull Sort> sortArgs,
    @NotNull ImmutableSeq<Arg<@NotNull Term>> args
  ) implements CallTerm {
    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitFnCall(this, p);
    }
  }

  record Prim(
    @NotNull DefVar<PrimDef, Decl.PrimDecl> ref,
    @NotNull PrimDef.ID id,
    @NotNull ImmutableSeq<@NotNull Sort> sortArgs,
    @NotNull ImmutableSeq<Arg<@NotNull Term>> args
  ) implements CallTerm {
    public Prim(@NotNull DefVar<@NotNull PrimDef, Decl.PrimDecl> ref,
                @NotNull ImmutableSeq<@NotNull Sort> sortArgs,
                @NotNull ImmutableSeq<Arg<@NotNull Term>> args) {
      this(ref, ref.core.id, sortArgs, args);
    }

    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitPrimCall(this, p);
    }
  }

  record Data(
    @NotNull DefVar<DataDef, Decl.DataDecl> ref,
    @NotNull ImmutableSeq<@NotNull Sort> sortArgs,
    @NotNull ImmutableSeq<Arg<@NotNull Term>> args
  ) implements CallTerm {
    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitDataCall(this, p);
    }

    public @NotNull ConHead conHead(@NotNull DefVar<CtorDef, Decl.DataCtor> ctorRef) {
      return new ConHead(ref, ctorRef, sortArgs, args);
    }
  }

  /**
   * @author kiva
   */
  record Struct(
    @NotNull DefVar<StructDef, Decl.StructDecl> ref,
    @NotNull ImmutableSeq<@NotNull Sort> sortArgs,
    @NotNull ImmutableSeq<Arg<@NotNull Term>> args
  ) implements CallTerm {
    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitStructCall(this, p);
    }
  }

  record ConHead(
    @NotNull DefVar<DataDef, Decl.DataDecl> dataRef,
    @NotNull DefVar<CtorDef, Decl.DataCtor> ref,
    @NotNull ImmutableSeq<@NotNull Sort> sortArgs,
    @NotNull ImmutableSeq<Arg<@NotNull Term>> dataArgs
  ) {
    public @NotNull Data underlyingDataCall() {
      return new Data(dataRef, sortArgs, dataArgs);
    }
  }

  record Con(
    @NotNull ConHead head,
    @NotNull ImmutableSeq<Arg<Term>> conArgs
  ) implements CallTerm {
    public Con(
      @NotNull DefVar<DataDef, Decl.DataDecl> dataRef,
      @NotNull DefVar<CtorDef, Decl.DataCtor> ref,
      @NotNull ImmutableSeq<Arg<@NotNull Term>> dataArgs,
      @NotNull ImmutableSeq<@NotNull Sort> sortArgs,
      @NotNull ImmutableSeq<Arg<@NotNull Term>> conArgs
    ) {
      this(new ConHead(dataRef, ref, sortArgs, dataArgs), conArgs);
    }

    @Override public @NotNull DefVar<CtorDef, Decl.DataCtor> ref() {
      return head.ref;
    }

    @Override public @NotNull ImmutableSeq<@NotNull Sort> sortArgs() {
      return head.sortArgs;
    }

    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitConCall(this, p);
    }

    @Override public @NotNull ImmutableSeq<Arg<@NotNull Term>> args() {
      return head.dataArgs.view().concat(conArgs).toImmutableSeq();
    }
  }

  /**
   * @author ice1000
   */
  record Hole(
    @NotNull Meta ref,
    @NotNull ImmutableSeq<@NotNull Arg<@NotNull Term>> contextArgs,
    @NotNull ImmutableSeq<@NotNull Arg<@NotNull Term>> args
  ) implements CallTerm {
    public @NotNull FormTerm.Pi asPi(boolean explicit) {
      return ref.asPi(ref.name() + "dom", ref.name() + "cod", explicit, contextArgs);
    }

    public @NotNull SeqView<@NotNull Arg<Term>> fullArgs() {
      return contextArgs.view().concat(args);
    }

    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitHole(this, p);
    }

    @Override public @NotNull ImmutableSeq<@NotNull Sort> sortArgs() {
      return ImmutableSeq.empty();
    }
  }

  /**
   * @author ice1000
   */
  record Access(
    @NotNull Term of,
    @NotNull DefVar<FieldDef, Decl.StructField> ref,
    @NotNull ImmutableSeq<@NotNull Sort> sortArgs,
    @NotNull ImmutableSeq<@NotNull Arg<@NotNull Term>> structArgs,
    @NotNull ImmutableSeq<@NotNull Arg<@NotNull Term>> fieldArgs
  ) implements CallTerm {
    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitAccess(this, p);
    }

    @Override public @NotNull ImmutableSeq<@NotNull Arg<Term>> args() {
      return structArgs.concat(fieldArgs);
    }
  }
}
