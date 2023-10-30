// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.literate;

import org.aya.pretty.doc.Doc;
import org.aya.util.prettier.PrettierOptions;
import org.jetbrains.annotations.NotNull;

public record FlclFaithfulPrettier(@Override @NotNull PrettierOptions options)
  implements FaithfulPrettier {
  public @NotNull Doc highlight(@NotNull FlclToken.File file) {
    var highlights = file.tokens().map(FlclToken::toInfo).sorted();
    FaithfulPrettier.checkHighlights(highlights);
    return doHighlight(file.sourceCode(), file.startIndex(), highlights);
  }
}
