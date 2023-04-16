// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.utils;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonParseException;
import org.aya.cli.render.Color;
import org.aya.cli.render.RenderOptions;
import org.aya.prettier.AyaPrettierOptions;
import org.aya.util.prettier.PrettierOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.UnknownNullability;
import org.jetbrains.annotations.VisibleForTesting;

import java.io.IOException;

/**
 * A JSON object for {@link org.aya.cli.render.RenderOptions} and {@link org.aya.prettier.AyaPrettierOptions }
 *
 * @see org.aya.cli.interactive.ReplConfig
 * @see org.aya.cli.library.json.LibraryConfigData
 */
public class LiteratePrettierOptions {
  public @NotNull AyaPrettierOptions prettierOptions = AyaPrettierOptions.pretty();
  public @UnknownNullability RenderOptions renderOptions = new RenderOptions();

  public void checkDeserialization() {
    if (prettierOptions.map.isEmpty()) prettierOptions.reset();
    // maintain the Nullability, renderOptions is probably null after deserializing
    if (renderOptions == null) renderOptions = new RenderOptions();
    renderOptions.checkDeserialization();
    try {
      renderOptions.stylist(RenderOptions.OutputTarget.Unix);
    } catch (IOException | JsonParseException e) {
      System.err.println("Failed to load stylist from config file, using default stylist instead.");
    }
  }

  @VisibleForTesting public static @NotNull GsonBuilder gsonBuilder(@NotNull GsonBuilder builder) {
    return builder
      .registerTypeAdapter(Color.class, new Color.Adapter())
      .registerTypeAdapter(PrettierOptions.Key.class, (JsonDeserializer<PrettierOptions.Key>) (json, typeOfT, context) -> {
        try {
          return AyaPrettierOptions.Key.valueOf(json.getAsString());
        } catch (IllegalArgumentException ignored) {
          return null;
        }
      });
  }
}
