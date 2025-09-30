// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.pat;

import org.aya.syntax.core.term.*;
import org.aya.syntax.core.term.call.ConCall;
import org.jetbrains.annotations.NotNull;

/// [org.aya.syntax.core.annotation.Bound]-friendly,
/// the dbi-context doesn't change during the recursion, as no new binding is introduced.
public interface PatToTerm {
  static @NotNull Term visit(@NotNull Pat pat) {
    return switch (pat) {
      case Pat.Misc misc -> switch (misc) {
        // We expect this to be never used, but this needs to not panic because
        // absurd clauses need to finish type checking
        case Absurd -> SortTerm.Type0;
        // case UntypedBind -> Panic.unreachable();
      };
      case Pat.Bind bind -> new FreeTerm(bind.bind());
      // FIXME: need a shapeFactory and produce RuleReducer if possible
      case Pat.Con con -> new ConCall(con.head(), con.args().map(PatToTerm::visit));
      case Pat.Tuple(var l, var r) -> new TupTerm(visit(l), visit(r));
      case Pat.Meta meta -> new MetaPatTerm(meta);
      case Pat.ShapedInt si -> si.toTerm();
    };
  }
}
