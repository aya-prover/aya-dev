// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import kala.collection.immutable.ImmutableSeq;
import org.aya.concrete.stmt.decl.TeleDecl;
import org.aya.core.def.Def;
import org.aya.core.def.FnDef;
import org.aya.core.pat.Pat;
import org.aya.generic.Shaped;
import org.aya.ref.DefVar;
import org.aya.util.Arg;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.UnaryOperator;

/**
 * @param head  should be also a {@link Term}
 * @param ulift
 * @param args
 */
public record ShapedFnCall(
  @NotNull Shaped.Appliable<Term> head,
  @Override int ulift,
  @Override @NotNull ImmutableSeq<Arg<Term>> args
) implements Callable.Tele {
  @Override
  public @NotNull DefVar<? extends Def, ? extends TeleDecl<?>> ref() {
    return head.ref();
  }

  private @NotNull ShapedFnCall update(@NotNull Shaped.Appliable<Term> head, @NotNull ImmutableSeq<Arg<Term>> args) {
    return head == this.head && args.sameElements(this.args, true)
      ? this
      : new ShapedFnCall(head, ulift, args);
  }

  @Override
  public @NotNull Term descent(@NotNull UnaryOperator<Term> f, @NotNull UnaryOperator<Pat> g) {
    return update((Shaped.Appliable<Term>) f.apply((Term) head), args.map(x -> x.descent(f)));
  }

  /**
   * @return null if this is not a fn call
   */
  public @Nullable FnCall toFnCall() {
    var ref = head.ref();
    if (ref.core instanceof FnDef || ref.concrete instanceof TeleDecl.FnDecl) {
      return new FnCall((DefVar<FnDef, TeleDecl.FnDecl>) ref, ulift, args);
    } else {
      return null;
    }
  }
}
