// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.free.data;

import org.aya.compiler.free.FreeJava;
import org.jetbrains.annotations.NotNull;

public interface LocalVariable {
  @NotNull FreeJava ref();
}
