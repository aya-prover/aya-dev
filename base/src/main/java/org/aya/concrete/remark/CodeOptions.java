// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.remark;

import org.aya.api.distill.DistillerOptions;
import org.aya.api.util.NormalizeMode;
import org.aya.concrete.parse.AyaParsing;
import org.aya.concrete.parse.AyaProducer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * @param mode <code>null</code> if as-is
 * @author ice1000
 */
public record CodeOptions(
  @Nullable NormalizeMode mode,
  @NotNull DistillerOptions options,
  @NotNull ShowCode showCode
) {
  public static final @NotNull Pattern PARSER = Pattern.compile(
    "\\A(([\\w ]*)(\\|([\\w ]*)\\|([\\w ]*))?:)?(.*)\\z");

  public static @NotNull Literate.Code analyze(@NotNull String literal, @NotNull AyaProducer producer) {
    var showImplicitArgs = true;
    var showImplicitPats = true;
    var showLambdaTypes = false;
    var showLevels = false;
    var matcher = PARSER.matcher(literal);
    var found = matcher.find();
    assert found;
    var commonOpt = matcher.group(2);
    NormalizeMode mode = null;
    ShowCode showCode = ShowCode.Concrete;
    if (commonOpt != null) {
      commonOpt = commonOpt.toUpperCase(Locale.ROOT);
      if (commonOpt.contains("C")) {
        showCode = ShowCode.Core;
      } else if (commonOpt.contains("T")) {
        showCode = ShowCode.Type;
      }
      if (commonOpt.contains("W")) {
        mode = NormalizeMode.WHNF;
      } else if (commonOpt.contains("N")) {
        mode = NormalizeMode.NF;
      }
    }
    var open = matcher.group(4);
    var close = matcher.group(5);
    if (open != null && close != null) {
      open = open.toUpperCase(Locale.ROOT);
      close = close.toUpperCase(Locale.ROOT);
      if (close.contains("I")) showImplicitArgs = false;
      if (open.contains("U")) showLevels = true;
      if (open.contains("L")) showLambdaTypes = true;
      if (close.contains("P")) showImplicitPats = false;
    }
    var expr = producer.visitExpr(AyaParsing.parser(matcher.group(6)).expr());
    var distillOpt = new DistillerOptions(
      true,
      showImplicitArgs,
      showImplicitPats,
      showLambdaTypes,
      showLevels);
    var options = new CodeOptions(mode, distillOpt, showCode);
    return new Literate.Code(expr, options);
  }

  public enum ShowCode {
    Concrete, Core, Type
  }
}
