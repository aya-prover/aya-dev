// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.parser;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class AyaPsiElementType extends IElementType {
  public AyaPsiElementType(@NonNls @NotNull String debugName) {
    super(debugName, AyaLanguage.INSTANCE);
  }
}
