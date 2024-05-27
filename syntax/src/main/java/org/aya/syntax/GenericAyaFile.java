// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax;

import kala.collection.immutable.ImmutableSeq;
import org.aya.literate.Literate;
import org.aya.syntax.concrete.stmt.Stmt;
import org.aya.syntax.literate.AyaLiterate;
import org.aya.util.error.SourceFile;
import org.aya.util.error.SourceFileLocator;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.MustBeInvokedByOverriders;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;

public interface GenericAyaFile {
  interface Factory {
    @NotNull GenericAyaFile createAyaFile(@NotNull SourceFileLocator locator, @NotNull Path path) throws IOException;
  }

  /**
   * Parse the file content and maybe do some extra processing.
   * For example, maybe we want to cache the result.
   */
  @MustBeInvokedByOverriders
  default @NotNull ImmutableSeq<Stmt> parseMe(@NotNull GenericAyaParser parser) throws IOException {
    return parser.program(codeFile(), originalFile());
  }

  /** @return the original source file, maybe a literate file */
  @NotNull SourceFile originalFile() throws IOException;
  /**
   * @return the valid aya source file
   * @implNote Literate files should override this method to return the extracted code file.
   */
  default @NotNull SourceFile codeFile() throws IOException { return originalFile(); }
  /**
   * @return the parsed literate output
   * @implNote This method wraps the file in a code block by default. Literate files should override this method.
   */
  default @NotNull Literate literate() throws IOException {
    var code = originalFile().sourceCode();
    var mockPos = new SourcePos(originalFile(), 0, code.length(), -1, -1, -1, -1);
    // ^ we only need index, so it's fine to use a mocked line/column
    return new AyaLiterate.AyaVisibleCodeBlock("aya", code, mockPos);
  }
}
