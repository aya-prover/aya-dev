// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import kala.collection.immutable.ImmutableSeq;
import org.aya.concrete.stmt.decl.CommonDecl;
import org.aya.concrete.stmt.decl.Decl;
import org.aya.concrete.stmt.decl.TeleDecl;
import org.aya.core.def.Def;
import org.aya.core.def.GenericDef;
import org.aya.ref.AnyVar;
import org.aya.ref.DefVar;
import org.aya.util.Arg;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 * @see AppTerm#make(AppTerm)
 */
public sealed interface Callable extends Term permits Callable.Common, FieldTerm, MetaTerm {
  @NotNull AnyVar ref();
  @NotNull ImmutableSeq<@NotNull Arg<Term>> args();
  /**
   * Call to a {@link TeleDecl}.
   */
  sealed interface Tele extends Common permits ConCall, DataCall, FnCall, PrimCall, ShapedFnCall {
    @Override @NotNull DefVar<? extends Def, ? extends TeleDecl<?>> ref();
    int ulift();
  }

  sealed interface Common extends Callable permits Tele {
    @Override @NotNull DefVar<? extends GenericDef, ? extends CommonDecl> ref();
    int ulift();
  }

  /** This exists solely for simplifying code in the tycker. */
  @FunctionalInterface
  interface Factory<D extends Def, S extends Decl> {
    @Contract(pure = true, value = "_,_,_->new") @NotNull Callable make(
      DefVar<D, S> defVar,
      int ulift,
      ImmutableSeq<@NotNull Arg<Term>> args
    );
  }
}
