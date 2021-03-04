// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.api;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.annotations.VisibleForTesting;

/**
 * @author kiva
 */
public final class Global {
  /**
   * Indicate that whether we are in tests.
   */
  private static boolean TEST = false;

  @TestOnly @VisibleForTesting @ApiStatus.Internal
  public static void enterTestMode() {
    TEST = true;
  }

  @Contract(pure = true) public static boolean isTest() {
    return TEST;
  }
}
