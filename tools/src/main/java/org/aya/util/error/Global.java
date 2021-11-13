// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.util.error;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.annotations.VisibleForTesting;

/**
 * @author kiva
 */
@ApiStatus.Internal
@TestOnly
@VisibleForTesting
public final class Global {
  public static boolean UNITE_SOURCE_POS = false;
  public static boolean NO_RANDOM_NAME = false;

  public static void reset() {
    UNITE_SOURCE_POS = false;
    NO_RANDOM_NAME = false;
  }
}
