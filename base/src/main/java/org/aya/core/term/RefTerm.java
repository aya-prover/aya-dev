// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import org.aya.concrete.stmt.decl.TeleDecl;
import org.aya.core.def.FieldDef;
import org.aya.core.pat.Pat;
import org.aya.ref.DefVar;
import org.aya.ref.LocalVar;
import org.jetbrains.annotations.NotNull;

import java.util.function.UnaryOperator;

/**
 * @author ice1000
 */
public record RefTerm(@NotNull LocalVar var) implements Term {
  @Override public @NotNull RefTerm descent(@NotNull UnaryOperator<Term> f, @NotNull UnaryOperator<Pat> g) {
    return this;
  }

  public record Field(@NotNull DefVar<FieldDef, TeleDecl.StructField> ref) implements Term {
    @Override public @NotNull Field descent(@NotNull UnaryOperator<Term> f, @NotNull UnaryOperator<Pat> g) {
      return this;
    }
  }
}
