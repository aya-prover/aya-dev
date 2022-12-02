// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.pretty.backend.md;

import org.aya.pretty.doc.Style;
import org.jetbrains.annotations.NotNull;

public sealed interface MdStyle extends Style.CustomStyle {
  /** GitHub flavored markdown */
  enum GFM implements MdStyle {
    H1, H2, H3, H4, H5, H6,
    BlockQuote, Paragraph,
  }

  record CodeBlock(@NotNull String language) implements MdStyle {}
}
