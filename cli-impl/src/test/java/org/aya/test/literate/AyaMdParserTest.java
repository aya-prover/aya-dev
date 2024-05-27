// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.test.literate;

import kala.collection.SeqView;
import org.aya.cli.render.RenderOptions;
import org.aya.cli.single.CompilerFlags;
import org.aya.cli.single.SingleAyaFile;
import org.aya.cli.single.SingleFileCompiler;
import org.aya.generic.Constants;
import org.aya.primitive.PrimFactory;
import org.aya.prettier.AyaPrettierOptions;
import org.aya.producer.AyaParserImpl;
import org.aya.resolve.context.EmptyContext;
import org.aya.resolve.module.DumbModuleLoader;
import org.aya.test.TestRunner;
import org.aya.util.FileUtil;
import org.aya.util.error.Global;
import org.aya.util.error.SourceFile;
import org.aya.util.reporter.BufferReporter;
import org.aya.util.reporter.IgnoringReporter;
import org.aya.util.reporter.ThrowingReporter;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class AyaMdParserTest {
  public final static @NotNull Path TEST_DIR = Path.of("src", "test", "resources", "literate");

  @BeforeEach public void setUp() throws IOException {
    Files.createDirectories(TEST_DIR);
    Global.NO_RANDOM_NAME = true;
  }

  record Case(@NotNull String modName) {
    public final static @NotNull String PREFIX_EXPECTED = "expected-";
    public final static @NotNull String EXTENSION_AYA_MD = Constants.AYA_LITERATE_POSTFIX;
    public final static @NotNull String EXTENSION_AYA = Constants.AYA_POSTFIX;
    public final static @NotNull String EXTENSION_HTML = ".html";
    public final static @NotNull String EXTENSION_TEX = ".tex";
    public final static @NotNull String EXTENSION_PLAIN_TEXT = ".txt";
    public final static @NotNull String EXTENSION_OUT_MD = ".out.md";
    public @NotNull String ayaName() { return modName + EXTENSION_AYA; }
    public @NotNull Path mdFile() { return TEST_DIR.resolve(modName + EXTENSION_AYA_MD); }
    public @NotNull Path ayaFile() { return TEST_DIR.resolve(ayaName()); }
    public @NotNull Path expectedAyaFile() { return TEST_DIR.resolve(PREFIX_EXPECTED + ayaName()); }
    public @NotNull Path htmlFile() { return TEST_DIR.resolve(modName + EXTENSION_HTML); }
    public @NotNull Path outMdFile() { return TEST_DIR.resolve(modName + EXTENSION_OUT_MD); }
    public @NotNull Path texFile() { return TEST_DIR.resolve(modName + EXTENSION_TEX); }
    public @NotNull Path plainTextFile() { return TEST_DIR.resolve(modName + EXTENSION_PLAIN_TEXT); }
  }

  public static @NotNull SourceFile file(@NotNull Path path) throws IOException {
    return SourceFile.from(TestRunner.LOCATOR, path);
  }

  @ParameterizedTest
  @ValueSource(strings = {"test", "wow"})
  public void testExtract(String caseName) throws IOException {
    var oneCase = new Case(caseName);
    var mdFile = new SingleAyaFile.CodeAyaFile(file(oneCase.mdFile()));
    var literate = SingleAyaFile.createLiterateFile(mdFile, new ThrowingReporter(AyaPrettierOptions.pretty()));
    var actualCode = literate.codeFile().sourceCode();
    FileUtil.writeString(oneCase.ayaFile(), actualCode);

    var expPath = oneCase.expectedAyaFile();

    if (!expPath.toFile().exists()) {
      System.err.println(STR."Test Data \{expPath} doesn't exist, skip.");
    } else {
      var expAyaFile = file(expPath);

      assertLinesMatch(expAyaFile.sourceCode().lines(), actualCode.lines());
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"test", "wow", /*"hoshino-said",*/ "heading", "compiler-output"})
  public void testHighlight(String caseName) throws IOException {
    var oneCase = new Case(caseName);
    var mdFile = new SingleAyaFile.CodeAyaFile(file(oneCase.mdFile()));

    var reporter = new BufferReporter();
    var literate = SingleAyaFile.createLiterateFile(mdFile, reporter);
    var stmts = literate.parseMe(new AyaParserImpl(reporter));
    var ctx = new EmptyContext(reporter, Path.of(".")).derive(oneCase.modName());
    var loader = new DumbModuleLoader(ctx);
    var info = loader.resolveModule(new PrimFactory(), ctx, stmts, loader);
    loader.tyckModule(info, null);
    literate.tyckAdditional(info);

    var doc = literate.toDoc(stmts, reporter.problems().toImmutableSeq(), AyaPrettierOptions.pretty()).toDoc();
    reporter.problems().clear();
    // save some coverage
    var actualTexInlinedStyle = doc.renderToTeX();
    var expectedMd = doc.renderToAyaMd();

    FileUtil.writeString(oneCase.htmlFile(), doc.renderToHtml());
    FileUtil.writeString(oneCase.outMdFile(), expectedMd);
    FileUtil.writeString(oneCase.texFile(), actualTexInlinedStyle);
    FileUtil.writeString(oneCase.plainTextFile(), doc.debugRender());

    // test single file compiler
    var flags = new CompilerFlags(
      CompilerFlags.Message.ASCII, false, false, null, SeqView.empty(),
      oneCase.outMdFile()
    );
    var compiler = new SingleFileCompiler(IgnoringReporter.INSTANCE, flags, null);
    compiler.compile(oneCase.mdFile(), null);
    var actualMd = Files.readString(oneCase.outMdFile());
    assertEquals(trim(expectedMd), trim(actualMd));

    var actualTexWithHeader = new RenderOptions().render(RenderOptions.OutputTarget.LaTeX,
      doc, new RenderOptions.DefaultSetup(true, true, true, true, -1, false));
    var actualTexButKa = new RenderOptions().render(RenderOptions.OutputTarget.KaTeX,
      doc, new RenderOptions.DefaultSetup(true, true, true, true, -1, false));
    assertFalse(actualTexInlinedStyle.isEmpty());
    assertFalse(actualTexWithHeader.isEmpty());
    assertFalse(actualTexButKa.isEmpty());
  }

  private @NotNull String trim(@NotNull String input) {
    return input.replaceAll("id=\"[^\"]+\"", "id=\"\"")
      .replaceAll("href=\"[^\"]+\"", "href=\"\"")
      .replaceAll("data-tooltip-text=\"[^\"]+\"", "data-tooltip-text=\"\"");
  }
}
