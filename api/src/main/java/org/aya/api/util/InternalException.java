// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.api.util;

/**
 * @author ice1000
 */
public abstract class InternalException extends RuntimeException {
  public InternalException() {
  }

  public InternalException(String message) {
    super(message);
  }

  public abstract void printHint();
  public abstract int exitCode();
}
