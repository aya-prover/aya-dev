// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.api.error;

import kala.collection.mutable.Buffer;
import org.jetbrains.annotations.NotNull;

public interface StoringReporter extends Reporter {
  @NotNull Buffer<Problem> problems();
}
