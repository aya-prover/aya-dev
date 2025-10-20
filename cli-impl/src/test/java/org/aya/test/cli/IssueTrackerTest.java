// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.test.cli;

import org.aya.cli.issue.IssueParser;
import org.aya.cli.issue.IssueSetup;
import org.aya.prettier.AyaPrettierOptions;
import org.aya.test.TestRunner;
import org.aya.util.position.SourceFile;
import org.aya.util.reporter.ThrowingReporter;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class IssueTrackerTest {
  @Test
  public void testParse() throws Exception {
    var source = SourceFile.from("Template.md", TestRunner.TEST_DIR.resolve("issue-tracker/Template.md"));
    var wd = Path.of("build/issue-tracker");
    IssueSetup.run(source, wd, new ThrowingReporter(AyaPrettierOptions.debug()));

    var metadata = Files.readString(wd.resolve(IssueSetup.METADATA_FILE));
    assertEquals("""
      {
        "version": {
          "major": 0,
          "minor": 39,
          "patch": 0,
          "snapshot": false,
          "hash": null,
          "java": -1
        },
        "files": [
          "foo.aya"
        ]
      }""", metadata);

    var fooAya = Files.readString(wd.resolve("src").resolve("foo.aya"));
    assertEquals("""
      inductive Nat | zro | suc Nat
      
      def what => 0""", fooAya);
  }

  @Test
  public void testDevil() {
    var base = Path.of("src");

    var file = new IssueParser.File("../../../../../../etc/shadow", "");
    assertNull(file.getValidFileName(base));

    file = new IssueParser.File("/root", "");
    assertNull(file.getValidFileName(base));

    file = new IssueParser.File("arith/nat/base.aya", "");
    assertEquals(Path.of("src/arith/nat/base.aya"), file.getValidFileName(base));
  }
}
