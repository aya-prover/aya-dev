// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.utils;

import kala.collection.Seq;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.control.Option;
import org.aya.cli.literate.AyaMdParser;
import org.aya.cli.literate.LiterateFaithfulPrettier;
import org.aya.cli.literate.SyntaxHighlight;
import org.aya.literate.Literate;
import org.aya.literate.LiterateConsumer;
import org.aya.normalize.Normalizer;
import org.aya.prettier.BasePrettier;
import org.aya.pretty.doc.Doc;
import org.aya.resolve.ResolveInfo;
import org.aya.resolve.salt.Desalt;
import org.aya.resolve.visitor.ExprResolver;
import org.aya.syntax.GenericAyaFile;
import org.aya.syntax.GenericAyaParser;
import org.aya.syntax.concrete.Expr;
import org.aya.syntax.concrete.stmt.Stmt;
import org.aya.syntax.core.Jdg;
import org.aya.syntax.core.def.AnyDef;
import org.aya.syntax.core.term.ErrorTerm;
import org.aya.syntax.literate.AyaLiterate;
import org.aya.syntax.literate.CodeOptions;
import org.aya.syntax.ref.AnyDefVar;
import org.aya.syntax.ref.ModulePath;
import org.aya.tyck.tycker.TeleTycker;
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
      var data = ExprResolver.resolveLax(info.thisModule(), c.expr)
        .descent(new Desalt(info));
      c.params = data.params();
      c.expr = data.expr();
    });
  }

  /**
   * Directly calling a def is usually an error due to the possible existence of
   * implicit parameters. However, when writing these functions in the REPL or a
   * literate document, we are not usually asking for normalizing the function,
   * instead we might just want to take a look at it, or check if it exists, or
   * generate a link to it. So it's a good idea to not show an error message,
   * and just pretty print the def instead.
   */
  public static @Nullable Jdg simpleVar(Expr expr) {
    if (expr instanceof Expr.Ref ref && ref.var() instanceof AnyDefVar defVar) {
      var anyDef = AnyDef.fromVar(defVar);
      return new Jdg.Default(
        new ErrorTerm(_ -> BasePrettier.refVar(anyDef), false),
        anyDef.signature().makePi(Seq.empty()));
    }
    return null;
  }

  public void tyck(@NotNull ResolveInfo info) {
    extractedExprs.forEach(c -> {
      assert c.expr != null;
      assert c.params != null;
      if (c.options.mode() == CodeOptions.NormalizeMode.NULL) {
        var jdg = simpleVar(c.expr.data());
        if (jdg != null) {
          c.tyckResult = jdg;
          return;
        }
      }
      var tycker = info.newTycker();
      var teleTycker = new TeleTycker.InlineCode(tycker);
      var result = teleTycker.checkInlineCode(c.params, c.expr);
      var normalizer = new Normalizer(tycker.state);
      c.tyckResult = result.map(t -> normalizer.normalize(t, c.options.mode()));
    });
  }

  public record InjectedFrontMatter(
    @Nullable String datetimeKey,
    @NotNull String datetimeValue
  ) { }
  public static @NotNull Doc toDoc(
    @NotNull GenericAyaFile ayaFile,
    @Nullable ModulePath currentFileModule,
    @NotNull ImmutableSeq<Stmt> program,
    @NotNull ImmutableSeq<Problem> problems,
    @NotNull InjectedFrontMatter injected,
    @NotNull PrettierOptions options
  ) throws IOException {
    var highlights = SyntaxHighlight.highlight(currentFileModule, Option.some(ayaFile.codeFile()), program);
    var literate = ayaFile.literate();
    if (injected.datetimeKey != null) {
      var frontMatter = literate.findFrontMatter();
      var label = new Literate.Raw(Doc.plain(injected.datetimeKey + ": " + injected.datetimeValue));
      if (frontMatter != null) {
        frontMatter.children().append(label);
      } else {
        var delimiter = new Literate.Raw(Doc.plain("---"));
        frontMatter = new Literate.FrontMatter(MutableList.of(
          delimiter, Literate.EOL, label, Literate.EOL, delimiter));
        literate = new Literate.Many(null, ImmutableSeq.of(frontMatter, literate));
      }
    }
    var prettier = new LiterateFaithfulPrettier(problems, highlights, options);
    prettier.accept(literate);
    return literate.toDoc();
  }
}
