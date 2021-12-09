// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.pretty.printer;

import kala.collection.mutable.MutableMap;
import org.aya.pretty.doc.Styles;
import org.jetbrains.annotations.NotNull;

public interface StyleFamily {
  @NotNull MutableMap<String, Styles> definedStyles();
}
