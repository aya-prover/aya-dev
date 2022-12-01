// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.single;

import kala.collection.immutable.ImmutableSeq;
import org.aya.cli.parse.AyaParserImpl;
import org.aya.cli.utils.AyaCompiler;
import org.aya.cli.utils.MainArgs;
import org.aya.core.def.PrimDef;
import org.aya.generic.util.AyaFiles;
import org.aya.resolve.ModuleCallback;
import org.aya.resolve.context.EmptyContext;
import org.aya.resolve.context.ModuleContext;
import org.aya.resolve.module.CachedModuleLoader;
import org.aya.resolve.module.FileModuleLoader;
import org.aya.resolve.module.ModuleListLoader;
import org.aya.tyck.trace.Trace;
import org.aya.util.FileUtil;
import org.aya.util.error.SourceFileLocator;
import org.aya.util.reporter.CountingReporter;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Function;

public record SingleFileCompiler(
  @NotNull Reporter reporter,
  @Nullable SourceFileLocator locator,
  @Nullable Trace.Builder builder
) {
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
    var reporter = CountingReporter.of(this.reporter);
    var ctx = context.apply(reporter);
    var locator = this.locator != null ? this.locator : new SourceFileLocator.Module(flags.modulePaths());
    var ayaParser = new AyaParserImpl(reporter);
    var fileManager = new SingleAyaFile.Factory(ayaParser);
    var primFactory = new PrimDef.Factory();
    return AyaCompiler.catching(reporter, flags, () -> {
      var ayaFile = fileManager.createAyaFile(locator, sourceFile);
      var program = ayaParser.program(ayaFile);
      var distillInfo = flags.distillInfo();
      var distillOut = FileUtil.escapeFileName(AyaFiles.stripAyaSourcePostfix(sourceFile.getFileName().toString()));
      ayaFile.distill(distillOut, distillInfo, program, MainArgs.DistillStage.raw);
      var loader = new CachedModuleLoader<>(new ModuleListLoader(reporter, flags.modulePaths().view().map(path ->
        new FileModuleLoader(locator, path, reporter, ayaParser, fileManager, primFactory, builder)).toImmutableSeq()));
      loader.tyckModule(primFactory, ctx, program, builder, (moduleResolve, defs) -> {
        ayaFile.distill(distillOut, distillInfo, program, MainArgs.DistillStage.scoped);
        ayaFile.distill(distillOut, distillInfo, defs, MainArgs.DistillStage.typed);
        if (flags.outputFile() != null) ayaFile.saveOutput(flags.outputFile(), flags, moduleResolve, defs);
        if (moduleCallback != null) moduleCallback.onModuleTycked(moduleResolve, defs);
      });
    });
  }
}
