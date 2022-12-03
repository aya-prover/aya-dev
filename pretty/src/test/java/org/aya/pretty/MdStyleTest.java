// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.pretty;

import org.aya.pretty.backend.md.MdStyle;
import org.aya.pretty.backend.string.LinkId;
import org.aya.pretty.doc.Doc;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MdStyleTest {
  @Test public void testMdStyle() {
    assertEquals("""
      # H1
      [Click me](https://google.com)
            
      ## H2
      ### H3
      #### H4
      ##### H5
      ###### H6
      > BlockQuote
            
      I love Java.I love Aya.I love Aya's pretty printer.I love Java
            
      I love Aya
            
      I love Aya's pretty printer.
            
      ```aya
      data Nat | zero | suc Nat
      ```
      Look! She is beautiful
            
      """.stripIndent(), doc().renderToMd());
  }

  @NotNull private Doc doc() {
    return Doc.cat(
      Doc.styled(MdStyle.h(1), "H1"),
      Doc.styled(MdStyle.GFM.Paragraph, Doc.hyperLink("Click me", new LinkId("https://google.com"))),
      Doc.styled(MdStyle.h(2), "H2"),
      Doc.styled(MdStyle.h(3), "H3"),
      Doc.styled(MdStyle.h(4), "H4"),
      Doc.styled(MdStyle.h(5), "H5"),
      Doc.styled(MdStyle.h(6), "H6"),
      Doc.styled(MdStyle.GFM.BlockQuote, "BlockQuote"),
      Doc.plain("I love Java."),
      Doc.plain("I love Aya."),
      Doc.plain("I love Aya's pretty printer."),
      Doc.styled(MdStyle.GFM.Paragraph, "I love Java"),
      Doc.styled(MdStyle.GFM.Paragraph, "I love Aya"),
      Doc.styled(MdStyle.GFM.Paragraph, "I love Aya's pretty printer."),
      Doc.styled(new MdStyle.CodeBlock("aya"), "data Nat | zero | suc Nat"),
      Doc.styled(MdStyle.GFM.Paragraph, "Look! She is beautiful")
    );
  }
}
