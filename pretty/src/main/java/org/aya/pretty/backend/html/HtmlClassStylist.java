// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.pretty.backend.html;

import org.aya.pretty.backend.string.ClosingStylist;
import org.aya.pretty.backend.string.InjectStylist;
import org.jetbrains.annotations.NotNull;

public class HtmlClassStylist extends InjectStylist {
  public HtmlClassStylist(@NotNull ClosingStylist delegate) {
    super(delegate);
  }
}
