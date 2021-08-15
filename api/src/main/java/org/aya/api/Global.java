// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.api;

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
}
