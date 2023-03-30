// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.remark.code;

import org.aya.concrete.remark.Literate;
import org.aya.generic.util.NormalizeMode;
import org.aya.pretty.doc.Doc;
import org.aya.util.error.SourcePos;
import org.aya.util.prettier.PrettierOptions;
import org.commonmark.node.Code;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 */
public record CodeOptions(
  @NotNull NormalizeMode mode,
  @NotNull PrettierOptions options,
  @NotNull ShowCode showCode
) {
  public static @NotNull Literate analyze(@NotNull Code code, @NotNull SourcePos sourcePos) {
    return switch (code.getFirstChild()) {
      case CodeAttrProcessor.Attr attr -> new Literate.Code(code.getLiteral(), sourcePos, attr.options);
      case default, null -> new Literate.Raw(Doc.code(code.getLiteral()));
    };
  }

  public enum ShowCode {
    Concrete, Core, Type
  }
}
