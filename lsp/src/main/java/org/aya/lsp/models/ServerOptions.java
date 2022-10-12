// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp.models;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.javacs.lsp.InitializeParams;
import org.jetbrains.annotations.Nullable;

/**
 * The server options for LSP
 *
 * @see RenderOptions
 * @see org.javacs.lsp.InitializeParams#initializationOptions
 * @see org.aya.lsp.server.AyaLanguageServer#updateOptions(ServerOptions)
 */
public class ServerOptions {
  /**
   * the color scheme for pretty printer, can be either {@link String} or {@link JsonObject}
   * <ul>
   *   <li>String: color scheme name, "emacs" and "intellij" are valid for now, see {@link org.aya.pretty.style.AyaColorScheme}</li>
   *   <li>JsonObject: a {@link String}-{@link Integer} map, see {@link org.aya.pretty.style.AyaColorScheme.Key}</li>
   * </ul>
   */
  public @Nullable JsonElement colorScheme;

  /**
   * unused for now
   *
   * @see RenderOptions#styleFamily()
   */
  public @Nullable JsonElement styleFamily;

  /**
   * @see RenderOptions#renderTarget()
   */
  public @Nullable RenderOptions.RenderTarget renderTarget;

  public ServerOptions(@Nullable JsonElement colorScheme, @Nullable JsonElement styleFamily, @Nullable RenderOptions.RenderTarget renderTarget) {
    this.colorScheme = colorScheme;
    this.styleFamily = styleFamily;
    this.renderTarget = renderTarget;
  }
}
