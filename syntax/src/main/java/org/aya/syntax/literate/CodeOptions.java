// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.literate;

import org.aya.literate.Literate;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Language;
import org.aya.util.error.SourcePos;
import org.aya.util.prettier.PrettierOptions;
import org.commonmark.node.Code;
import org.jetbrains.annotations.NotNull;

public record CodeOptions(
  @NotNull NormalizeMode mode,
  @NotNull PrettierOptions options,
  @NotNull ShowCode showCode
) {
  public static @NotNull Literate analyze(@NotNull Code code, @NotNull SourcePos sourcePos) {
    return code.getFirstChild() instanceof CodeAttrProcessor.Attr attr
      ? new AyaLiterate.AyaInlineCode(code.getLiteral(), sourcePos, attr.options)
      : new Literate.Raw(Doc.code(Language.Builtin.Plain, Doc.plain(code.getLiteral())));
  }

  public enum ShowCode {
    Concrete, Core, Type
  }
  public enum NormalizeMode {
    HEAD, FULL, NULL
  }
}
