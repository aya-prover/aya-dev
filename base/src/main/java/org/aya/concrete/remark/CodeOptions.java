// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.remark;

import org.aya.concrete.GenericAyaParser;
import org.aya.generic.util.NormalizeMode;
import org.aya.pretty.doc.Doc;
import org.aya.util.distill.DistillerOptions;
import org.aya.util.error.SourcePos;
import org.commonmark.node.Code;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 */
public record CodeOptions(
  @NotNull NormalizeMode mode,
  @NotNull DistillerOptions options,
  @NotNull ShowCode showCode
) {
  public static final @NotNull CodeOptions DEFAULT =
    new CodeOptions(NormalizeMode.NULL, DistillerOptions.pretty(), ShowCode.Core);

  public static @NotNull Literate analyze(@NotNull Code code, @NotNull GenericAyaParser parser, @NotNull SourcePos sourcePos) {
    if (code.getFirstChild() instanceof CodeAttrProcessor.Attr attr) {
      return new Literate.Code(parser.expr(code.getLiteral(), sourcePos), attr.options);
    } else return new Literate.Raw(Doc.code(code.getLiteral()));
  }

  public enum ShowCode {
    Concrete, Core, Type
  }
}
