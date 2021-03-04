// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.api.error;

import org.glavo.kala.collection.mutable.ArrayBuffer;
import org.glavo.kala.collection.mutable.Buffer;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 */
public record CollectReporter(@NotNull Buffer<@NotNull Problem> errors) implements Reporter {
  public CollectReporter() {
    this(new ArrayBuffer<>());
  }

  /**
   * {@inheritDoc}
   */
  @Override public void report(@NotNull Problem problem) {
    errors.append(problem);
  }
}
