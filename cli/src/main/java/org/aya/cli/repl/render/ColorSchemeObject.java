// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl.render;

import org.jetbrains.annotations.Nullable;

public class ColorSchemeObject {
  public @Nullable Color keyword;
  public @Nullable Color fnName;
  public @Nullable Color generalized;
  public @Nullable Color dataName;
  public @Nullable Color structName;
  public @Nullable Color conName;
  public @Nullable Color fieldName;
}
