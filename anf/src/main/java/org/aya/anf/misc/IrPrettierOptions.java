// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.anf.misc;

import org.aya.util.PrettierOptions;

public class IrPrettierOptions extends PrettierOptions {
  public IrPrettierOptions() {
    super(IrPrettierOptions.Key.class);
  }

  public enum Key implements PrettierOptions.Key {
    IndentLetBinds
  }

  @Override
  public void reset() {
    for (var value: Key.values()) map.put(value, false);
    map.put(Key.IndentLetBinds, true);
  }
}
