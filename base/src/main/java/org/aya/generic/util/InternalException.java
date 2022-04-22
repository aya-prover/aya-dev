// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.generic.util;

import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 */
public class InternalException extends RuntimeException {
  public InternalException() {}

  public InternalException(@NotNull String message) {
    super(message);
  }

  public InternalException(@NotNull String message, Throwable cause) {
    super(message, cause);
  }

  public void printHint() {
    System.out.println(getMessage());
  }

  public int exitCode() {
    return -1;
  }
}
