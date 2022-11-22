// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.render;

import com.google.gson.*;
import kala.control.Try;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Type;

public class Color {
  // See https://code.visualstudio.com/api/references/theme-color#color-formats
  public final static class Adapter implements JsonDeserializer<Color>, JsonSerializer<Color> {
    // 0x00000FFF -> 0x000F0F0F
    public static int parse3(int color) {
      color = color & 0x00000FFF;
      return ((color & 0xF00) << 8) |
        ((color & 0xF0) << 4) |
        (color & 0xF);
    }

    // 0x0000FFF0 -> 0x000F0F0F
    public static int parse4(int color) {
      // dismiss alpha channel;
      color = color & 0x0000FFF0;
      return ((color & 0xF000) << 4) |
        (color & 0xF00) |
        ((color & 0xF0) >>> 4);
    }

    // 0xFFFFFF00 -> 0x00FFFFFFF
    public static int parse8(int color) {
      return color >>> 8;
    }

    // 0x00FFFFFF -> 0x00FFFFFF
    public static int parse6(int color) {
      return color;
    }

    public static @NotNull Try<Integer> parseColor(@NotNull String color) {
      String colorCode;
      if (color.charAt(0) == '#') colorCode = color.substring(1);
      else if (color.startsWith("0x")) colorCode = color.substring(2);
      else colorCode = color;

      return Try.of(() -> {
        // Integer.parseInt("80000000", 16) will fail
        var value = (int) Long.parseLong(colorCode, 16);
        return switch (colorCode.length()) {
          case 3 -> parse3(value);
          case 4 -> parse4(value);
          case 6 -> parse6(value);
          case 8 -> parse8(value);
          default -> throw new NumberFormatException("Invalid color: " + color);
        };
      });
    }

    @Override
    public JsonElement serialize(Color src, Type typeOfSrc, JsonSerializationContext context) {
      assert src != null;
      return new JsonPrimitive(String.format("#%06X", src.color));
    }

    @Override
    public Color deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
      assert json != null;

      if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) {
        var colorCode = json.getAsString();
        var parsed = parseColor(colorCode);

        if (parsed.isFailure()) {
          throw new JsonParseException(parsed.getCause());
        }

        return new Color(parsed.get());
      } else {
        throw new JsonParseException("expected 'string'");
      }
    }
  }

  public int color;

  public Color() {
  }

  public Color(int color) {
    this.color = color;
  }
}
