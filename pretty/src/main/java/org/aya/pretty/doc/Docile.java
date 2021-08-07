// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.pretty.doc;

import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.Debug;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * Should be called <code>Prettiable</code>.
 * Used for debugging purpose <strong>only</strong>.
 *
 * @author ice1000
 */
@Debug.Renderer(text = Docile.DEBUG_RENDER)
public interface Docile {
  @NotNull Doc toDoc();

  @Language(value = "JAVA", prefix = "class X{void x(Docile y){y.", suffix = ";}}")
  @NotNull @NonNls String DEBUG_RENDER = "toDoc().debugRender()";
}
