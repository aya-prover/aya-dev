// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.generic;

import org.aya.distill.AyaDistillerOptions;
import org.aya.pretty.doc.Doc;
import org.aya.util.distill.DistillerOptions;
import org.jetbrains.annotations.Debug;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 */
@FunctionalInterface
@Debug.Renderer(text = "debuggerOnlyToDoc().debugRender()")
public interface AyaDocile /*extends Docile*/ {
  /**
   * Load DistillerOptions by using it explicitly so IDEA won't show cannot load blahblah
   * in the debugger window.
   *
   * @apiNote This should not be used in any other place.
   * @deprecated use {@link #toDoc(DistillerOptions)} instead
   */
  @Deprecated default @NotNull Doc debuggerOnlyToDoc() {
    return toDoc(AyaDistillerOptions.debug());
  }

  @NotNull Doc toDoc(@NotNull DistillerOptions options);
  // @Override default @NotNull Doc toDoc() {
  //   return toDoc(DistillerOptions.DEBUG);
  // }
}
