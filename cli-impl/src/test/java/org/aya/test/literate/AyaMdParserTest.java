// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.test.literate;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import org.aya.cli.render.RenderOptions;
import org.aya.cli.single.CompilerFlags;
import org.aya.cli.single.SingleAyaFile;
import org.aya.cli.single.SingleFileCompiler;
import org.aya.cli.utils.LiterateData;
import org.aya.generic.Constants;
import org.aya.prettier.AyaPrettierOptions;
import org.aya.pretty.doc.Doc;
import org.aya.primitive.PrimFactory;
import org.aya.producer.AyaParserImpl;
import org.aya.resolve.ResolveInfo;
import org.aya.resolve.context.Context;
import org.aya.resolve.context.EmptyContext;
import org.aya.resolve.module.DumbModuleLoader;
import org.aya.syntax.concrete.stmt.Stmt;
import org.aya.test.TestRunner;
import org.aya.util.FileUtil;
import org.aya.util.Global;
import org.aya.util.Panic;
import org.aya.util.TimeUtil;
import org.aya.util.position.SourceFile;
import org.aya.util.reporter.BufferReporter;
import org.aya.util.reporter.IgnoringReporter;
import org.aya.util.reporter.ThrowingReporter;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
      System.err.println("Test Data " + expPath + " doesn't exist, skip.");
    } else {
      var expAyaFile = file(expPath);

      // Have to trim because trailing newlines are usually deleted by vcs
      assertLinesMatch(expAyaFile.sourceCode().trim().lines(), actualCode.trim().lines());
    }
  }

  private static @NotNull LiterateTestCase initLiterateTestCase(Case oneCase) throws IOException {
    var mdFile = new SingleAyaFile.CodeAyaFile(file(oneCase.mdFile()));

    var reporter = new BufferReporter();
    var literate = SingleAyaFile.createLiterateFile(mdFile, reporter);
    var stmts = literate.parseMe(new AyaParserImpl(reporter));
    var ctx = new EmptyContext(reporter, Path.of(".")).derive(oneCase.modName());
    var loader = new DumbModuleLoader(ctx);

    ResolveInfo info;
    try {
      info = loader.resolveModule(new PrimFactory(), ctx, stmts, loader);
    } catch (Context.ResolvingInterruptedException e) {
      info = Panic.unreachable();
    }

    loader.tyckModule(info, null);
    literate.tyckAdditional(info);
    return new LiterateTestCase(reporter, literate, stmts);
  }
  private record LiterateTestCase(
    BufferReporter reporter, SingleAyaFile.MarkdownAyaFile literate,
    ImmutableSeq<Stmt> stmts
  ) { }

  @ParameterizedTest
  @ValueSource(strings = {"hoshino-said", "wow", "test", "heading", "compiler-output"})
  public void testHighlight(String caseName) throws IOException {
    var oneCase = new Case(caseName);
    var data = initLiterateTestCase(oneCase);

    var defaultFM = new LiterateData.InjectedFrontMatter(null, TimeUtil.gitFormat());
    var doc = data.literate().toDoc(data.stmts(), data.reporter().problems().toSeq(),
      defaultFM, AyaPrettierOptions.pretty()).toDoc();
    data.reporter().problems().clear();
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

  @Test public void testTime() throws IOException {
    var doc = lastUpdatedTest("heading");
    assertTrue(doc.renderToMd().startsWith("---\nlastUpdated: "));
    doc = lastUpdatedTest("frontmatter");
    assertTrue(doc.renderToMd().startsWith("---\ntitle: Twitter\nlastUpdated: "));
  }

  private static @NotNull Doc lastUpdatedTest(String caseName) throws IOException {
    var oneCase = new Case(caseName);
    var data = initLiterateTestCase(oneCase);
    var defaultFM = new LiterateData.InjectedFrontMatter("lastUpdated", TimeUtil.gitFormat());
    return data.literate().toDoc(data.stmts(), data.reporter().problems().toSeq(),
      defaultFM, AyaPrettierOptions.pretty()).toDoc();
  }

  private @NotNull String trim(@NotNull String input) {
    return input.replaceAll("id=\"[^\"]+\"", "id=\"\"")
      .replaceAll("href=\"[^\"]+\"", "href=\"\"")
      .replaceAll("data-tooltip-text=\"[^\"]+\"", "data-tooltip-text=\"\"");
  }
}
