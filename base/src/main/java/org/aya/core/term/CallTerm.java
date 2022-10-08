// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import org.aya.concrete.stmt.Decl;
import org.aya.concrete.stmt.TeleDecl;
import org.aya.core.Meta;
import org.aya.core.def.*;
import org.aya.generic.Arg;
import org.aya.ref.AnyVar;
import org.aya.ref.DefVar;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

/**
 * @author ice1000
 * @see ElimTerm#make(ElimTerm.App)
 */
public sealed interface CallTerm extends Term {
  @NotNull AnyVar ref();
  @NotNull ImmutableSeq<@NotNull Arg<Term>> args();
  sealed interface DefCall extends CallTerm {
    @Override @NotNull DefVar<? extends Def, ? extends Decl.Telescopic> ref();
    int ulift();
  }

  /** This exists solely for simplifying code in the tycker. */
  @FunctionalInterface
  interface Factory<D extends Def, S extends Decl> {
    @Contract(pure = true, value = "_,_,_->new") @NotNull CallTerm make(
      DefVar<D, S> defVar,
      int ulift,
      ImmutableSeq<@NotNull Arg<Term>> args
    );
  }

  record Fn(
    @Override @NotNull DefVar<FnDef, TeleDecl.FnDecl> ref,
    @Override int ulift,
    @Override @NotNull ImmutableSeq<Arg<@NotNull Term>> args
  ) implements DefCall {
  }

  record Prim(
    @Override @NotNull DefVar<PrimDef, TeleDecl.PrimDecl> ref,
    @NotNull PrimDef.ID id,
    @Override int ulift,
    @Override @NotNull ImmutableSeq<Arg<@NotNull Term>> args
  ) implements DefCall {
    public Prim(@NotNull DefVar<@NotNull PrimDef, TeleDecl.PrimDecl> ref,
                int ulift, @NotNull ImmutableSeq<Arg<@NotNull Term>> args) {
      this(ref, ref.core.id, ulift, args);
    }

  }

  record Data(
    @Override @NotNull DefVar<DataDef, TeleDecl.DataDecl> ref,
    @Override int ulift,
    @Override @NotNull ImmutableSeq<Arg<@NotNull Term>> args
  ) implements DefCall, StableWHNF {

    public @NotNull ConHead conHead(@NotNull DefVar<CtorDef, TeleDecl.DataCtor> ctorRef) {
      return new ConHead(ref, ctorRef, ulift, args);
    }
  }

  /**
   * @author kiva
   */
  record Struct(
    @Override @NotNull DefVar<StructDef, TeleDecl.StructDecl> ref,
    @Override int ulift,
    @Override @NotNull ImmutableSeq<Arg<@NotNull Term>> args
  ) implements DefCall, StableWHNF {
  }

  record ConHead(
    @NotNull DefVar<DataDef, TeleDecl.DataDecl> dataRef,
    @NotNull DefVar<CtorDef, TeleDecl.DataCtor> ref,
    int ulift,
    @NotNull ImmutableSeq<Arg<@NotNull Term>> dataArgs
  ) {
    public @NotNull Data underlyingDataCall() {
      return new Data(dataRef, ulift, dataArgs);
    }

    public @NotNull ConHead descent(@NotNull Function<@NotNull Term, @NotNull Term> f) {
      var args = dataArgs().map(arg -> arg.descent(f));
      if (args.sameElements(dataArgs(), true)) return this;
      return new CallTerm.ConHead(dataRef(), ref(), ulift(), args);
    }
  }

  record Con(
    @NotNull ConHead head,
    @NotNull ImmutableSeq<Arg<Term>> conArgs
  ) implements DefCall {
    public Con(
      @NotNull DefVar<DataDef, TeleDecl.DataDecl> dataRef,
      @NotNull DefVar<CtorDef, TeleDecl.DataCtor> ref,
      @NotNull ImmutableSeq<Arg<@NotNull Term>> dataArgs,
      int ulift,
      @NotNull ImmutableSeq<Arg<@NotNull Term>> conArgs
    ) {
      this(new ConHead(dataRef, ref, ulift, dataArgs), conArgs);
    }

    @Override public @NotNull DefVar<CtorDef, TeleDecl.DataCtor> ref() {
      return head.ref;
    }

    @Override public int ulift() {
      return head.ulift;
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
    // TODO[ice]: remove this below
    int ulift,
    @NotNull ImmutableSeq<@NotNull Arg<@NotNull Term>> contextArgs,
    @NotNull ImmutableSeq<@NotNull Arg<@NotNull Term>> args
  ) implements CallTerm {
    public @NotNull FormTerm.Pi asPi(boolean explicit) {
      return ref.asPi(ref.name() + "dom", ref.name() + "cod", explicit, ulift, contextArgs);
    }

    public @NotNull SeqView<@NotNull Arg<Term>> fullArgs() {
      return contextArgs.view().concat(args);
    }

  }

  /**
   * @author ice1000
   */
  record Access(
    @NotNull Term of,
    @NotNull DefVar<FieldDef, TeleDecl.StructField> ref,
    @NotNull ImmutableSeq<@NotNull Arg<@NotNull Term>> structArgs,
    @NotNull ImmutableSeq<@NotNull Arg<@NotNull Term>> fieldArgs
  ) implements CallTerm {

    @Override public @NotNull ImmutableSeq<@NotNull Arg<Term>> args() {
      return structArgs.concat(fieldArgs);
    }
  }
}
