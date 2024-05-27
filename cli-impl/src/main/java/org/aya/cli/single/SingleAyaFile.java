// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.single;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.function.BooleanObjBiFunction;
import org.aya.cli.render.RenderOptions;
import org.aya.cli.utils.CliEnums;
import org.aya.cli.utils.LiterateData;
import org.aya.generic.AyaDocile;
import org.aya.literate.Literate;
import org.aya.pretty.doc.Doc;
import org.aya.resolve.ResolveInfo;
import org.aya.syntax.AyaFiles;
import org.aya.syntax.GenericAyaFile;
import org.aya.syntax.GenericAyaParser;
import org.aya.syntax.concrete.stmt.Stmt;
import org.aya.syntax.concrete.stmt.decl.Decl;
import org.aya.syntax.core.def.PrimDef;
import org.aya.syntax.core.def.TyckDef;
import org.aya.util.FileUtil;
import org.aya.util.error.SourceFile;
import org.aya.util.error.SourceFileLocator;
import org.aya.util.prettier.PrettierOptions;
import org.aya.util.reporter.CollectingReporter;
import org.aya.util.reporter.Problem;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.MustBeInvokedByOverriders;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.io.IOException;
import java.nio.file.Path;

public sealed interface SingleAyaFile extends GenericAyaFile {
  private static @Nullable CompilerFlags.PrettyInfo parsePrettyInfo(@NotNull CompilerFlags flags) {
    if (flags.prettyInfo() != null) return flags.prettyInfo();
    return CompilerFlags.prettyInfoFromOutput(flags.outputFile(), new RenderOptions(), false, false, false);
  }

  @SuppressWarnings("unchecked") default void pretty(
    @NotNull CompilerFlags compilerFlags,
    @NotNull ImmutableSeq<? extends AyaDocile> doc,
    @NotNull CollectingReporter reporter,
    @NotNull CliEnums.PrettyStage currentStage
  ) throws IOException {
    var flags = parsePrettyInfo(compilerFlags);
    if (flags == null || currentStage != flags.prettyStage()) return;

    var out = flags.prettyFormat().target;
    String fileName;
    Path prettyDir;

    var outputFile = compilerFlags.outputFile();
    if (outputFile != null) {
      prettyDir = outputFile.toAbsolutePath().getParent();
      fileName = outputFile.getFileName().toString();
    } else {
      prettyDir = flags.prettyDir() != null ? Path.of(flags.prettyDir()) : Path.of(".");
      fileName = AyaFiles.stripAyaSourcePostfix(originalFile().display()) + out.fileExt;
    }

    var renderOptions = flags.renderOptions();
    if (currentStage == CliEnums.PrettyStage.literate) {
      var d = toDoc((ImmutableSeq<Stmt>) doc, reporter.problems().toImmutableSeq(), flags.prettierOptions());
      var text = renderOptions.render(out, d, flags.backendOpts(true));
      FileUtil.writeString(prettyDir.resolve(fileName), text);
    } else {
      doWrite(doc, prettyDir, flags.prettierOptions(), fileName, out.fileExt,
        (hdr, d) -> renderOptions.render(out, d, flags.backendOpts(hdr)));
    }
  }
  @VisibleForTesting default @NotNull Doc toDoc(
    @NotNull ImmutableSeq<Stmt> program,
    @NotNull ImmutableSeq<Problem> problems,
    @NotNull PrettierOptions options
  ) throws IOException {
    return LiterateData.toDoc(this, null, program, problems, options);
  }

  private void doWrite(
    ImmutableSeq<? extends AyaDocile> doc, Path prettyDir,
    @NotNull PrettierOptions options, String fileName, String fileExt,
    BooleanObjBiFunction<Doc, String> toString
  ) throws IOException {
    var docs = MutableList.<Doc>create();
    var eachPrettyDir = prettyDir.resolve(fileName + ".each");
    for (int i = 0; i < doc.size(); i++) {
      var item = doc.get(i);
      // Skip uninteresting items
      var thisDoc = item.toDoc(options);
      docs.append(thisDoc);
      if (item instanceof PrimDef) continue;
      FileUtil.writeString(eachPrettyDir.resolve(FileUtil.escapeFileName(nameOf(i, item)) + fileExt), toString.apply(false, thisDoc));
    }
    FileUtil.writeString(prettyDir.resolve(fileName), toString.apply(true, Doc.vcat(docs)));
  }

  private static @NotNull String nameOf(int i, AyaDocile item) {
    return item instanceof TyckDef def ? def.ref().name()
      : item instanceof Decl decl ? decl.ref().name() : String.valueOf(i);
  }

  /** Must be called after {@link #parseMe} */
  default void resolveAdditional(@NotNull ResolveInfo info) { }

  @MustBeInvokedByOverriders
  default void tyckAdditional(@NotNull ResolveInfo info) {
    resolveAdditional(info);
  }

  record Factory(@NotNull Reporter reporter) implements GenericAyaFile.Factory {
    @Override public @NotNull SingleAyaFile
    createAyaFile(@NotNull SourceFileLocator locator, @NotNull Path path) throws IOException {
      var codeFile = new CodeAyaFile(SourceFile.from(locator, path));
      return AyaFiles.isLiterate(path) ? createLiterateFile(codeFile, reporter) : codeFile;
    }
  }

  record CodeAyaFile(@NotNull SourceFile originalFile) implements SingleAyaFile {
  }

  @VisibleForTesting static @NotNull MarkdownAyaFile
  createLiterateFile(@NotNull CodeAyaFile template, @NotNull Reporter reporter) {
    return new MarkdownAyaFile(template.originalFile, LiterateData.create(template.originalFile, reporter));
  }

  record MarkdownAyaFile(@Override @NotNull SourceFile originalFile,
                         @NotNull LiterateData data) implements SingleAyaFile {
    @Override public void resolveAdditional(@NotNull ResolveInfo info) {
      SingleAyaFile.super.resolveAdditional(info);
      data.resolve(info);
    }

    @Override public void tyckAdditional(@NotNull ResolveInfo info) {
      SingleAyaFile.super.tyckAdditional(info);
      data.tyck(info);
    }

    @Override public @NotNull ImmutableSeq<Stmt> parseMe(@NotNull GenericAyaParser parser) throws IOException {
      data.parseMe(parser);
      return SingleAyaFile.super.parseMe(parser);
    }

    @Override public @NotNull SourceFile codeFile() { return data.extractedAya(); }
    @Override public @NotNull Literate literate() { return data.literate(); }
  }
}
