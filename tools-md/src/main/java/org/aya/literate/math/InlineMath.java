// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.literate.math;

import org.commonmark.node.CustomNode;
import org.commonmark.node.Delimited;
import org.commonmark.node.Nodes;
import org.commonmark.node.SourceSpans;
import org.commonmark.parser.delimiter.DelimiterProcessor;
import org.commonmark.parser.delimiter.DelimiterRun;
import org.jetbrains.annotations.NotNull;

/**
 * see: <a href="https://github.com/commonmark/commonmark-java/blob/main/commonmark-ext-gfm-strikethrough/src/main/java/org/commonmark/ext/gfm/strikethrough/Strikethrough.java">...</a>
 */
public class InlineMath extends CustomNode implements Delimited {
  private static final @NotNull String DELIMITER = "$";

  @Override public @NotNull String getOpeningDelimiter() {
    return DELIMITER;
  }

  @Override public @NotNull String getClosingDelimiter() {
    return DELIMITER;
  }

  public enum Processor implements DelimiterProcessor {
    INSTANCE;

    @Override public char getOpeningCharacter() {
      return '$';
    }

    @Override public char getClosingCharacter() {
      return '$';
    }

    @Override public int getMinLength() {
      return 1;
    }

    @Override public int process(@NotNull DelimiterRun openingRun, @NotNull DelimiterRun closingRun) {
      if (openingRun.length() == closingRun.length() && openingRun.length() == getMinLength()) {
        var opener = openingRun.getOpener();
        // Wrap nodes between delimiters in the node.
        var math = new InlineMath();

        var sourceSpans = new SourceSpans();
        sourceSpans.addAllFrom(openingRun.getOpeners(openingRun.length()));
        for (var node : Nodes.between(opener, closingRun.getCloser())) {
          math.appendChild(node);
          sourceSpans.addAll(node.getSourceSpans());
        }
        sourceSpans.addAllFrom(closingRun.getClosers(closingRun.length()));
        math.setSourceSpans(sourceSpans.getSourceSpans());

        opener.insertAfter(math);
        return openingRun.length();
      } else {
        return 0;
      }
    }
  }
}
