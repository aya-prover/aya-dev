// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.api.util;

/**
 * @author ice1000
 */
public abstract class MziBreakingException extends RuntimeException {
  public abstract void printHint();
  public abstract int exitCode();
}
