// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.api.util;

/**
 * @author ice1000
 */
public abstract class InternalException extends RuntimeException {
  public abstract void printHint();
  public abstract int exitCode();
}
