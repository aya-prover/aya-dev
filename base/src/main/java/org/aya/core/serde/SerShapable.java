// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.serde;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

public interface SerShapable {
  @Contract(pure = true)
  @Nullable SerDef.SerShapeResult shapeResult();
}
