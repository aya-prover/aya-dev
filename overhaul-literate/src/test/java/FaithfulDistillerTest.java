import kala.collection.Set;
import kala.control.Option;
import org.aya.cli.literate.SyntaxHighlight;
import org.aya.cli.parse.AyaParserImpl;
import org.aya.concrete.desugar.AyaBinOpSet;
import org.aya.concrete.remark2.FaithfulDistiller;
import org.aya.concrete.stmt.Stmt;
import org.aya.core.def.PrimDef;
import org.aya.resolve.ResolveInfo;
import org.aya.resolve.context.EmptyContext;
import org.aya.resolve.module.EmptyModuleLoader;
import org.aya.util.error.SourceFile;
import org.aya.util.reporter.ThrowingReporter;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
public class FaithfulDistillerTest {
  @Test
  public void test() throws IOException {
    var reporter = ThrowingReporter.INSTANCE;

    var root = Path.of("src", "test", "resources");
    var modName = "Main";
    var fileName = modName + ".aya";
    var outputFileName = modName + ".html";

    var sourceFile = new SourceFile(fileName, root, Files.readString(root.resolve(fileName)));
    var parser = new AyaParserImpl(reporter);
    var stmts = parser.program(sourceFile);
    var resolveInfo = new ResolveInfo(
      new PrimDef.Factory(),
      new EmptyContext(reporter, root).derive(modName),
      stmts);

    Stmt.resolveWithoutDesugar(stmts.view(), resolveInfo, EmptyModuleLoader.INSTANCE);

    var highlights = SyntaxHighlight.highlight(Option.some(sourceFile), stmts);
    var doc = FaithfulDistiller.highlight(sourceFile, Set.from(highlights).toImmutableSeq().view().sorted());
    var output = doc.renderToHtml(true);

    Files.writeString(root.resolve(outputFileName), output);
  }
}
