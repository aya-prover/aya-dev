// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.generic;

import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.jetbrains.annotations.NotNull;

/**
 * @param <Pattern> {@link org.aya.core.pat.Pat} or {@link org.aya.concrete.Pattern}
 * @param <Body>    {@link org.aya.core.term.Term} or {@link org.aya.concrete.Expr} or
 *                  {@link org.aya.core.def.DataDef.Ctor}
 * @author ice1000
 */
public record Matching<Pattern, Body>(
  @NotNull ImmutableSeq<Pattern> patterns,
  @NotNull Body body
) {
}
