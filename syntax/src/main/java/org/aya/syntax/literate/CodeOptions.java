// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.literate;

import com.intellij.openapi.util.text.StringUtil;
import org.aya.prettier.AyaPrettierOptions;
import org.aya.prettier.AyaPrettierOptions.Key;
import org.aya.util.prettier.PrettierOptions;
import org.intellij.markdown.ast.ASTNode;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

public record CodeOptions(
  @NotNull NormalizeMode mode,
  @NotNull PrettierOptions options,
  @NotNull ShowCode showCode
) {
  public enum ShowCode {
    Concrete, Core, Type
  }
  public enum NormalizeMode {
    HEAD, FULL, NULL
  }

  public static @NotNull CodeOptions parseAttrSet(ASTNode attrSet, Function<ASTNode, String> toString) {
    var dist = new AyaPrettierOptions();
    var mode = NormalizeMode.NULL;
    var show = ShowCode.Core;
    for (var attr : attrSet.getChildren()) {
      if (attr.getType() != AyaBacktickParser.ATTR) continue;
      var key = toString.apply(attr.getChildren().getFirst());
      var value = toString.apply(attr.getChildren().getLast());
      if ("mode".equalsIgnoreCase(key)) {
        mode = cbt(value, NormalizeMode.values(), NormalizeMode.NULL);
        continue;
      }
      if ("show".equalsIgnoreCase(key)) {
        show = cbt(value, ShowCode.values(), ShowCode.Core);
        continue;
      }
      var cbt = cbt(key, Key.values(), null);
      if (cbt != null) {
        var isTrue = value.equalsIgnoreCase("true") || value.equalsIgnoreCase("yes");
        dist.map.put(cbt, isTrue);
      }
    }
    return new CodeOptions(mode, dist, show);
  }

  private static <E extends Enum<E>> E cbt(@NotNull String key, E[] values, E otherwise) {
    for (var val : values)
      if (StringUtil.containsIgnoreCase(val.name(), key)) return val;
    return otherwise;
  }
}
