// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp.models;

import org.aya.cli.render.Color;
import org.aya.cli.render.RenderOptions;
import org.aya.lsp.utils.Log;
import org.aya.pretty.style.AyaColorScheme;
import org.aya.pretty.style.AyaStyleFamily;
import org.aya.pretty.style.AyaStyleKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class ServerRenderOptions {
  public @Nullable String colorScheme;
  public @Nullable Map<String, String> override;
  public @Nullable RenderOptions.OutputTarget target = RenderOptions.OutputTarget.Plain;

  public ServerRenderOptions() {
  }

  public ServerRenderOptions(@Nullable String colorScheme, @Nullable Map<String, String> override, @Nullable RenderOptions.OutputTarget target) {
    this.colorScheme = colorScheme;
    this.override = override;
    this.target = target;
  }

  public @NotNull RenderOptions buildRenderOptions() {
    var colorSchemeName = this.colorScheme;
    if (colorSchemeName != null) colorSchemeName = colorSchemeName.toLowerCase();

    var colorScheme = switch (colorSchemeName) {
      case "emacs" -> AyaColorScheme.EMACS;
      case "intellij" -> AyaColorScheme.INTELLIJ;
      // fallback
      case null, default -> {
        var cause = colorSchemeName == null ? "unspecified" : "invalid";
        Log.w("Property 'colorScheme' is %s, 'Emacs' will be used.", cause);
        yield AyaColorScheme.EMACS;
      }
    };

    // override

    if (override != null) {
      override.forEach((name, colorCode) -> {
        try {
          var key = AyaStyleKey.valueOf(name);
          var value = Color.Adapter.parseColor(colorCode).getOrThrow();

          colorScheme.definedColors().put(key.key(), value);
        } catch (NumberFormatException e) {
          Log.w("Color '%s' is invalid, because: %s", colorCode, e.getMessage());
        } catch (IllegalArgumentException e) {
          Log.w("Key '%s' is not a valid style key.", name);
        }
      });
    }

    var opts = new RenderOptions();
    opts.colorScheme = RenderOptions.ColorSchemeName.Custom;
    opts.styleFamily = RenderOptions.StyleFamilyName.Default;
    // I know what I am doing.
    opts.doBadThing(colorScheme, AyaStyleFamily.DEFAULT);
    return opts;
  }

  public @NotNull RenderOptions.OutputTarget target() {
    return target == null ? RenderOptions.OutputTarget.Plain : target;
  }
}
