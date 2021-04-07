// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.term;

import org.aya.api.ref.DefVar;
import org.aya.api.util.Arg;
import org.aya.concrete.Decl;
import org.aya.core.def.StructDef;
import org.aya.util.Decision;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * Elimination rules.
 *
 * @author ice1000
 */
public sealed interface ElimTerm extends Term {
  @NotNull Term of();

  @Override @Contract(pure = true) default @NotNull Decision whnf() {
    var of = of();
    if (of instanceof IntroTerm) return Decision.NO;
    if (of.whnf() == Decision.YES) return Decision.YES;
    return Decision.MAYBE;
  }

  /**
   * @author re-xyr
   */
  record Proj(@NotNull Term of, int ix) implements ElimTerm {
    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitProj(this, p);
    }

    @Override public <P, Q, R> R doAccept(@NotNull BiVisitor<P, Q, R> visitor, P p, Q q) {
      return visitor.visitProj(this, p, q);
    }
  }

  /**
   * @author ice1000
   */
  record Access(@NotNull Term of, @NotNull DefVar<StructDef.Field, Decl.StructField> ref) implements ElimTerm {
    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitAccess(this, p);
    }

    @Override public <P, Q, R> R doAccept(@NotNull BiVisitor<P, Q, R> visitor, P p, Q q) {
      return visitor.visitAccess(this, p, q);
    }
  }

  record App(@NotNull Term of, @NotNull Arg<@NotNull Term> arg) implements ElimTerm {
    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitApp(this, p);
    }

    @Override public <P, Q, R> R doAccept(@NotNull BiVisitor<P, Q, R> visitor, P p, Q q) {
      return visitor.visitApp(this, p, q);
    }
  }
}
