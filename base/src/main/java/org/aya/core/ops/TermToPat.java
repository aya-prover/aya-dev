// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.ops;

import org.aya.api.util.Arg;
import org.aya.core.def.PrimDef;
import org.aya.core.pat.Pat;
import org.aya.core.term.CallTerm;
import org.aya.core.term.IntroTerm;
import org.aya.core.term.RefTerm;
import org.aya.core.term.Term;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface TermToPat {
  static @Nullable Pat toPat(@NotNull Arg<@NotNull Term> arg) {
    return toPat(arg.term(), arg.explicit());
  }

  static @Nullable Pat toPat(@NotNull Term term, boolean explicit) {
    return switch (term) {
      default -> null;
      case RefTerm ref -> new Pat.Bind(explicit, ref.var(), ref.type());
      case CallTerm.Con conCall -> new Pat.Ctor(explicit, conCall.ref(),
        conCall.args().map(TermToPat::toPat),
        conCall.head().underlyingDataCall());
      case CallTerm.Prim prim -> switch (prim.ref().core.id) {
        case LEFT, RIGHT -> new Pat.Prim(explicit, prim.ref(), PrimDef.intervalCall());
        default -> null;
      };
      case IntroTerm.Tuple tuple -> new Pat.Tuple(explicit,
        tuple.items().map(item -> toPat(item, true)), term);
    };
  }
}
