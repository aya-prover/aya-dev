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

import java.nio.file.Path;
import java.util.Objects;

public class IssueTrackerTest {
  @Test
  public void testParse() throws Exception {
    var source = SourceFile.from("Template.md", TestRunner.TEST_DIR.resolve("issue-tracker/Template.md"));
    var result = Objects.requireNonNull(
      new IssueParser(source, new ThrowingReporter(AyaPrettierOptions.debug())).parse()
    );

    var files = result.files();

    IssueSetup.setup(files, Path.of("build/issue-tracker"));
  }
}
