// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.api.distill;

import org.aya.pretty.doc.Doc;
import org.jetbrains.annotations.Debug;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 */
@FunctionalInterface
@Debug.Renderer(text = "debuggerOnlyToDoc().debugRender()")
public interface AyaDocile /*extends Docile*/ {
  // load DistillerOptions by using it explicitly so IDEA won't show cannot load blahblah
  // in the debugger window. this should not be used in any other place.
  @Deprecated default @NotNull Doc debuggerOnlyToDoc() {
    return toDoc(DistillerOptions.DEBUG);
  }

  @NotNull Doc toDoc(@NotNull DistillerOptions options);
  // @Override default @NotNull Doc toDoc() {
  //   return toDoc(DistillerOptions.DEBUG);
  // }
}
