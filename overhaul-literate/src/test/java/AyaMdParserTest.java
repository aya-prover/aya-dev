import kala.collection.Set;
import kala.control.Option;
import org.aya.cli.literate.SyntaxHighlight;
import org.aya.cli.parse.AyaParserImpl;
import org.aya.cli.single.SingleFileCompiler;
import org.aya.concrete.desugar.AyaBinOpSet;
import org.aya.concrete.remark2.AyaMdParser;
import org.aya.concrete.remark2.LiterateConsumer;
import org.aya.concrete.stmt.Stmt;
import org.aya.core.def.PrimDef;
import org.aya.resolve.ResolveInfo;
import org.aya.resolve.context.EmptyContext;
import org.aya.resolve.module.EmptyModuleLoader;
import org.aya.util.error.SourceFile;
import org.aya.util.reporter.ThrowingReporter;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
public class AyaMdParserTest {
  public final static @NotNull Path TEST_DIR = Path.of("src", "test", "resources");

  record Case(@NotNull String fileName, @NotNull String expectedFileName) {
    public final static @NotNull String EXTENSION_AYA_MD = ".aya.md";
    public final static @NotNull String EXTENSION_AYA = ".aya";

    public @NotNull String mdName() {
      return fileName + EXTENSION_AYA_MD;
    }

    public @NotNull String ayaName() {
      return fileName + EXTENSION_AYA;
    }

    public @NotNull Path mdFile() {
      return TEST_DIR.resolve(mdName());
    }

    public @NotNull Path ayaFile() {
      return TEST_DIR.resolve(ayaName());
    }
  }

  public static @NotNull SourceFile file(@NotNull String fileName) throws IOException {
    var path = TEST_DIR.resolve(fileName);
    var content = Files.readString(path);

    return new SourceFile(fileName, path, content);
  }

  @Test
  public void testExtract() throws IOException {
    var mdFile = file("test.aya.md");
    var ayaFile = file("test.aya");
    var parser = new AyaMdParser(mdFile);
    var literate = parser.parseLiterate(new AyaParserImpl(ThrowingReporter.INSTANCE));
    var actualCode = AyaMdParser.extractAya(literate);
    ;

    assertEquals(ayaFile.sourceCode(), actualCode);
  }

  @Test
  public void testHighlight() throws IOException {
    var mdFile = file("test.aya.md");

    var ayaParser = new AyaParserImpl(ThrowingReporter.INSTANCE);
    var mdParser = new AyaMdParser(mdFile);
    var literate = mdParser.parseLiterate(ayaParser);
    var ayaCode = AyaMdParser.extractAya(literate);

    // parse aya code
    var fakeFile = new SourceFile("<null>.aya", Option.none(), ayaCode);
    var stmts = ayaParser.program(fakeFile);
    Stmt.resolveWithoutDesugar(stmts, new ResolveInfo(
      new PrimDef.Factory(),
      new EmptyContext(ThrowingReporter.INSTANCE, Path.of(".")).derive("<null>"),
      stmts
    ), EmptyModuleLoader.INSTANCE);

    var highlights = Set.from(SyntaxHighlight.highlight(Option.some(fakeFile), stmts)).toImmutableSeq()
      .sorted();
    new LiterateConsumer.Highlight(highlights).accept(literate);

    Files.writeString(TEST_DIR.resolve("null.aya.md.html"), literate.toDoc().renderToHtml());
  }
}
