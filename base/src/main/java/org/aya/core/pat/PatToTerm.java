// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.pat;

import org.aya.core.term.*;
import org.aya.ref.LocalVar;
import org.aya.util.Arg;
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
      case Pat.Absurd absurd -> new RefTerm(new LocalVar("()"));
      case Pat.Ctor ctor -> visitCtor(ctor);
      case Pat.Bind bind -> new RefTerm(bind.bind());
      case Pat.Tuple tuple -> new TupTerm(tuple.pats().map(p -> p.map(this::visit)));
      case Pat.Meta meta -> new MetaPatTerm(meta);
      case Pat.ShapedInt lit -> new IntegerTerm(lit.repr(), lit.recognition(), lit.type());
    };
  }

  protected @NotNull Term visitCtor(Pat.@NotNull Ctor ctor) {
    var data = (DataCall) ctor.type();
    var args = Arg.mapSeq(ctor.params(), this::visit);
    return new ConCall(data.ref(), ctor.ref(), data.args(), data.ulift(), args);
  }
}
