// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.single;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.DynamicSeq;
import org.aya.api.distill.AyaDocile;
import org.aya.api.distill.DistillerOptions;
import org.aya.api.error.CountingReporter;
import org.aya.api.error.Reporter;
import org.aya.api.error.SourceFileLocator;
import org.aya.api.util.InternalException;
import org.aya.api.util.InterruptException;
import org.aya.cli.utils.MainArgs;
import org.aya.concrete.parse.AyaParsing;
import org.aya.concrete.resolve.context.EmptyContext;
import org.aya.concrete.resolve.context.ModuleContext;
import org.aya.concrete.resolve.module.CachedModuleLoader;
import org.aya.concrete.resolve.module.FileModuleLoader;
import org.aya.concrete.resolve.module.ModuleListLoader;
import org.aya.concrete.stmt.Decl;
import org.aya.core.def.Def;
import org.aya.core.def.PrimDef;
import org.aya.pretty.backend.string.StringPrinterConfig;
import org.aya.pretty.doc.Doc;
import org.aya.tyck.trace.Trace;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.BiFunction;
import java.util.function.Function;

public record SingleFileCompiler(
  @NotNull Reporter reporter,
  @Nullable SourceFileLocator locator,
  Trace.@Nullable Builder builder
) {
  public int compile(
    @NotNull Path sourceFile,
    @NotNull CompilerFlags flags,
    @Nullable FileModuleLoader.FileModuleLoaderCallback moduleCallback
  ) throws IOException {
    return compile(sourceFile, reporter -> new EmptyContext(reporter).derive(ImmutableSeq.of("Mian")), flags, moduleCallback);
  }

  public int compile(
    @NotNull Path sourceFile,
    @NotNull Function<Reporter, ModuleContext> context,
    @NotNull CompilerFlags flags,
    @Nullable FileModuleLoader.FileModuleLoaderCallback moduleCallback
  ) throws IOException {
    var reporter = this.reporter instanceof CountingReporter countingReporter
      ? countingReporter : new CountingReporter(this.reporter);
    var ctx = context.apply(reporter);
    var locator = this.locator != null ? this.locator : new SourceFileLocator.Module(flags.modulePaths());
    try {
      var program = AyaParsing.program(locator, reporter, sourceFile);
      var distillInfo = flags.distillInfo();
      distill(sourceFile, distillInfo, program, MainArgs.DistillStage.raw);
      var loader = new ModuleListLoader(flags.modulePaths().view().map(path ->
        new CachedModuleLoader(new FileModuleLoader(locator, path, reporter, moduleCallback, builder))).toImmutableSeq());
      FileModuleLoader.tyckModule(ctx, loader, program, reporter,
        resolveInfo -> {
          distill(sourceFile, distillInfo, program, MainArgs.DistillStage.scoped);
          if (moduleCallback != null) moduleCallback.onResolved(sourceFile, resolveInfo, program);
        },
        defs -> {
          distill(sourceFile, distillInfo, defs, MainArgs.DistillStage.typed);
          if (moduleCallback != null) moduleCallback.onTycked(sourceFile, program, defs);
        }, builder);
    } catch (InternalException e) {
      FileModuleLoader.handleInternalError(e);
      reporter.reportString("Internal error");
      return e.exitCode();
    } catch (InterruptException e) {
      reporter.reportString(e.stage().name() + " interrupted due to:");
      if (flags.interruptedTrace()) e.printStackTrace();
    } finally {
      PrimDef.Factory.INSTANCE.clear();
    }
    if (reporter.noError()) {
      reporter.reportString(flags.message().successNotion());
      return 0;
    } else {
      reporter.reportString(reporter.countToString());
      reporter.reportString(flags.message().failNotion());
      return 1;
    }
  }

  private void distill(
    @NotNull Path sourceFile,
    @Nullable CompilerFlags.DistillInfo flags,
    @NotNull ImmutableSeq<? extends AyaDocile> doc,
    @NotNull MainArgs.DistillStage currentStage
  ) throws IOException {
    if (flags == null || currentStage != flags.distillStage()) return;
    var ayaFileName = sourceFile.getFileName().toString();
    var dotIndex = ayaFileName.indexOf('.');
    var distillDir = sourceFile.resolveSibling(flags.distillDir());
    if (!Files.exists(distillDir)) Files.createDirectories(distillDir);
    var fileName = ayaFileName
      .substring(0, dotIndex > 0 ? dotIndex : ayaFileName.length())
      // Escape file names, see https://stackoverflow.com/a/41108758/7083401
      .replaceAll("[\\\\/:*?\"<>|]", "_");
    switch (flags.distillFormat()) {
      case html -> doWrite(doc, distillDir, fileName, ".html", Doc::renderToHtml);
      case latex -> doWrite(doc, distillDir, fileName, ".tex", (thisDoc, $) -> thisDoc.renderToTeX());
      case plain -> doWrite(doc, distillDir, fileName, ".txt", (thisDoc, $) -> thisDoc.debugRender());
      case unix -> doWrite(doc, distillDir, fileName, ".txt", (thisDoc, $) -> thisDoc.renderToString(StringPrinterConfig.unixTerminal()));
    }
  }

  private void doWrite(
    ImmutableSeq<? extends AyaDocile> doc, Path distillDir,
    String fileName, String fileExt, BiFunction<Doc, Boolean, String> toString
  ) throws IOException {
    var docs = DynamicSeq.<Doc>create();
    for (int i = 0; i < doc.size(); i++) {
      var item = doc.get(i);
      // Skip uninteresting items
      if (item instanceof PrimDef) continue;
      var thisDoc = item.toDoc(DistillerOptions.pretty());
      Files.writeString(distillDir.resolve(fileName + "-" + nameOf(i, item) + fileExt), toString.apply(thisDoc, false));
      docs.append(thisDoc);
    }
    Files.writeString(distillDir.resolve(fileName + fileExt), toString.apply(Doc.vcat(docs), true));
  }

  @NotNull private String nameOf(int i, AyaDocile item) {
    return item instanceof Def def ? def.ref().name()
      : item instanceof Decl decl ? decl.ref().name() : String.valueOf(i);
  }
}
