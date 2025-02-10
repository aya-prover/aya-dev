// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.generic;

import org.aya.prettier.AyaPrettierOptions;
import org.aya.pretty.doc.Doc;
import org.aya.util.PrettierOptions;
import org.jetbrains.annotations.Debug;
import org.jetbrains.annotations.NotNull;

@FunctionalInterface
@Debug.Renderer(text = "toDoc(AyaPrettierOptions.debug()).debugRender()")
public interface AyaDocile /*extends Docile*/ {
  /**
   * Always prefer using {@link #toDoc(PrettierOptions)} instead,
   * this method is intended for non-user-facing pretty printing,
   * such as assertions, testing, etc.
   */
  default @NotNull String easyToString() {
    return toDoc(AyaPrettierOptions.pretty()).debugRender();
  }

  @NotNull Doc toDoc(@NotNull PrettierOptions options);
}
