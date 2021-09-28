// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.api.distill;

import org.aya.pretty.doc.Doc;
import org.jetbrains.annotations.Debug;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 */
@FunctionalInterface
@Debug.Renderer(text = "toDoc(DistillerOptions.DEBUG).debugRender()")
public interface AyaDocile /*extends Docile*/ {
  @NotNull Doc toDoc(@NotNull DistillerOptions options);
  // @Override default @NotNull Doc toDoc() {
  //   return toDoc(DistillerOptions.DEBUG);
  // }
}
