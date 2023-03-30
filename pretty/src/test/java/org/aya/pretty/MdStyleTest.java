// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.pretty;

import org.aya.pretty.backend.md.MdStyle;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Language;
import org.aya.pretty.doc.Link;
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
            
      1\\. fake list
            
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
      Doc.styled(MdStyle.GFM.Paragraph, Doc.hyperLink("Click me", Link.page("https://google.com"))),
      Doc.styled(MdStyle.h(2), "H2"),
      Doc.styled(MdStyle.h(3), "H3"),
      Doc.styled(MdStyle.h(4), "H4"),
      Doc.styled(MdStyle.h(5), "H5"),
      Doc.styled(MdStyle.h(6), "H6"),
      Doc.styled(MdStyle.GFM.BlockQuote, "BlockQuote"),
      Doc.plain("1. "),
      Doc.styled(MdStyle.GFM.Paragraph, "fake list"),
      Doc.plain("I love Java."),
      Doc.plain("I love Aya."),
      Doc.plain("I love Aya's pretty printer."),
      Doc.styled(MdStyle.GFM.Paragraph, "I love Java"),
      Doc.styled(MdStyle.GFM.Paragraph, "I love Aya"),
      Doc.styled(MdStyle.GFM.Paragraph, "I love Aya's pretty printer."),
      Doc.codeBlock(Language.Builtin.Aya, "data Nat | zero | suc Nat"),
      Doc.styled(MdStyle.GFM.Paragraph, "Look! She is beautiful")
    );
  }

  @Test
  public void testEscapeDocs() {
    assertEquals("!\"\\#$%\\&'\\(\\)\\*\\+,-./:\\;\\<=\\>?@\\[\\\\\\]^\\_\\`{\\|}\\~",
      escapeDoc12().renderToMd());
    // In fact, the markdown: `\→\A\a\ \3\φ\«` also produces the text as escapeDoc1 describes,
    // but it makes the Java/Markdown code more complex/confusing (for me at least)
    assertEquals("\\\\→\\\\A\\\\a\\\\ \\\\3\\\\φ\\\\«", escapeDoc13().renderToMd());
    assertEquals("""
      \\*not emphasized\\*
      \\<br/\\> not a tag
      \\[not a link\\]\\(/foo\\)
      \\`not code\\`
      1\\. not a list
      \\* not a list
      \\# not a heading
      \\[foo\\]: /url "not a reference"
      \\&ouml\\; not a character entity
      """, escapeDoc14().renderToMd());
    assertEquals("`\\[\\``", escapeDoc17().renderToMd());
  }

  @NotNull private Doc escapeDoc12() {
    return Doc.plain("!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~");
  }

  @NotNull private Doc escapeDoc13() {
    return Doc.plain("\\→\\A\\a\\ \\3\\φ\\«");
  }

  @NotNull private Doc escapeDoc14() {
    return Doc.plain("""
      *not emphasized*
      <br/> not a tag
      [not a link](/foo)
      `not code`
      1. not a list
      * not a list
      # not a heading
      [foo]: /url "not a reference"
      &ouml; not a character entity
      """);
  }

  @NotNull private Doc escapeDoc17() {
    return Doc.code("\\[\\`");
  }

  @Test
  public void testList() {
    assertEquals("""
      - first
        
        third
      - fourth
        - 4.1
        - 4.2
            
      """.stripIndent(), bulletList().renderToMd().stripIndent());
    assertEquals("""
      1. first

         third
      2. fourth
         - 4.1
         - 4.2

      """.stripIndent(), orderedList().renderToMd().stripIndent());
  }

  @NotNull private Doc bulletList() {
    return Doc.bullet(
      Doc.cat(Doc.plain("first"), Doc.line(), Doc.line(), Doc.plain("third")),
      Doc.cat(Doc.plain("fourth"), Doc.bullet(
        Doc.plain("4.1"),
        Doc.plain("4.2")
      ))
    );
  }

  @NotNull private Doc orderedList() {
    return Doc.ordered(
      Doc.cat(Doc.plain("first"), Doc.line(), Doc.line(), Doc.plain("third")),
      Doc.cat(Doc.plain("fourth"), Doc.bullet(
        Doc.plain("4.1"),
        Doc.plain("4.2")
      ))
    );
  }
}
