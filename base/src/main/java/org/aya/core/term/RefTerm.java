// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import org.aya.concrete.stmt.Decl;
import org.aya.core.def.FieldDef;
import org.aya.core.pat.Pat;
import org.aya.ref.DefVar;
import org.aya.ref.LocalVar;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 */
public record RefTerm(@NotNull LocalVar var, int lift) implements Term {
  @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
    return visitor.visitRef(this, p);
  }

  public record Field(@NotNull DefVar<FieldDef, Decl.StructField> ref, int lift) implements Term {
    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitFieldRef(this, p);
    }
  }

  public record MetaPat(@NotNull Pat.Meta ref, int lift) implements Term {
    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitMetaPat(this, p);
    }

    public @NotNull Term inline() {
      var sol = ref.solution().value;
      return sol != null ? sol.toTerm() : this;
    }
  }
}
