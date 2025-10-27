// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.single;

import org.aya.cli.utils.CliEnums;
import org.aya.cli.utils.CompilerUtil;
import org.aya.producer.AyaParserImpl;
import org.aya.resolve.context.EmptyContext;
import org.aya.resolve.context.ModuleContext;
import org.aya.resolve.module.*;
import org.aya.states.primitive.PrimFactory;
import org.aya.util.position.SourceFileLocator;
import org.aya.util.reporter.ClearableReporter;
import org.aya.util.reporter.CollectingReporter;
import org.aya.util.reporter.CountingReporter;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;

public final class SingleFileCompiler {
  /// For pretty-printing
  public final @NotNull CollectingReporter collectingReporter;
  /// For actual error reporting
  public final @NotNull ClearableReporter countingReporter;
  public final @NotNull CompilerFlags flags;
  public final @NotNull SourceFileLocator locator;
  public final @NotNull AyaParserImpl ayaParser;
  public final @NotNull SingleAyaFile.Factory fileManager;
  public final @NotNull ModuleLoader loader;

  public SingleFileCompiler(
    @NotNull Reporter baseReporter,
    @NotNull CompilerFlags flags,
    @Nullable SourceFileLocator baseLocator
  ) {
    this.flags = flags;
    collectingReporter = CollectingReporter.delegate(baseReporter);
    countingReporter = CountingReporter.delegate(collectingReporter);
    locator = baseLocator != null ? baseLocator : new SourceFileLocator.Module(flags.modulePaths());
    ayaParser = new AyaParserImpl(countingReporter);
    fileManager = new SingleAyaFile.Factory(countingReporter);
    loader = new CachedModuleLoader<>(new ModuleListLoader(countingReporter,
      flags.modulePaths().map(path ->
          new FileModuleLoader(locator, path, countingReporter, ayaParser, fileManager))
        .toSeq()));
  }

  public <E extends IOException> int compile(
    @NotNull Path sourceFile, @Nullable ModuleCallback<E> moduleCallback
  ) throws IOException {
    return compile(sourceFile, new EmptyContext(sourceFile).derive("Mian"), moduleCallback);
  }

  public <E extends IOException> int compile(
    @NotNull Path sourceFile,
    @NotNull ModuleContext context,
    @Nullable ModuleCallback<E> moduleCallback
  ) throws IOException {
    return CompilerUtil.catching(countingReporter, flags, () -> {
      var ayaFile = fileManager.createAyaFile(locator, sourceFile);
      var program = ayaFile.parseMe(ayaParser).program();
      ayaFile.pretty(flags, program, collectingReporter, CliEnums.PrettyStage.raw);
      var info = loader.resolveModule(new PrimFactory(), context, program, loader);
      if (info == null) return;
      loader.tyckModule(info, (resolveInfo, defs) -> {
        ayaFile.tyckAdditional(resolveInfo);
        ayaFile.pretty(flags, program, collectingReporter, CliEnums.PrettyStage.scoped);
        ayaFile.pretty(flags, defs, collectingReporter, CliEnums.PrettyStage.typed);
        ayaFile.pretty(flags, program, collectingReporter, CliEnums.PrettyStage.literate);
        if (moduleCallback != null) moduleCallback.onModuleTycked(resolveInfo, defs);
      });
    });
  }
}
