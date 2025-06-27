// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.anf.misc;

import org.aya.pretty.doc.Doc;
import org.jetbrains.annotations.NotNull;

/// An instance of this can be rendered with IRRenderer.
public interface IRComponentRender {
  @NotNull Doc consoleRender();
}
