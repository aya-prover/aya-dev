// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.test.cli;

import kala.collection.immutable.ImmutableSeq;
import org.aya.cli.issue.IssueParser;
import org.aya.cli.issue.IssueRunner;
import org.aya.literate.parser.BaseMdParser;
import org.aya.literate.parser.InterestingLanguage;
import org.aya.prettier.AyaPrettierOptions;
import org.aya.test.TestRunner;
import org.aya.util.position.SourceFile;
import org.aya.util.reporter.ThrowingReporter;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Objects;

public class IssueCheckerTest {
  @Test
  public void testParse() throws Exception {
    var source = SourceFile.from("Template.md", TestRunner.TEST_DIR.resolve("issue-checker/Template.md"));
    var parser = new BaseMdParser(source, new ThrowingReporter(AyaPrettierOptions.debug()), ImmutableSeq.of(InterestingLanguage.ALL));
    var literate = parser.parseLiterate();
    var result = Objects.requireNonNull(new IssueParser().accept(literate));
    var files = result.component1();

    IssueRunner.setup(files, Path.of("build/issue-checker"));
  }
}
