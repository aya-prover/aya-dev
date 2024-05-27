// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp.models;

import org.jetbrains.annotations.Nullable;

public class ServerOptions {
  public ServerRenderOptions renderOptions;

  public ServerOptions() { }
  public ServerOptions(@Nullable ServerRenderOptions renderOptions) {
    this.renderOptions = renderOptions;
  }
}
