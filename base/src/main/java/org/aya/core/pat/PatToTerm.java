// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.pat;

import org.aya.core.term.*;
import org.aya.generic.Arg;
import org.aya.ref.LocalVar;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 * @see org.aya.tyck.pat.Conquer for why not final
 */
public class PatToTerm {
  static final @NotNull PatToTerm INSTANCE = new PatToTerm();

  protected PatToTerm() {
  }

  public Term visit(@NotNull Pat pat) {
    return switch (pat) {
      // [ice]: this code is reachable (to substitute a telescope), but the telescope will be dropped anyway.
      case Pat.Absurd absurd -> new RefTerm(new LocalVar("()"), 0);
      case Pat.Ctor ctor -> visitCtor(ctor);
      case Pat.Bind bind -> new RefTerm(bind.bind(), 0);
      case Pat.Tuple tuple -> new IntroTerm.Tuple(tuple.pats().map(this::visit));
      case Pat.Meta meta -> new RefTerm.MetaPat(meta, 0);
      case Pat.End end -> end.isRight() ? PrimTerm.End.RIGHT : PrimTerm.End.LEFT;
      case Pat.ShapedInt lit -> new LitTerm.ShapedInt(lit.integer(), lit.shape(), lit.type());
    };
  }

  protected @NotNull Term visitCtor(Pat.@NotNull Ctor ctor) {
    var data = (CallTerm.Data) ctor.type();
    var core = ctor.ref().core;
    var tele = core.selfTele;
    var args = ctor.params().view().zip(tele)
      .map(p -> new Arg<>(visit(p._1), p._2.explicit()))
      .toImmutableSeq();
    return new CallTerm.Con(data.ref(), ctor.ref(), data.args(), data.ulift(), args);
  }
}
