// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.generic;

import org.jetbrains.annotations.ApiStatus;

/**
 * @author imkiva
 */
public abstract class InterruptException extends RuntimeException {
  @ApiStatus.Internal public abstract InterruptStage stage();

  public enum InterruptStage {
    Parsing,
    Resolving,
    Tycking,
  }
}
