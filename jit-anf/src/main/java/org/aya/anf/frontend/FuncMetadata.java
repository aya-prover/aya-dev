// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.anf.frontend;

/// Contains metadata for an `IRFunc`.
public class FuncMetadata {
  public int uses = 0;
  public int generatedFuncs = 0;

  @Override
  public String toString() {
    return String.format("[uses=%d, generated_funcs=%d]", uses, generatedFuncs);
  }
}
