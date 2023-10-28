// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.parse;

import com.intellij.psi.DefaultPsiParser;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableMap;
import org.aya.cli.literate.FlclToken;
import org.aya.parser.FlclLanguage;
import org.aya.parser.FlclParserDefinition;
import org.aya.util.error.SourceFile;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;

public record FlclParser(
  @NotNull Reporter reporter,
  @NotNull MutableMap<String, ImmutableSeq<String>> decls
) {
  public @NotNull ImmutableSeq<FlclToken> program(@NotNull SourceFile file) {
    throw new UnsupportedOperationException("TODO");
  }

  private static class FlclFleetParser extends DefaultPsiParser {
    public FlclFleetParser() {
      super(new FlclParserDefinition(ParserUtil.forLanguage(FlclLanguage.INSTANCE)));
    }
  }
}
