// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.api.error;

import org.jetbrains.annotations.NotNull;
import org.mzi.pretty.doc.Doc;
import org.mzi.pretty.error.PrettyError;
import org.mzi.pretty.error.Span;

/**
 * @author ice1000
 */
public interface Problem {
  enum Severity {
    INFO,
    GOAL,
    ERROR,
    WARN,
  }

  enum Stage {
    TERCK,
    TYCK,
    RESOLVE,
    PARSE,
    OTHER
  }

  @NotNull SourcePos sourcePos();
  @NotNull Doc describe();
  @NotNull Severity level();
  default @NotNull Stage stage() {
    return Stage.OTHER;
  }

  default @NotNull PrettyError toPrettyError(@NotNull String filePath,
                                             @NotNull String sourceCode,
                                             @NotNull Doc noteMessage) {
    var sourcePos = sourcePos();
    return new PrettyError(
      filePath,
      Span.from(sourceCode, sourcePos.tokenStartIndex(), sourcePos.tokenEndIndex()),
      describe(),
      noteMessage
    );
  }

  interface Error extends Problem {
    @Override
    default @NotNull Severity level() {
      return Severity.ERROR;
    }
  }

  interface Warn extends Problem {
    @Override
    default @NotNull Severity level() {
      return Severity.WARN;
    }
  }

  interface Goal extends Problem {
    @Override
    default @NotNull Severity level() {
      return Severity.GOAL;
    }
  }

  interface Info extends Problem {
    @Override
    default @NotNull Severity level() {
      return Severity.INFO;
    }
  }
}
