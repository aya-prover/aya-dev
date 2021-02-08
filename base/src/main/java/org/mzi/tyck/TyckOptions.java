// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.tyck;

/**
 * Intended to be modified only when the compiler starts.
 * It should become immutable right after that, so the fields are kept thread-unsafe.
 *
 * @author ice10
 */
public final class TyckOptions {
  public static boolean TYPE_IN_TYPE = true;
  public static boolean VERBOSE = false;
}
