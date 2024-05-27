// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.test.literate;

import org.aya.cli.literate.FlclFaithfulPrettier;
import org.aya.prettier.AyaPrettierOptions;
import org.aya.producer.flcl.FlclParser;
import org.aya.util.FileUtil;
import org.aya.util.error.SourceFile;
import org.aya.util.reporter.ThrowingReporter;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

public class FlclPrettierTest {
  public static final ThrowingReporter REPORTER = new ThrowingReporter(AyaPrettierOptions.pretty());
  @Test public void fakeLiterate() throws IOException {
    var root = AyaMdParserTest.TEST_DIR.getParent().resolve("flcl");
    var fileName = "test.flcl";
    var file = new SourceFile("FLCL", root, Files.readString(root.resolve(fileName)));
    var parser = new FlclParser(REPORTER, file);
    var prettier = new FlclFaithfulPrettier(AyaPrettierOptions.pretty());
    var doc = prettier.highlight(parser.computeAst());
    FileUtil.writeString(root.resolve("test.tex"), doc.renderToTeX());
  }
}
