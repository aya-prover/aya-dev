// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.pretty.backend.string.custom;

import org.aya.pretty.doc.Style;

public enum UnixTermStyle implements Style.CustomStyle {
  Dim,
  DoubleUnderline,
  CurlyUnderline,
  Blink,
  Overline,
  Reverse,
}
