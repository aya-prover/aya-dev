// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.ref;

import org.jetbrains.annotations.Debug;
import org.jetbrains.annotations.NotNull;

/**
 * @author kiva
 */
@Debug.Renderer(hasChildren = "false", text = "name()")
public interface AnyVar {
  @NotNull String name();
}
