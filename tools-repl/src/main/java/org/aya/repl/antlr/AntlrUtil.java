// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.repl.antlr;

import kala.collection.SeqView;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.aya.util.error.SourceFile;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;

/**
 * @author imkiva
 */
public interface AntlrUtil {
  static @NotNull SourcePos sourcePosOf(TerminalNode node, SourceFile sourceFile) {
    var token = node.getSymbol();
    var line = token.getLine();
    return new SourcePos(
      sourceFile,
      token.getStartIndex(),
      token.getStopIndex(),
      line,
      token.getCharPositionInLine(),
      line,
      token.getCharPositionInLine() + token.getText().length() - 1
    );
  }

  static @NotNull SourcePos sourcePosOf(ParserRuleContext ctx, SourceFile sourceFile) {
    var start = ctx.getStart();
    var end = ctx.getStop();
    return new SourcePos(
      sourceFile,
      start.getStartIndex(),
      end.getStopIndex(),
      start.getLine(),
      start.getCharPositionInLine(),
      end.getLine(),
      end.getCharPositionInLine() + end.getText().length() - 1
    );
  }
  static @NotNull SourcePos sourcePosForSubExpr(
    @NotNull SourceFile sourceFile,
    @NotNull SeqView<SourcePos> params,
    @NotNull SourcePos bodyPos
  ) {
    var restParamSourcePos = params.fold(SourcePos.NONE, (acc, it) -> {
      if (acc == SourcePos.NONE) return it;
      return new SourcePos(sourceFile, acc.tokenStartIndex(), it.tokenEndIndex(),
        acc.startLine(), acc.startColumn(), it.endLine(), it.endColumn());
    });
    return new SourcePos(
      sourceFile,
      restParamSourcePos.tokenStartIndex(),
      bodyPos.tokenEndIndex(),
      restParamSourcePos.startLine(),
      restParamSourcePos.startColumn(),
      bodyPos.endLine(),
      bodyPos.endColumn()
    );
  }
}
