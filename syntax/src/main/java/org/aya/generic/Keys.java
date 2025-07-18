// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.generic;

import com.intellij.openapi.util.Key;
import kala.value.MutableValue;
import org.aya.syntax.concrete.Expr;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.ref.LocalVar;
import org.jetbrains.annotations.NotNull;

public class Keys {
  public static final @NotNull Key<MutableValue<Term>> withTerm = new Key<>("withTerm");
  public static final @NotNull Key<Expr> withType = new Key<>("withType");
  public static final @NotNull Key<LocalVar> bindIntro = new Key<>("bindIntro");
}
