// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.core.term;

import org.jetbrains.annotations.NotNull;
import org.mzi.core.Tele;

/**
 * @author kiva
 */
public interface DT extends Term {
  @NotNull Tele telescope();

  /**
   * @return that if it's a copi or a cosigma.
   */
  boolean co();
}
