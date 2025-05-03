// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.util;

/// Interface used for delegation purposes
public interface HasError {
  boolean hasError();
  void foundError();

  default void checkError(boolean anyError) {
    if (anyError) foundError();
  }

  final class Bool implements HasError {
    private boolean hasError = false;
    @Override public boolean hasError() { return hasError; }
    @Override public void foundError() { this.hasError = true; }
  }
}
