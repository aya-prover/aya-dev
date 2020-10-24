// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.util.cancel;

public interface CancellationIndicator {
  boolean isCanceled();
  void cancel();

  default void checkCanceled() throws CancellationException {
    if (isCanceled()) throw new CancellationException();
  }
}
