// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.utils;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import org.aya.cli.literate.AyaMdParser;
import org.aya.concrete.GenericAyaParser;
import org.aya.concrete.desugar.Desugarer;
import org.aya.concrete.remark.Literate;
import org.aya.concrete.remark.LiterateConsumer;
import org.aya.resolve.ResolveInfo;
import org.aya.util.error.SourceFile;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;

public record LiterateData(
  @NotNull Literate literate,
  @NotNull ImmutableSeq<Literate.Code> extractedExprs,
  @NotNull SourceFile extractedAya
) {
  public static @NotNull LiterateData create(@NotNull SourceFile mdFile, @NotNull Reporter reporter) {
    var mdParser = new AyaMdParser(mdFile, reporter);
    var lit = mdParser.parseLiterate();
    var ayaCode = mdParser.extractAya(lit);
    var exprs = new LiterateConsumer.Codes(MutableList.create()).extract(lit);
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
}
