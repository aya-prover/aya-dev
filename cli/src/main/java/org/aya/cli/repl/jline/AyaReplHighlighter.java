// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl.jline;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import org.antlr.v4.runtime.Token;
import org.aya.distill.BaseDistiller;
import org.aya.parser.AyaLexer;
import org.aya.pretty.backend.string.StringPrinterConfig;
import org.aya.pretty.doc.Doc;
import org.jetbrains.annotations.NotNull;
import org.jline.reader.Highlighter;
import org.jline.reader.LineReader;
import org.jline.reader.impl.DefaultHighlighter;
import org.jline.utils.AttributedString;

import java.util.regex.Pattern;

public class AyaReplHighlighter extends DefaultHighlighter implements Highlighter {
  // TODO: generate this from the grammar
  static ImmutableSeq<Integer> KEYWORDS = ImmutableSeq.of(
    AyaLexer.INFIX,
    AyaLexer.INFIXL,
    AyaLexer.INFIXR,
    AyaLexer.TIGHTER,
    AyaLexer.LOOSER,
    AyaLexer.EXAMPLE,
    AyaLexer.COUNTEREXAMPLE,
    AyaLexer.ULEVEL,
    AyaLexer.TYPE,
    AyaLexer.AS,
    AyaLexer.OPEN,
    AyaLexer.IMPORT,
    AyaLexer.PUBLIC,
    AyaLexer.PRIVATE,
    AyaLexer.USING,
    AyaLexer.HIDING,
    AyaLexer.COERCE,
    AyaLexer.ERASE,
    AyaLexer.INLINE,
    AyaLexer.MODULE_KW,
    AyaLexer.BIND_KW,
    AyaLexer.MATCH,
    AyaLexer.ABSURD,
    AyaLexer.VARIABLE,
    AyaLexer.ABUSING,
    AyaLexer.DEF,
    AyaLexer.STRUCT,
    AyaLexer.DATA,
    AyaLexer.PRIM,
    AyaLexer.EXTENDS,
    AyaLexer.NEW_KW,
    AyaLexer.LSUC_KW,
    AyaLexer.LMAX_KW,
    AyaLexer.SIGMA,
    AyaLexer.LAMBDA,
    AyaLexer.PI,
    AyaLexer.FORALL,
    AyaLexer.TO,
    AyaLexer.IMPLIES,
    AyaLexer.SUCHTHAT,
    AyaLexer.COLON2,
    AyaLexer.BAR
  );

  @Override
  public AttributedString highlight(LineReader reader, String buffer) {
    var tokens = AyaReplParser.tokensNoEOF(buffer);
    return AttributedString.fromAnsi(tokensToDoc(tokens)
      .renderToString(StringPrinterConfig.unixTerminal()));
  }

  private @NotNull Doc tokensToDoc(@NotNull SeqView<Token> tokens) {
    return Doc.cat(tokens.map(t -> KEYWORDS.contains(t.getType())
      ? Doc.styled(BaseDistiller.KEYWORD, t.getText())
      : Doc.plain(t.getText())));
  }

  @Override
  public void setErrorPattern(Pattern errorPattern) {
    super.setErrorPattern(errorPattern);
  }

  @Override
  public void setErrorIndex(int errorIndex) {
    super.setErrorIndex(errorIndex);
  }
}
