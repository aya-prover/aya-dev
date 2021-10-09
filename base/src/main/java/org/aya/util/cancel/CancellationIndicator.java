// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.util.cancel;

public interface CancellationIndicator {
  boolean isCanceled();
  void cancel();

  default void checkCanceled() throws CancellationException {
    if (isCanceled()) throw new CancellationException();
  }
}
