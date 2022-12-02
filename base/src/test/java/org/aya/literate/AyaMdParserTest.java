// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.literate;

import kala.collection.Seq;
import kala.collection.SeqView;
import kala.control.Option;
import org.aya.cli.literate.AyaMdParser;
import org.aya.cli.literate.LiterateConsumer;
import org.aya.cli.literate.SyntaxHighlight;
import org.aya.cli.parse.AyaParserImpl;
import org.aya.cli.single.CompilerFlags;
import org.aya.cli.single.SingleFileCompiler;
import org.aya.concrete.stmt.Stmt;
import org.aya.core.def.PrimDef;
import org.aya.generic.Constants;
import org.aya.resolve.ResolveInfo;
import org.aya.resolve.context.EmptyContext;
import org.aya.resolve.module.EmptyModuleLoader;
import org.aya.util.error.SourceFile;
import org.aya.util.reporter.ThrowingReporter;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertLinesMatch;

public class AyaMdParserTest {
  public final static @NotNull Path TEST_DIR = Path.of("src", "test", "resources", "literate");

  record Case(@NotNull String modName) {
    public final static @NotNull String PREFIX_EXPECTED = "expected-";
    public final static @NotNull String EXTENSION_AYA_MD = Constants.AYA_LITERATE_POSTFIX;
    public final static @NotNull String EXTENSION_AYA = Constants.AYA_POSTFIX;
    public final static @NotNull String EXTENSION_HTML = EXTENSION_AYA_MD + ".html";

    public @NotNull String mdName() {
      return modName + EXTENSION_AYA_MD;
    }

    public @NotNull String ayaName() {
      return modName + EXTENSION_AYA;
    }

    public @NotNull String expectedAyaName() {
      return PREFIX_EXPECTED + ayaName();
    }

    public @NotNull String htmlName() {
      return modName + EXTENSION_HTML;
    }

    public @NotNull Path mdFile() {
      return TEST_DIR.resolve(mdName());
    }

    public @NotNull Path ayaFile() {
      return TEST_DIR.resolve(ayaName());
    }

    public @NotNull Path expectedAyaFile() {
      return TEST_DIR.resolve(expectedAyaName());
    }

    public @NotNull Path htmlFile() {
      return TEST_DIR.resolve(htmlName());
    }

    public @NotNull Path outMdFile() {
      return TEST_DIR.resolve(htmlName() + ".out.md");
    }
  }

  public static @NotNull SourceFile file(@NotNull Path path) throws IOException {
    return new SourceFile(path.toFile().getName(), path, Files.readString(path));
  }

  @Test
  public void testExtract() throws IOException {
    var cases = Seq.of(
      new Case("test"),
      new Case("wow")
    );

    for (var oneCase : cases) {
      var mdFile = file(oneCase.mdFile());
      var parser = new AyaMdParser(mdFile);
      var literate = parser.parseLiterate(new AyaParserImpl(ThrowingReporter.INSTANCE));
      var actualCode = AyaMdParser.extractAya(literate);

      Files.writeString(oneCase.ayaFile(), actualCode);

      var expPath = oneCase.expectedAyaFile();

      if (!expPath.toFile().exists()) {
        System.err.println("Test Data " + expPath + " doesn't exist, skip.");
      } else {
        var expAyaFile = file(expPath);

        assertLinesMatch(expAyaFile.sourceCode().lines(), actualCode.lines());
      }
    }
  }

  @Test
  public void testHighlight() throws IOException {
    var cases = Seq.of(
      new Case("test"),
      new Case("wow"),
      new Case("heading")
    );

    for (var oneCase : cases) {
      var mdFile = file(oneCase.mdFile());

      var ayaParser = new AyaParserImpl(ThrowingReporter.INSTANCE);
      var mdParser = new AyaMdParser(mdFile);
      var literate = mdParser.parseLiterate(ayaParser);
      var ayaCode = AyaMdParser.extractAya(literate);

      Files.writeString(oneCase.ayaFile(), ayaCode);

      // parse aya code
      var ayaFile = file(oneCase.ayaFile());
      var stmts = ayaParser.program(ayaFile, mdFile);
      Stmt.resolve(stmts, new ResolveInfo(
        new PrimDef.Factory(),
        new EmptyContext(ThrowingReporter.INSTANCE, Path.of(".")).derive(oneCase.modName()),
        stmts
      ), EmptyModuleLoader.INSTANCE);

      var highlights = SyntaxHighlight.highlight(Option.some(ayaFile), stmts);
      new LiterateConsumer.Highlights(highlights).accept(literate);
      var doc = literate.toDoc();
      var expectedHtml = doc.renderToHtml();
      Files.writeString(oneCase.htmlFile(), expectedHtml);

      // test single file compiler
      var compiler = new SingleFileCompiler(ThrowingReporter.INSTANCE, null, null);
      compiler.compile(oneCase.mdFile(), new CompilerFlags(
        CompilerFlags.Message.ASCII, false, false, null, SeqView.empty(),
        oneCase.htmlFile()
      ), null);
      var actualHtml = Files.readString(oneCase.htmlFile());
      assertEquals(trimIdHref(expectedHtml), trimIdHref(actualHtml));
      Files.writeString(oneCase.outMdFile(), doc.renderToMd());
    }
  }

  private @NotNull String trimIdHref(@NotNull String input) {
    return input.replaceAll("id=\"[^\"]+\"", "id=\"\"")
      .replaceAll("href=\"[^\"]+\"", "href=\"\"");
  }
}
