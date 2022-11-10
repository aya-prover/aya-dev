// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl.render;

import com.google.gson.*;
import kala.control.Result;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Type;

public class Color {
  public final static class Adapter implements JsonDeserializer<Color>, JsonSerializer<Color> {
    public static @NotNull Result<Integer, String> parseColor(@NotNull String color) {
      String colorCode;
      if (color.charAt(0) == '#') colorCode = color.substring(1);
      else if (color.startsWith("0x")) colorCode = color.substring(2);
      else colorCode = color;

      if (colorCode.length() != 6) {
        return Result.err("The color code \"" + color + "\" is too long or too short!");
      }

      try {
        var result = Integer.parseInt(colorCode, 16);

        return Result.ok(result);
      } catch (NumberFormatException e) {
        return Result.err(e.getMessage());
      }
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

        if (parsed.isErr()) {
          throw new JsonParseException(parsed.getErr());
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
