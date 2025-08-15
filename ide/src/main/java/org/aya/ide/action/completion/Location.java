// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.ide.action.completion;

import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;

import static org.aya.parser.AyaPsiElementTypes.*;

// TODO: the list is still incomplete, add more later
public enum Location {
  Modifier(TokenSet.create(KW_PUBLIC, KW_PRIVATE, KW_OPAQUE, KW_INFIX, KW_INFIXL, KW_INFIXR, KW_OVERLAP)),   // only modifiers
  Bind(TokenSet.EMPTY),       // no completion
  Expr(TokenSet.create(
    KW_DO, KW_LET, KW_IN,
    KW_FORALL, KW_PI, KW_LAMBDA, KW_SIGMA,
    KW_ULIFT, KW_TYPE, KW_SET, KW_PROP, KW_ISET,
    KW_SELF, KW_NEW, KW_PARTIAL, KW_MATCH)),       // almost everything
  Pattern(TokenSet.create(KW_AS)),    // only constructors
  Elim(TokenSet.EMPTY),       // only binds
  TopLevel(TokenSet.create(KW_DEF, KW_DATA, KW_CODATA, KW_MODULE, KW_CLASS)),
  /// top level, available when the cursor is at the beginning of the line
  Unknown(TokenSet.EMPTY);     // no completion

  public final @NotNull TokenSet keywords;

  Location(@NotNull TokenSet keywords) {
    this.keywords = keywords;
  }
}
