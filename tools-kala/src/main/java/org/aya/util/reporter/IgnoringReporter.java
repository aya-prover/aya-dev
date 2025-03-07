// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.util.reporter;

import org.jetbrains.annotations.NotNull;

public enum IgnoringReporter implements Reporter {
  INSTANCE;

  @Override public void report(@NotNull Problem problem) { }
}
