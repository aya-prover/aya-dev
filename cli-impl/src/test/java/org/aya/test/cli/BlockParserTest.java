// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.test.cli;

import kala.control.Option;
import org.aya.cli.issue.BlockParser;
import org.aya.cli.issue.IssueParser;
import org.aya.prettier.AyaPrettierOptions;
import org.aya.util.position.SourceFile;
import org.aya.util.reporter.ThrowingReporter;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class BlockParserTest {
  public static final @NotNull String SOURCE = """
    ## Versions
    
    Aya version: <!-- BEGIN VERSION -->`0.39-SNAPSHOT`<!-- END VERSION -->
    
    ## Codes
    
    <!-- BEGIN FILES -->
    In file `foo.aya`
    ```aya
    def id {A : Type} (a : A) : A => a
    ```
    <!-- END FILES -->
    
    ## ...
    """;

  @Test
  public void test() {
    var source = new SourceFile("test.md", Option.none(), SOURCE);
    var result = BlockParser.parse(source, new ThrowingReporter(AyaPrettierOptions.debug()), IssueParser.BlockType::valueOf);

    assertEquals(2, result.size());

    var block0 = result.get(0);
    var block1 = result.get(1);

    assertEquals(IssueParser.BlockType.VERSION, block0.blockType());
    assertEquals(IssueParser.BlockType.FILES, block1.blockType());

    assertEquals("`0.39-SNAPSHOT`", block0.content().toString());
    assertEquals("""  
      \nIn file `foo.aya`
      ```aya
      def id {A : Type} (a : A) : A => a
      ```
      """, block1.content().toString());
  }
}
