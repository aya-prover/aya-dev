// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.single;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.control.Option;
import org.aya.cli.literate.AyaMdParser;
import org.aya.cli.literate.LiterateConsumer;
import org.aya.cli.literate.SyntaxHighlight;
import org.aya.cli.render.RenderOptions;
import org.aya.cli.utils.MainArgs;
import org.aya.cli.utils.MainArgs.DistillFormat;
import org.aya.concrete.GenericAyaFile;
import org.aya.concrete.GenericAyaParser;
import org.aya.concrete.desugar.Desugarer;
import org.aya.concrete.remark.Literate;
import org.aya.concrete.stmt.Decl;
import org.aya.concrete.stmt.Stmt;
import org.aya.core.def.Def;
import org.aya.core.def.PrimDef;
import org.aya.generic.AyaDocile;
import org.aya.generic.Constants;
import org.aya.generic.util.AyaFiles;
import org.aya.pretty.doc.Doc;
import org.aya.resolve.ResolveInfo;
import org.aya.util.FileUtil;
import org.aya.util.distill.DistillerOptions;
import org.aya.util.error.SourceFile;
import org.aya.util.error.SourceFileLocator;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.MustBeInvokedByOverriders;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.BiFunction;

public sealed interface SingleAyaFile extends GenericAyaFile {
  private static @Nullable CompilerFlags.DistillInfo parseDistillInfo(@NotNull CompilerFlags flags) {
    if (flags.distillInfo() != null) return flags.distillInfo();
    if (flags.outputFile() != null) return new CompilerFlags.DistillInfo(
      false,
      MainArgs.DistillStage.literate,
      detectFormat(flags.outputFile()),
      DistillerOptions.pretty(),
      new RenderOptions(),
      null);
    return null;
  }
  private static @NotNull DistillFormat detectFormat(@NotNull Path outputFile) {
    var name = outputFile.getFileName().toString();
    if (name.endsWith(".md")) return DistillFormat.markdown;
    if (name.endsWith(".tex")) return DistillFormat.latex;
    if (name.endsWith(".html")) return DistillFormat.html;
    return DistillFormat.plain;
  }

  @SuppressWarnings("unchecked") default void distill(
    @NotNull CompilerFlags compilerFlags,
    @NotNull ImmutableSeq<? extends AyaDocile> doc,
    @NotNull MainArgs.DistillStage currentStage
  ) throws IOException {
    var flags = parseDistillInfo(compilerFlags);
    if (flags == null || currentStage != flags.distillStage()) return;

    var out = flags.distillFormat().target;
    String fileName;
    Path distillDir;

    if (compilerFlags.outputFile() != null) {
      distillDir = compilerFlags.outputFile().toAbsolutePath().getParent();
      fileName = compilerFlags.outputFile().getFileName().toString();
    } else {
      distillDir = flags.distillDir() != null ? Path.of(flags.distillDir()) : Path.of(".");
      fileName = AyaFiles.stripAyaSourcePostfix(originalFile().display()) + out.fileExt;
    }

    var renderOptions = flags.renderOptions();
    if (currentStage == MainArgs.DistillStage.literate) {
      var text = renderOptions.render(out, docitfy((ImmutableSeq<Stmt>) doc), true, !flags.ascii());
      FileUtil.writeString(distillDir.resolve(fileName), text);
    } else {
      doWrite(doc, distillDir, flags.distillerOptions(), fileName, out.fileExt,
        (d, hdr) -> renderOptions.render(out, d, hdr, !flags.ascii()));
    }
  }
  @VisibleForTesting default @NotNull Doc docitfy(ImmutableSeq<Stmt> program) throws IOException {
    var highlights = SyntaxHighlight.highlight(Option.some(codeFile()), program);
    var literate = literate();
    new LiterateConsumer.Highlights(highlights).accept(literate);
    return literate.toDoc();
  }

  private void doWrite(
    ImmutableSeq<? extends AyaDocile> doc, Path distillDir,
    @NotNull DistillerOptions options, String fileName, String fileExt,
    BiFunction<Doc, Boolean, String> toString
  ) throws IOException {
    var docs = MutableList.<Doc>create();
    var eachDistillDir = distillDir.resolve(fileName + ".each");
    for (int i = 0; i < doc.size(); i++) {
      var item = doc.get(i);
      // Skip uninteresting items
      var thisDoc = item.toDoc(options);
      docs.append(thisDoc);
      if (item instanceof PrimDef) continue;
      FileUtil.writeString(eachDistillDir.resolve(FileUtil.escapeFileName(nameOf(i, item)) + fileExt), toString.apply(thisDoc, false));
    }
    FileUtil.writeString(distillDir.resolve(fileName), toString.apply(Doc.vcat(docs), true));
  }

  private static @NotNull String nameOf(int i, AyaDocile item) {
    return item instanceof Def def ? def.ref().name()
      : item instanceof Decl decl ? decl.ref().name() : String.valueOf(i);
  }

  /** Must be called after {@link #parseMe} */
  default void resolveAdditional(@NotNull ResolveInfo info) {
  }

  @MustBeInvokedByOverriders
  default void tyckAdditional(@NotNull ResolveInfo info) {
    resolveAdditional(info);
  }

  record Factory(@NotNull Reporter reporter) implements GenericAyaFile.Factory {
    @Override public @NotNull SingleAyaFile
    createAyaFile(@NotNull SourceFileLocator locator, @NotNull Path path) throws IOException {
      var fileName = path.getFileName().toString();
      var codeFile = new CodeAyaFile(SourceFile.from(locator, path));
      return fileName.endsWith(Constants.AYA_LITERATE_POSTFIX)
        ? createLiterateFile(codeFile, reporter) : codeFile;
    }
  }

  record CodeAyaFile(@NotNull SourceFile originalFile) implements SingleAyaFile {
  }

  private static @NotNull MarkdownAyaFile.Data
  createData(@NotNull CodeAyaFile template, @NotNull Reporter reporter) {
    var mdFile = template.originalFile;
    var mdParser = new AyaMdParser(mdFile, reporter);
    var lit = mdParser.parseLiterate();
    var ayaCode = AyaMdParser.extractAya(lit);
    var exprs = new LiterateConsumer.Codes(MutableList.create()).extract(lit);
    var code = new SourceFile(mdFile.display(), mdFile.underlying(), ayaCode);
    return new MarkdownAyaFile.Data(lit, exprs, code);
  }

  @VisibleForTesting static @NotNull MarkdownAyaFile
  createLiterateFile(@NotNull CodeAyaFile template, @NotNull Reporter reporter) {
    return new MarkdownAyaFile(template.originalFile, createData(template, reporter));
  }

  record MarkdownAyaFile(@Override @NotNull SourceFile originalFile, @NotNull Data data) implements SingleAyaFile {
    record Data(
      @NotNull Literate literate,
      @NotNull ImmutableSeq<Literate.Code> extractedExprs,
      @NotNull SourceFile extractedAya
    ) {}

    @Override public void resolveAdditional(@NotNull ResolveInfo info) {
      SingleAyaFile.super.resolveAdditional(info);
      data.extractedExprs.forEach(c -> {
        assert c.expr != null;
        c.expr = new Desugarer(info).apply(c.expr.resolveLax(info.thisModule()));
      });
    }

    @Override public void tyckAdditional(@NotNull ResolveInfo info) {
      SingleAyaFile.super.tyckAdditional(info);
      var tycker = info.newTycker(info.thisModule().reporter(), null);
      data.extractedExprs.forEach(c -> {
        assert c.expr != null;
        c.tyckResult = tycker.zonk(tycker.synthesize(c.expr)).normalize(c.options.mode(), tycker.state);
      });
    }

    @Override public @NotNull ImmutableSeq<Stmt> parseMe(@NotNull GenericAyaParser parser) throws IOException {
      data.extractedExprs.forEach(code -> code.expr = parser.expr(code.code, code.sourcePos));
      return SingleAyaFile.super.parseMe(parser);
    }

    @Override public @NotNull SourceFile codeFile() {
      return data.extractedAya;
    }

    @Override public @NotNull Literate literate() throws IOException {
      return data.literate;
    }
  }
}
