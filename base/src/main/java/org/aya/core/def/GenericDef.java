// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.def;

import org.aya.core.pat.Pat;
import org.aya.core.term.Term;
import org.aya.generic.AyaDocile;
import org.aya.ref.DefVar;
import org.jetbrains.annotations.NotNull;

import java.util.function.UnaryOperator;

/**
 * @author zaoqi
 */
public sealed interface GenericDef extends AyaDocile permits ClassDef, Def {
  @NotNull DefVar<?, ?> ref();

  @NotNull Term result();

  @NotNull GenericDef descent(@NotNull UnaryOperator<Term> f, @NotNull UnaryOperator<Pat> g);
}
