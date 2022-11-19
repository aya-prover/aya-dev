// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.literate.utils;

import com.intellij.lexer.FlexLexer;
import kala.collection.Seq;
import kala.collection.immutable.ImmutableSeq;
import org.aya.cli.literate.HighlightInfo;
import org.aya.cli.literate.HighlightInfoHolder;
import org.aya.cli.literate.Highlighter;
import org.aya.concrete.stmt.Stmt;
import org.aya.parser.AyaParserDefinitionBase;
import org.aya.util.distill.DistillerOptions;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import static org.aya.cli.literate.HighlightInfoType.*;

public interface HighlighterUtil {
  static @NotNull HighlightInfoHolder highlight(@NotNull ImmutableSeq<Stmt> program, @NotNull DistillerOptions options) {
    var distiller = new Highlighter(options);
    program.forEach(distiller);
    return distiller.result();
  }

  @Contract(value = "_, _ -> param1", mutates = "param1")
  static @NotNull HighlightInfoHolder highlightKeywords(@NotNull HighlightInfoHolder base, @NotNull Seq<FlexLexer.Token> tokens) {
    var keywords = tokens.view()
      .filter(x -> AyaParserDefinitionBase.KEYWORDS.contains(x.type()))
      .map(token -> new HighlightInfo(token.range(), new Lit(LitKind.Keyword)));

    keywords.forEach(base::addInfo);
    return base;
  }
}
