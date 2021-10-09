// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.pretty.doc;

import org.jetbrains.annotations.Debug;
import org.jetbrains.annotations.NotNull;

/**
 * Should be called <code>Prettiable</code>.
 * Used for debugging purpose <strong>only</strong>.
 *
 * @author ice1000
 */
@Debug.Renderer(text = "toDoc().debugRender()")
public interface Docile {
  @NotNull Doc toDoc();
}
