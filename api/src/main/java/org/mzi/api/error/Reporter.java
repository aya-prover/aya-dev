// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.api.error;

import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 */
public interface Reporter {
  void report(@NotNull Error error);
}
