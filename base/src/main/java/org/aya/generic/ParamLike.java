// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.generic;

import org.aya.api.ref.Var;
import org.aya.core.pretty.DefPrettier;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Docile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @param <Expr> the type of the expression contained, either
 *               {@link org.aya.core.term.Term} or {@link org.aya.concrete.Expr}.
 * @author ice1000
 */
public interface ParamLike<Expr extends Docile> extends Docile {
  boolean explicit();
  @NotNull Var ref();
  @Nullable Expr type();
  @Override default @NotNull Doc toDoc() {
    var explicit = explicit();
    var type = type();
    return Doc.hcat(
      Doc.plain(explicit ? "(" : "{"),
      DefPrettier.plainLink(ref()),
      type == null ? Doc.empty() : Doc.hcat(Doc.plain(" : "), type.toDoc()),
      Doc.plain(explicit ? ")" : "}")
    );
  }
}
