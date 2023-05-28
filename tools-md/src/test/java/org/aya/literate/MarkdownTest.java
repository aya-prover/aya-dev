// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.literate;

import kala.collection.immutable.ImmutableSeq;
import kala.control.Option;
import org.aya.literate.parser.BaseMdParser;
import org.aya.literate.parser.InterestingLanguage;
import org.aya.util.error.SourceFile;
import org.aya.util.reporter.IgnoringReporter;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class MarkdownTest {
  @Test public void boring() {
    assertInstanceOf(Literate.Raw.class, parse("""
      ```markdown
      ~~~markdown
      ~~~
      ```
      """, ImmutableSeq.empty()));
    assertInstanceOf(Literate.Raw.class, parse("""
      ~~~markdown
      ```markdown
      ```
      ~~~
      """, ImmutableSeq.empty()));
  }

  @Test public void mdIsInteresting() {
    assertInstanceOf(Literate.CodeBlock.class, parse("""
      ```markdown
      ~~~markdown
      ~~~~markdown
      ~~~~
      ~~~
      ```
      """, ImmutableSeq.of(InterestingLanguage.ALL)));
    assertInstanceOf(Literate.CodeBlock.class, parse("""
      ~~~markdown
      ```markdown
      ````markdown
      ````
      ```
      ~~~
      """, ImmutableSeq.of(InterestingLanguage.ALL)));
  }

  @Test public void interesting() {
    var lit = parse("""
      ```c
      1
      ```
      ~~~java
      2
      ~~~
      ```aya
      3
      ```
      """, ImmutableSeq.of(InterestingLanguage.of("c"), InterestingLanguage.of("aya")));
    assertInstanceOf(Literate.Many.class, lit);
    assertEquals(3, ((Literate.Many) lit).children().size());
    assertInstanceOf(Literate.CodeBlock.class, ((Literate.Many) lit).children().get(0));
    assertInstanceOf(Literate.Raw.class, ((Literate.Many) lit).children().get(1));
    assertInstanceOf(Literate.CodeBlock.class, ((Literate.Many) lit).children().get(2));
  }

  @Test public void indent() {
    parse(false, """
        ```java
        public class Main {}
        ```
      """, """
        ```java
        public class Main {}
        ```
      """);
  }

  @Test public void markdownInMarkdown() {
    // The code block is treated as plain text if the language is not interesting.
    // Arbitrary nesting level of markdown is supported.
    parse(false, """
      ```markdown
      ~~~markdown
      ~~~
      ```
      """, """
      ```markdown
      ~~~markdown
      ~~~
      ```
      """);
    parse(false, """
      ~~~markdown
      ```markdown
      ```
      ~~~
      """, """
      ~~~markdown
      ```markdown
      ```
      ~~~
      """);
    parse(false, """
      ```markdown
      ~~~markdown
      ~~~~markdown
      ~~~~
      ~~~
      ```
      """, """
      ```markdown
      ~~~markdown
      ~~~~markdown
      ~~~~
      ~~~
      ```
      """);
    // If md is itself interesting, this is OK.
    parse(true, """
      ~~~markdown
      ```markdown
      ```
      ~~~
      """, """
      ~~~markdown
      ```markdown
      ```
      ~~~
      """);
    // oops... but usually, we don't need to treat Markdown an interesting language.
    parse(true, """
      ```markdown
      ~~~markdown
      ~~~
      ```
      """, """
      ~~~markdown
      ~~~markdown
      ~~~
      ~~~
      """);
  }

  public void parse(boolean mdIsInteresting, @NotNull @Language("Markdown") String input, @NotNull @Language("Markdown") String expected) {
    var lit = parse(input, mdIsInteresting ? ImmutableSeq.of(InterestingLanguage.of("markdown")) : ImmutableSeq.empty());
    assertEquals(expected, lit.toDoc().renderToMd());
  }

  public @NotNull Literate parse(@NotNull @Language("Markdown") String md, @NotNull ImmutableSeq<InterestingLanguage<?>> lang) {
    var file = new SourceFile("test.md", Option.none(), md);
    return new BaseMdParser(file, IgnoringReporter.INSTANCE, lang).parseLiterate();
  }
}
