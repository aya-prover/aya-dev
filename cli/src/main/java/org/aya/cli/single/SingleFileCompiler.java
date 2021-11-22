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
import org.aya.cli.utils.AyaCompiler;
import org.aya.cli.utils.MainArgs;
import org.aya.concrete.parse.AyaParsing;
import org.aya.concrete.resolve.ModuleCallback;
import org.aya.concrete.resolve.context.EmptyContext;
import org.aya.concrete.resolve.context.ModuleContext;
import org.aya.concrete.resolve.module.CachedModuleLoader;
import org.aya.concrete.resolve.module.FileModuleLoader;
import org.aya.concrete.resolve.module.ModuleListLoader;
import org.aya.concrete.stmt.Decl;
import org.aya.core.def.Def;
import org.aya.core.def.PrimDef;
import org.aya.core.serde.Serializer;
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
  @Nullable Trace.Builder builder,
  @NotNull DistillerOptions distillerOptions
) {
  public SingleFileCompiler(@NotNull Reporter reporter, @Nullable SourceFileLocator locator, @Nullable Trace.Builder builder) {
    this(reporter, locator, builder, DistillerOptions.pretty());
  }

  public <E extends IOException> int compile(
    @NotNull Path sourceFile,
    @NotNull CompilerFlags flags,
    @Nullable ModuleCallback<E> moduleCallback
  ) throws IOException {
    return compile(sourceFile, reporter -> new EmptyContext(reporter, sourceFile).derive(ImmutableSeq.of("Mian")), flags, moduleCallback);
  }

  public <E extends IOException> int compile(
    @NotNull Path sourceFile,
    @NotNull Function<Reporter, ModuleContext> context,
    @NotNull CompilerFlags flags,
    @Nullable ModuleCallback<E> moduleCallback
  ) throws IOException {
    var reporter = this.reporter instanceof CountingReporter countingReporter
      ? countingReporter : new CountingReporter(this.reporter);
    var ctx = context.apply(reporter);
    var locator = this.locator != null ? this.locator : new SourceFileLocator.Module(flags.modulePaths());
    return AyaCompiler.catching(reporter, flags, () -> {
      var program = AyaParsing.program(locator, reporter, sourceFile);
      var distillInfo = flags.distillInfo();
      distill(sourceFile, distillInfo, program, MainArgs.DistillStage.raw);
      var loader = new ModuleListLoader(flags.modulePaths().view().map(path ->
        new CachedModuleLoader(new FileModuleLoader(locator, path, reporter, builder))).toImmutableSeq());
      FileModuleLoader.tyckModule(ctx, loader, program, reporter, builder,
        (moduleResolve, stmts, defs) -> {
          distill(sourceFile, distillInfo, program, MainArgs.DistillStage.scoped);
          distill(sourceFile, distillInfo, defs, MainArgs.DistillStage.typed);
          if (flags.outputFile() != null) AyaCompiler.saveCompiledCore(flags.outputFile(), moduleResolve, defs, new Serializer.State());
          if (moduleCallback != null) moduleCallback.onModuleTycked(moduleResolve, stmts, defs);
        });
    });
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
    var fileName = escape(ayaFileName.substring(0, dotIndex > 0 ? dotIndex : ayaFileName.length()));
    switch (flags.distillFormat()) {
      case html -> doWrite(doc, distillDir, fileName, ".html", Doc::renderToHtml);
      case latex -> doWrite(doc, distillDir, fileName, ".tex", (thisDoc, $) -> thisDoc.renderToTeX());
      case plain -> doWrite(doc, distillDir, fileName, ".txt", (thisDoc, $) -> thisDoc.debugRender());
      case unix -> doWrite(doc, distillDir, fileName, ".txt", (thisDoc, $) -> thisDoc.renderToString(StringPrinterConfig.unixTerminal()));
    }
  }

  private @NotNull String escape(@NotNull String s) {
    // Escape file names, see https://stackoverflow.com/a/41108758/7083401
    return s.replaceAll("[\\\\/:*?\"<>|]", "_");
  }

  private void doWrite(
    ImmutableSeq<? extends AyaDocile> doc, Path distillDir,
    String fileName, String fileExt, BiFunction<Doc, Boolean, String> toString
  ) throws IOException {
    var docs = DynamicSeq.<Doc>create();
    for (int i = 0; i < doc.size(); i++) {
      var item = doc.get(i);
      // Skip uninteresting items
      var thisDoc = item.toDoc(distillerOptions);
      docs.append(thisDoc);
      if (item instanceof PrimDef) continue;
      Files.writeString(distillDir.resolve(fileName + "-" + escape(nameOf(i, item)) + fileExt), toString.apply(thisDoc, false));
    }
    Files.writeString(distillDir.resolve(fileName + fileExt), toString.apply(Doc.vcat(docs), true));
  }

  @NotNull private String nameOf(int i, AyaDocile item) {
    return item instanceof Def def ? def.ref().name()
      : item instanceof Decl decl ? decl.ref().name() : String.valueOf(i);
  }
}
