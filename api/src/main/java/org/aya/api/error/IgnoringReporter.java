// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.api.error;

import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 */
public final class IgnoringReporter implements Reporter {
  public static final @NotNull IgnoringReporter INSTANCE = new IgnoringReporter();
  private IgnoringReporter() {
  }

  @Override public void report(@NotNull Problem problem) {
  }
}
