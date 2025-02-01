// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.

import java.lang.reflect.Field;

import org.aya.compiler.serializers.AyaSerializer;
import org.aya.syntax.compile.JitDef;
import org.jetbrains.annotations.NotNull;

public record CompileTester(@NotNull ClassLoader cl) {
  @SuppressWarnings("unchecked")
  public <T> @NotNull Class<T> load(String qualified) {
    try {
      return (Class<T>) cl.loadClass(qualified);
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }

  @SuppressWarnings("unchecked")
  public <T extends JitDef> T getInstance(@NotNull Class<T> clazz) {
    try {
      Field field = clazz.getField(AyaSerializer.STATIC_FIELD_INSTANCE);
      field.setAccessible(true);
      return (T) field.get(null);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  public <T extends JitDef> T loadInstance(String qualified) {
    return getInstance(load(qualified));
  }
}
