// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.generic.util;

/**
 * @author ice1000
 */
public class InternalException extends RuntimeException {
  public InternalException() {}

  public InternalException(String message) {
    super(message);
  }

  public void printHint() {
    System.out.println(getMessage());
  }

  public int exitCode() {
    return -1;
  }
}
