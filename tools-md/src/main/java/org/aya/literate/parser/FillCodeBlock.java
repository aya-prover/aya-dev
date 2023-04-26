// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.literate.parser;

import kala.collection.mutable.MutableList;
import org.commonmark.node.*;
import org.commonmark.parser.PostProcessor;

/**
 * Make the source spans of the code block continuous
 */
public class FillCodeBlock implements PostProcessor {
  public static final class Visitor extends AbstractVisitor {
    private Visitor() {}

    @Override public void visit(FencedCodeBlock codeBlock) {
      if (codeBlock != null) {
        var sourceSpans = codeBlock.getSourceSpans();

        if (sourceSpans != null && sourceSpans.size() >= 2) {
          var it = sourceSpans.iterator();
          var lastSpan = it.next();
          var conSourceSpans = MutableList.of(lastSpan);

          while (it.hasNext()) {
            var curSpan = it.next();

            // Continuous?
            while (lastSpan.getLineIndex() + 1 != curSpan.getLineIndex()) {
              // No, fill the empty line
              lastSpan = SourceSpan.of(lastSpan.getLineIndex() + 1, -1, 0);
              conSourceSpans.append(lastSpan);
            }

            // Ahh, now continuous!
            lastSpan = curSpan;
            conSourceSpans.append(lastSpan);
          }

          codeBlock.setSourceSpans(conSourceSpans.asJava());
        }
      }

      super.visit(codeBlock);
    }
  }

  public final static FillCodeBlock INSTANCE = new FillCodeBlock();
  public final static Visitor VISITOR = new Visitor();

  private FillCodeBlock() {}

  @Override public Node process(Node node) {
    if (node instanceof Document doc) VISITOR.visit(doc);
    return node;
  }
}
