// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.generic;

import org.aya.prettier.AyaPrettierOptions;
import org.aya.pretty.doc.Doc;
import org.aya.util.prettier.PrettierOptions;
import org.jetbrains.annotations.Debug;
import org.jetbrains.annotations.NotNull;

@FunctionalInterface
@Debug.Renderer(text = "debuggerOnlyToString()")
public interface AyaDocile /*extends Docile*/ {
  /**
   * Load PrettierOptions by using it explicitly so IDEA won't show cannot load blahblah
   * in the debugger window.
   *
   * @apiNote This should not be used in any other place.
   * @deprecated use {@link #toDoc(PrettierOptions)} instead
   */
  @Deprecated default @NotNull String debuggerOnlyToString() {
    return toDoc(AyaPrettierOptions.debug()).debugRender();
  }

  @NotNull Doc toDoc(@NotNull PrettierOptions options);
}
