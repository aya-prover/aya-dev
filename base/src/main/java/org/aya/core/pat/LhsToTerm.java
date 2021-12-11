// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.pat;

import kala.collection.immutable.ImmutableSeq;
import org.aya.api.util.Arg;
import org.aya.core.term.CallTerm;
import org.aya.core.term.IntroTerm;
import org.aya.core.term.RefTerm;
import org.aya.core.term.Term;
import org.jetbrains.annotations.NotNull;

/**
 * This is almost the same as PatToTerm.
 * Maybe it should be refactored in the future
 * @author tonfeiz
 * @see org.aya.tyck.pat.Conquer for why not final
 */
public class LhsToTerm {
  static final @NotNull LhsToTerm INSTANCE = new LhsToTerm();

  protected LhsToTerm() {
  }

  public Term visit(@NotNull Lhs lhs) {
    return switch (lhs) {
      case Lhs.Prim prim -> new CallTerm.Prim(prim.ref(), ImmutableSeq.empty(), ImmutableSeq.empty());
      case Lhs.Ctor ctor -> visitCtor(ctor);
      case Lhs.Bind bind -> new RefTerm(bind.bind());
      case Lhs.Tuple tuple -> new IntroTerm.Tuple(tuple.lhss().map(this::visit));
    };
  }

  protected @NotNull Term visitCtor(Lhs.@NotNull Ctor ctor) {
    var data = (CallTerm.Data) ctor.type();
    var core = ctor.ref().core;
    var tele = core.selfTele;
    var args = ctor.params().view().zip(tele)
      .map(p -> new Arg<>(visit(p._1), p._2.explicit()))
      .toImmutableSeq();
    return new CallTerm.Con(data.ref(), ctor.ref(), data.args(), data.sortArgs(), args);
  }
}
