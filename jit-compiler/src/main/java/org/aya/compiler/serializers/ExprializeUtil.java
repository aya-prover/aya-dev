// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.serializers;

import com.intellij.openapi.util.text.StringUtil;
import org.aya.util.Panic;
import org.jetbrains.annotations.NotNull;

public interface ExprializeUtil {
  String CLASS_PANIC = getJavaRef(Panic.class);
  static @NotNull String makeString(@NotNull String raw) {
    return "\"" + StringUtil.escapeStringCharacters(raw) + "\"";
  }

  static @NotNull String getInstance(@NotNull String defName) {
    return defName + "." + AyaSerializer.STATIC_FIELD_INSTANCE;
  }

  /**
   * Get the reference to {@param clazz}, it should be imported to current file.
   */
  static @NotNull String getJavaRef(@NotNull Class<?> clazz) {
    return clazz.getSimpleName();
  }
}
