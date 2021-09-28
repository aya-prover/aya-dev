// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.generic;

import org.aya.api.distill.AyaDocile;
import org.aya.api.distill.DistillerOptions;
import org.aya.api.ref.Var;
import org.aya.distill.BaseDistiller;
import org.aya.pretty.doc.Doc;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @param <Expr> the type of the expression contained, either
 *               {@link org.aya.core.term.Term} or {@link org.aya.concrete.Expr}.
 * @author ice1000
 */
public interface ParamLike<Expr extends AyaDocile> extends AyaDocile {
  boolean explicit();
  @NotNull Var ref();
  @Nullable Expr type();
  @Override default @NotNull Doc toDoc(@NotNull DistillerOptions options) {
    return toDoc(nameDoc(), options);
  }
  default @NotNull Doc nameDoc() {
    return BaseDistiller.linkDef(ref());
  }
  default @NotNull Doc toDoc(@NotNull Doc names, @NotNull DistillerOptions options) {
    var type = type();
    return Doc.licit(explicit(), Doc.cat(names, type == null ? Doc.empty() : Doc.cat(Doc.symbol(" : "), type.toDoc(options))));
  }
}
