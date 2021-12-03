// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.api.error;

import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 */
public record IgnoringReporter() implements Reporter {
  public static final @NotNull IgnoringReporter INSTANCE = new IgnoringReporter();
  @Override public void report(@NotNull Problem problem) {
  }
}
