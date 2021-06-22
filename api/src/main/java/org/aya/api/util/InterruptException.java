// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.api.util;

import org.jetbrains.annotations.ApiStatus;

/**
 * @author imkiva
 */
public abstract class InterruptException extends RuntimeException {
  @ApiStatus.Internal public abstract InterruptStage stage();

  public enum InterruptStage {
    Parsing,
    Resolving,
  }
}
