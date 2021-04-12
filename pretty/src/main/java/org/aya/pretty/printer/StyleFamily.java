// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.pretty.printer;

import org.aya.pretty.doc.Styles;
import org.glavo.kala.collection.mutable.MutableMap;

public interface StyleFamily {
  MutableMap<String, Styles> definedStyles();
}
