// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.visitor;

import org.aya.core.def.PrimDef;
import org.aya.core.pat.Pat;
import org.aya.core.term.CallTerm;
import org.aya.core.term.IntroTerm;
import org.aya.core.term.RefTerm;
import org.aya.core.term.Term;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public interface TermToPat {
  static @Nullable Pat toPat(@NotNull Term term, boolean explicit) {
    //noinspection ConstantConditions
    return switch (term) {
      default -> null;
      case RefTerm ref -> new Pat.Bind(explicit, ref.var(), ref.type());
      case CallTerm.Con conCall -> new Pat.Ctor(explicit, conCall.ref(),
        conCall.args().map(at -> toPat(at.term(), at.explicit())), null,
        conCall.head().underlyingDataCall());
      case CallTerm.Prim prim -> {
        // TODO[ice]: add id to primcall and replace this test
        if (Objects.equals(prim.ref().name(), PrimDef.ID.LEFT.id))
          yield new Pat.Prim(explicit, prim.ref(), prim.computeType());
        if (Objects.equals(prim.ref().name(), PrimDef.ID.RIGHT.id))
          yield new Pat.Prim(explicit, prim.ref(), prim.computeType());
        yield null;
      }
      case IntroTerm.Tuple tuple -> new Pat.Tuple(explicit,
        tuple.items().map(item -> toPat(item, true)), null, term);
    };
  }
}
