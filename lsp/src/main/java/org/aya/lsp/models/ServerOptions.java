// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp.models;

import com.google.gson.JsonElement;
import org.aya.lsp.options.RenderOptions;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * The server options for LSP
 *
 * @see RenderOptions
 * @see org.javacs.lsp.InitializeParams#initializationOptions
 * @see org.aya.lsp.server.AyaLanguageServer#updateOptions(ServerOptions)
 */
public class ServerOptions {
  /**
   * the color scheme which overrides the {@link ServerOptions#colorName}.
   * Due to the JSON doesn't support hexadecimal integer literal, we use {@link String} instead of {@link Integer}.
   */
  public @Nullable Map<String, String> colorOverride;

  /**
   * The colorScheme name, "emacs"/"Emacs" and "intellij"/"IntelliJ" are valid for now, see {@link RenderOptions#BUILTIN_COLOR_SCHEMES}.
   * "none" is picked if {@code colorScheme == null}
   */
  public @Nullable String colorName;

  /**
   * unused for now
   *
   * @see RenderOptions#styleFamily()
   */
  public @Nullable JsonElement styleFamily;

  public ServerOptions(@Nullable String colorName, @Nullable Map<String, String> colorOverride, @Nullable JsonElement styleFamily) {
    this.colorOverride = colorOverride;
    this.colorName = colorName;
    this.styleFamily = styleFamily;
  }
}
