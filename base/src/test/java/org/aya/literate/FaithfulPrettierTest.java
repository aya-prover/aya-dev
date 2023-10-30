// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.literate;

import kala.collection.immutable.ImmutableSeq;
import kala.control.Option;
import org.aya.cli.literate.FlclFaithfulPrettier;
import org.aya.cli.literate.LiterateFaithfulPrettier;
import org.aya.cli.literate.SyntaxHighlight;
import org.aya.cli.parse.AyaParserImpl;
import org.aya.cli.parse.FlclParser;
import org.aya.prettier.AyaPrettierOptions;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Language;
import org.aya.resolve.context.EmptyContext;
import org.aya.test.AyaThrowingReporter;
import org.aya.tyck.TyckDeclTest;
import org.aya.util.FileUtil;
import org.aya.util.error.SourceFile;
import org.aya.util.error.SourcePos;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

public class FaithfulPrettierTest {
  @Test public void literate() throws IOException {
    var reporter = AyaThrowingReporter.INSTANCE;

    var root = AyaMdParserTest.TEST_DIR;
    var modName = "Main";
    var fileName = modName + ".aya";
    var outputFileName = modName + ".html";

    var sourceFile = new SourceFile(fileName, root, Files.readString(root.resolve(fileName)));
    var parser = new AyaParserImpl(reporter);
    var stmts = parser.program(sourceFile);
    TyckDeclTest.resolve(stmts, new EmptyContext(reporter, root).derive(modName));

    var highlights = SyntaxHighlight.highlight(null, Option.some(sourceFile), stmts);
    var mockPos = new SourcePos(sourceFile, 0, sourceFile.sourceCode().length() - 1, // <- tokenEndIndex is inclusive
      -1, -1, -1, -1);
    var doc = new LiterateFaithfulPrettier(ImmutableSeq.empty(), highlights, AyaPrettierOptions.pretty())
      .highlight(sourceFile.sourceCode(), mockPos);
    var output = Doc.codeBlock(Language.Builtin.Aya, doc).renderToHtml(true);
    FileUtil.writeString(root.resolve(outputFileName), output);
  }

  @Test public void fakeLiterate() throws IOException {
    var root = AyaMdParserTest.TEST_DIR.getParent().resolve("flcl");
    var fileName = "test.flcl";
    var file = new SourceFile("FLCL", root, Files.readString(root.resolve(fileName)));
    var parser = new FlclParser(AyaThrowingReporter.INSTANCE, file);
    var prettier = new FlclFaithfulPrettier(AyaPrettierOptions.pretty());
    var doc = prettier.highlight(parser.computeAst());
    FileUtil.writeString(root.resolve("test.latex"), doc.renderToTeX());
  }
}
