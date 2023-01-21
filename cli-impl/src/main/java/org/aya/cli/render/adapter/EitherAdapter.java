// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.render.adapter;

import com.google.gson.*;
import kala.control.Either;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

public class EitherAdapter implements JsonSerializer<Either<?, ?>>, JsonDeserializer<Either<?, ?>> {
  /**
   * Avoiding deserialize {@code "123"} to {@code (Integer) 123} instead of {@code (String) "123"}
   *
   * @implNote It should not return true for the same {@param json} with different {@param types}.
   */
  public static boolean bestMatch(@NotNull JsonElement json, @NotNull Type type) {
    if (json.isJsonPrimitive()) {
      var prim = json.getAsJsonPrimitive();

      return (prim.isString() && type == String.class)
        || (prim.isNumber() && type instanceof Class<?> clazz && Number.class.isAssignableFrom(clazz))
        || (prim.isBoolean() && type == Boolean.class);
    } else {
      // non primitive
      return false;
    }
  }

  @Override
  public JsonElement serialize(Either<?, ?> src, Type typeOfSrc, JsonSerializationContext context) {
    assert src != null;
    assert typeOfSrc != null;
    assert context != null;

    if (typeOfSrc instanceof ParameterizedType generic) {
      var typeArgs = generic.getActualTypeArguments();
      assert typeArgs.length == 2;

      return src.fold(
        x -> context.serialize(x, typeArgs[0]),
        x -> context.serialize(x, typeArgs[1]));
    } else {
      throw new JsonParseException("Unable to serialize an Either<?, ?> without a ParameterizedType");
    }
  }

  @Override
  public Either<?, ?> deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
    assert json != null;
    assert typeOfT != null;
    assert context != null;

    if (typeOfT instanceof ParameterizedType generic) {
      var typeArgs = generic.getActualTypeArguments();
      assert typeArgs.length == 2;

      var bestMatch0 = bestMatch(json, typeArgs[0]);
      var bestMatch1 = bestMatch(json, typeArgs[1]);

      if (bestMatch0) return Either.left(context.deserialize(json, typeArgs[0]));
      if (bestMatch1) return Either.right(context.deserialize(json, typeArgs[1]));

      // no best match, try to deserialize each of them
      try {
        return Either.left(context.deserialize(json, typeArgs[0]));
      } catch (JsonParseException ex) {
        return Either.right(context.deserialize(json, typeArgs[1]));
      }
    } else {
      throw new JsonParseException("Unable to deserialize an Either<?, ?> without a ParameterizedType");
    }
  }
}
