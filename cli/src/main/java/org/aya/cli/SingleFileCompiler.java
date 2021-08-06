// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.cli;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.Buffer;
import org.aya.api.error.CountingReporter;
import org.aya.api.error.Reporter;
import org.aya.api.error.SourceFileLocator;
import org.aya.api.util.InternalException;
import org.aya.api.util.InterruptException;
import org.aya.concrete.parse.AyaParsing;
import org.aya.concrete.resolve.module.CachedModuleLoader;
import org.aya.concrete.resolve.module.FileModuleLoader;
import org.aya.concrete.resolve.module.ModuleListLoader;
import org.aya.concrete.stmt.Decl;
import org.aya.concrete.stmt.Stmt;
import org.aya.core.def.Def;
import org.aya.core.def.PrimDef;
import org.aya.core.def.TopLevel;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Docile;
import org.aya.tyck.trace.Trace;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;

public record SingleFileCompiler(
  @NotNull Reporter reporter,
  @Nullable SourceFileLocator locator,
  Trace.@Nullable Builder builder
) {
  public int compile(@NotNull Path sourceFile, @NotNull CompilerFlags flags) throws IOException {
    return compile(sourceFile, flags, stmts -> {}, (stmts, defs) -> {});
  }

  public int compile(
    @NotNull Path sourceFile, @NotNull CompilerFlags flags,
    @NotNull Consumer<ImmutableSeq<Stmt>> onResolved,
    @NotNull BiConsumer<ImmutableSeq<Stmt>, ImmutableSeq<TopLevel>> onTycked
  ) throws IOException {
    var reporter = new CountingReporter(this.reporter);
    var locator = this.locator != null ? this.locator : new SourceFileLocator.Module(flags.modulePaths());
    try {
      var program = AyaParsing.program(locator, reporter, sourceFile);
      var distillInfo = flags.distillInfo();
      distill(sourceFile, distillInfo, program, CliArgs.DistillStage.raw);
      var loader = new ModuleListLoader(flags.modulePaths().view().map(path ->
        new CachedModuleLoader(new FileModuleLoader(locator, path, reporter, builder))).toImmutableSeq());
      FileModuleLoader.tyckModule(loader, program, reporter,
        () -> {
          distill(sourceFile, distillInfo, program, CliArgs.DistillStage.scoped);
          onResolved.accept(program);
        },
        defs -> {
          distill(sourceFile, distillInfo, defs, CliArgs.DistillStage.typed);
          onTycked.accept(program, defs);
        }, builder);
    } catch (InternalException e) {
      FileModuleLoader.handleInternalError(e);
      reporter.reportString("Internal error");
      return e.exitCode();
    } catch (InterruptException e) {
      reporter.reportString(e.stage().name() + " interrupted due to error(s).");
      if (flags.interruptedTrace()) e.printStackTrace();
    } finally {
      PrimDef.clearConcrete();
    }
    if (reporter.isEmpty()) {
      reporter.reportString(flags.message().successNotion());
      return 0;
    } else {
      reporter.reportString(flags.message().failNotion());
      return -1;
    }
  }

  private void distill(
    @NotNull Path sourceFile,
    @Nullable CompilerFlags.DistillInfo flags,
    ImmutableSeq<? extends Docile> doc,
    @NotNull CliArgs.DistillStage currentStage
  ) throws IOException {
    if (flags == null || currentStage != flags.distillStage()) return;
    var ayaFileName = sourceFile.getFileName().toString();
    var dotIndex = ayaFileName.indexOf('.');
    var distillDir = sourceFile.resolveSibling(flags.distillDir());
    if (!Files.exists(distillDir)) Files.createDirectories(distillDir);
    var fileName = ayaFileName
      .substring(0, dotIndex > 0 ? dotIndex : ayaFileName.length());
    switch (flags.distillFormat()) {
      case html -> doWrite(doc, distillDir, fileName, ".html", Doc::renderToHtml);
      case latex -> doWrite(doc, distillDir, fileName, ".tex", (thisDoc, bool) -> thisDoc.renderToTeX());
    }
  }

  private void doWrite(
    ImmutableSeq<? extends Docile> doc, Path distillDir,
    String fileName, String fileExt, BiFunction<Doc, Boolean, String> toString
  ) throws IOException {
    var docs = Buffer.<Doc>of();
    for (int i = 0; i < doc.size(); i++) {
      var item = doc.get(i);
      var thisDoc = item.toDoc();
      Files.writeString(distillDir.resolve(fileName + "-" + nameOf(i, item) + fileExt), toString.apply(thisDoc, false));
      docs.append(thisDoc);
    }
    Files.writeString(distillDir.resolve(fileName + fileExt), toString.apply(Doc.vcat(docs), true));
  }

  @NotNull private String nameOf(int i, Docile item) {
    return item instanceof Def def ? def.ref().name()
      : item instanceof Decl decl ? decl.ref().name() : String.valueOf(i);
  }
}
