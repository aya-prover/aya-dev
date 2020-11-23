// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.api.error;

import org.jetbrains.annotations.NotNull;
import org.mzi.pretty.doc.Doc;

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
