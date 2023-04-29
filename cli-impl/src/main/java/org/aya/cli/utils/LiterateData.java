// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.utils;

import kala.collection.immutable.ImmutableSeq;
import kala.control.Option;
import org.aya.cli.literate.AyaMdParser;
import org.aya.cli.literate.FaithfulPrettier;
import org.aya.cli.literate.SyntaxHighlight;
import org.aya.concrete.GenericAyaFile;
import org.aya.concrete.GenericAyaParser;
import org.aya.concrete.desugar.Desugarer;
import org.aya.concrete.remark.AyaLiterate;
import org.aya.concrete.stmt.Stmt;
import org.aya.literate.Literate;
import org.aya.literate.LiterateConsumer;
import org.aya.pretty.doc.Doc;
import org.aya.resolve.ResolveInfo;
import org.aya.util.error.SourceFile;
import org.aya.util.prettier.PrettierOptions;
import org.aya.util.reporter.Problem;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public record LiterateData(
  @NotNull Literate literate,
  @NotNull ImmutableSeq<AyaLiterate.AyaInlineCode> extractedExprs,
  @NotNull SourceFile extractedAya
) {
  public static @NotNull LiterateData create(@NotNull SourceFile mdFile, @NotNull Reporter reporter) {
    var mdParser = new AyaMdParser(mdFile, reporter);
    var lit = mdParser.parseLiterate();
    var ayaCode = mdParser.extractAya(lit);
    var exprs = new LiterateConsumer.InstanceExtractinator<>(AyaLiterate.AyaInlineCode.class).extract(lit);
    var code = new SourceFile(mdFile.display(), mdFile.underlying(), ayaCode);
    return new LiterateData(lit, exprs, code);
  }

  public void parseMe(@NotNull GenericAyaParser parser) {
    extractedExprs.forEach(code -> code.expr = parser.expr(code.code, code.sourcePos));
  }

  public void resolve(@NotNull ResolveInfo info) {
    extractedExprs.forEach(c -> {
      assert c.expr != null;
      c.expr = new Desugarer(info).apply(c.expr.resolveLax(info.thisModule()));
    });
  }

  public void tyck(@NotNull ResolveInfo info) {
    var tycker = info.newTycker(info.thisModule().reporter(), null);
    extractedExprs.forEach(c -> {
      assert c.expr != null;
      c.tyckResult = tycker.zonk(tycker.synthesize(c.expr)).normalize(c.options.mode(), tycker.state);
    });
  }

  public static @NotNull Doc toDoc(
    @NotNull GenericAyaFile ayaFile,
    @Nullable ImmutableSeq<String> currentFileModule,
    @NotNull ImmutableSeq<Stmt> program,
    @NotNull ImmutableSeq<Problem> problems,
    @NotNull PrettierOptions options
  ) throws IOException {
    var highlights = SyntaxHighlight.highlight(currentFileModule, Option.some(ayaFile.codeFile()), program);
    var literate = ayaFile.literate();
    var prettier = new FaithfulPrettier(problems, highlights, options);
    prettier.accept(literate);
    return literate.toDoc();
  }
}
