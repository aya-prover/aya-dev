// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.single;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.control.Option;
import kala.value.MutableValue;
import org.aya.cli.literate.AyaMdParser;
import org.aya.cli.literate.LiterateConsumer;
import org.aya.cli.literate.SyntaxHighlight;
import org.aya.cli.utils.AyaCompiler;
import org.aya.cli.utils.MainArgs;
import org.aya.concrete.GenericAyaFile;
import org.aya.concrete.GenericAyaParser;
import org.aya.concrete.remark.Literate;
import org.aya.concrete.stmt.Decl;
import org.aya.concrete.stmt.Stmt;
import org.aya.core.def.Def;
import org.aya.core.def.GenericDef;
import org.aya.core.def.PrimDef;
import org.aya.core.serde.Serializer;
import org.aya.generic.AyaDocile;
import org.aya.generic.Constants;
import org.aya.pretty.doc.Doc;
import org.aya.resolve.ResolveInfo;
import org.aya.util.FileUtil;
import org.aya.util.distill.DistillerOptions;
import org.aya.util.error.SourceFile;
import org.aya.util.error.SourceFileLocator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.BiFunction;

public sealed interface SingleAyaFile extends GenericAyaFile {
  void distill(
    @NotNull String outputFileName,
    @Nullable CompilerFlags.DistillInfo flags,
    @NotNull ImmutableSeq<? extends AyaDocile> doc,
    @NotNull MainArgs.DistillStage currentStage
  ) throws IOException;

  void saveOutput(
    @NotNull Path outputFile,
    @NotNull CompilerFlags compilerFlags,
    @NotNull ResolveInfo resolveInfo,
    @NotNull ImmutableSeq<GenericDef> defs
  ) throws IOException;

  record Factory(@NotNull GenericAyaParser parser) implements GenericAyaFile.Factory {
    @Override public @NotNull SingleAyaFile createAyaFile(@NotNull SourceFileLocator locator, @NotNull Path path) {
      var fileName = path.getFileName().toString();
      return fileName.endsWith(Constants.AYA_LITERATE_POSTFIX)
        ? new MarkdownAyaFile(parser, locator, path, MutableValue.create(), MutableValue.create())
        : new CodeAyaFile(locator, path);
    }
  }

  record CodeAyaFile(@NotNull SourceFileLocator locator, @NotNull Path underlyingFile) implements SingleAyaFile {
    @Override public @NotNull SourceFile sourceFile() throws IOException {
      return SourceFile.from(locator, underlyingFile);
    }

    @Override public void saveOutput(
      @NotNull Path outputFile,
      @NotNull CompilerFlags compilerFlags,
      @NotNull ResolveInfo resolveInfo,
      @NotNull ImmutableSeq<GenericDef> defs
    ) throws IOException {
      AyaCompiler.saveCompiledCore(outputFile, resolveInfo, defs, new Serializer.State());
    }

    @Override public void distill(
      @NotNull String outputFileName,
      CompilerFlags.@Nullable DistillInfo flags,
      @NotNull ImmutableSeq<? extends AyaDocile> doc,
      MainArgs.@NotNull DistillStage currentStage
    ) throws IOException {
      if (flags == null || currentStage != flags.distillStage()) return;
      var distillDir = underlyingFile.resolveSibling(flags.distillDir());
      if (!Files.exists(distillDir)) Files.createDirectories(distillDir);
      var renderOptions = flags.renderOptions();
      var out = flags.distillFormat().target;
      doWrite(doc, distillDir, flags.distillerOptions(), outputFileName, out.fileExt,
        (d, hdr) -> renderOptions.render(out, d, hdr, !flags.ascii()));
    }

    private void doWrite(
      ImmutableSeq<? extends AyaDocile> doc, Path distillDir,
      @NotNull DistillerOptions options, String fileName, String fileExt,
      BiFunction<Doc, Boolean, String> toString
    ) throws IOException {
      var docs = MutableList.<Doc>create();
      for (int i = 0; i < doc.size(); i++) {
        var item = doc.get(i);
        // Skip uninteresting items
        var thisDoc = item.toDoc(options);
        docs.append(thisDoc);
        if (item instanceof PrimDef) continue;
        Files.writeString(distillDir.resolve(fileName + "-" + FileUtil.escapeFileName(nameOf(i, item)) + fileExt), toString.apply(thisDoc, false));
      }
      Files.writeString(distillDir.resolve(fileName + fileExt), toString.apply(Doc.vcat(docs), true));
    }

    @NotNull private String nameOf(int i, AyaDocile item) {
      return item instanceof Def def ? def.ref().name()
        : item instanceof Decl decl ? decl.ref().name() : String.valueOf(i);
    }
  }

  record MarkdownAyaFile(
    @NotNull GenericAyaParser parser,
    @NotNull SourceFileLocator locator, @NotNull Path underlyingFile,
    @NotNull MutableValue<SourceFile> markdownFile,
    @NotNull MutableValue<Data> data
  ) implements SingleAyaFile {
    record Data(
      @NotNull Literate literate,
      @NotNull SourceFile extractedAya
    ) {}

    private @NotNull SourceFile asMarkdownFile() throws IOException {
      var file = markdownFile.get();
      if (file == null) {
        file = SourceFile.from(locator, underlyingFile);
        markdownFile.set(file);
      }
      return file;
    }

    @Override public @NotNull SourceFile sourceFile() throws IOException {
      var aya = data.get();
      if (aya == null) {
        var mdParser = new AyaMdParser(asMarkdownFile());
        var lit = mdParser.parseLiterate(parser);
        var ayaCode = AyaMdParser.extractAya(lit);
        var code = SourceFile.from(locator, underlyingFile, ayaCode);
        aya = new Data(lit, code);
        data.set(aya);
      }
      return aya.extractedAya;
    }

    @Override public @NotNull SourceFile errorReportSourceFile() throws IOException {
      return asMarkdownFile();
    }

    private void render(@NotNull Path outputFile, @NotNull ImmutableSeq<Stmt> program) throws IOException {
      var lit = data.get();
      if (lit == null) return;
      var highlights = SyntaxHighlight.highlight(Option.some(sourceFile()), program);
      new LiterateConsumer.Highlights(highlights).accept(lit.literate);
      Files.writeString(outputFile, lit.literate.toDoc().renderToAyaMd());
    }

    @Override public void saveOutput(
      @NotNull Path outputFile,
      @NotNull CompilerFlags compilerFlags,
      @NotNull ResolveInfo resolveInfo,
      @NotNull ImmutableSeq<GenericDef> defs
    ) throws IOException {
      render(outputFile, resolveInfo.program());
    }

    @Override public void distill(
      @NotNull String outputFileName,
      CompilerFlags.@Nullable DistillInfo flags,
      @NotNull ImmutableSeq<? extends AyaDocile> doc,
      MainArgs.@NotNull DistillStage currentStage
    ) throws IOException {
      if (flags == null || currentStage != MainArgs.DistillStage.scoped) return;
      var distillDir = underlyingFile.resolveSibling(flags.distillDir());
      if (!Files.exists(distillDir)) Files.createDirectories(distillDir);
      //noinspection unchecked
      render(distillDir.resolve(outputFileName + ".html"), (ImmutableSeq<Stmt>) doc);
    }
  }
}
