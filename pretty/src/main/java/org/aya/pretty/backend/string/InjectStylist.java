// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.pretty.backend.string;

import org.jetbrains.annotations.NotNull;

public class InjectStylist extends ClosingStylist {
  protected final @NotNull ClosingStylist delegate;

  public InjectStylist(@NotNull ClosingStylist delegate) {
    super(delegate.colorScheme, delegate.styleFamily);
    this.delegate = delegate;
  }

  @Override protected @NotNull StyleToken formatItalic(StringPrinter.Outer outer) {
    return delegate.formatItalic(outer);
  }

  @Override protected @NotNull StyleToken formatBold(StringPrinter.Outer outer) {
    return delegate.formatBold(outer);
  }

  @Override protected @NotNull StyleToken formatStrike(StringPrinter.Outer outer) {
    return delegate.formatStrike(outer);
  }

  @Override protected @NotNull StyleToken formatUnderline(StringPrinter.Outer outer) {
    return delegate.formatUnderline(outer);
  }

  @Override protected @NotNull StyleToken formatColorHex(int rgb, boolean background) {
    return delegate.formatColorHex(rgb, background);
  }
}
