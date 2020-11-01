// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.api;

/**
 * @author kiva
 */
public final class Global {
  /**
   * Indicate that whether we are in tests.
   */
  public static boolean TEST = false;

  public static void runInTestMode(Runnable r) {
    Global.TEST = true;
    try {
      r.run();
    } finally {
      Global.TEST = false;
    }
  }
}
