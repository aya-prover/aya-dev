// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.pretty.error;

import org.glavo.kala.control.Option;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * @author kiva
 */
public record PrettyError(
  int start,
  int end,
  int startLine,
  int startCol,
  int endLine,
  int endCol,
  @NotNull String message,
  @NotNull String filePath,
  @NotNull String line,
  @NotNull Option<String> continuedLine
) {
  private @NotNull String spacing() {
    var line = Math.max(startLine, endLine);
    var lineLen = countLengthOfFormatted(line);
    return " ".repeat(Math.max(0, lineLen));
  }

  private @NotNull String underline() {
    var underline = new StringBuilder();
    var start = startCol;
    var end = Option.some(endCol);
    if (start > endCol) {
      end = Option.some(start + 1);
      start = endCol - 1;
    }

    var offset = start - 1;
    var lineChars = line.chars();

    lineChars.limit(offset).forEach(c -> {
      if (c == '\t') underline.append('\t');
      else underline.append(' ');
    });

    if (end.isDefined()) {
      var e = end.get();
      if (e - start > 1) {
        underline.append('^');
        underline.append("-".repeat(Math.max(0, e - start - 2)));
        underline.append('^');
      } else {
        underline.append('^');
      }
    } else {
      underline.append("^---");
    }

    return underline.toString();
  }

  private int countLengthOfFormatted(int i) {
    return String.format(Locale.getDefault(), "%d", i).length();
  }
}
