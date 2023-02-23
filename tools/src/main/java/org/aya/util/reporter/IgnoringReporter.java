// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.util.reporter;

import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 */
public enum IgnoringReporter implements Reporter {
  INSTANCE;

  @Override public void report(@NotNull Problem problem) {
  }
}
