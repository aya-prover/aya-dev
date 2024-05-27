// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.single;

import org.aya.cli.utils.CliEnums;
import org.aya.cli.utils.CompilerUtil;
import org.aya.primitive.PrimFactory;
import org.aya.producer.AyaParserImpl;
import org.aya.resolve.context.EmptyContext;
import org.aya.resolve.context.ModuleContext;
import org.aya.resolve.module.*;
import org.aya.util.error.SourceFileLocator;
import org.aya.util.reporter.CollectingReporter;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Function;

public final class SingleFileCompiler {
  public final @NotNull CollectingReporter reporter;
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
    reporter = CollectingReporter.delegate(baseReporter);
    locator = baseLocator != null ? baseLocator : new SourceFileLocator.Module(flags.modulePaths());
    ayaParser = new AyaParserImpl(reporter);
    fileManager = new SingleAyaFile.Factory(reporter);
    loader = new CachedModuleLoader<>(new ModuleListLoader(this.reporter,
      flags.modulePaths().view().map(path ->
        new FileModuleLoader(locator, path, reporter, ayaParser, fileManager)).toImmutableSeq()));
  }

  public <E extends IOException> int compile(
    @NotNull Path sourceFile, @Nullable ModuleCallback<E> moduleCallback
  ) throws IOException {
    return compile(sourceFile, reporter -> new EmptyContext(reporter, sourceFile).derive("Mian"), moduleCallback);
  }

  public <E extends IOException> int compile(
    @NotNull Path sourceFile,
    @NotNull Function<Reporter, ModuleContext> context,
    @Nullable ModuleCallback<E> moduleCallback
  ) throws IOException {
    return CompilerUtil.catching(reporter, flags, () -> {
      var ctx = context.apply(reporter);
      var ayaFile = fileManager.createAyaFile(locator, sourceFile);
      var program = ayaFile.parseMe(ayaParser);
      ayaFile.pretty(flags, program, reporter, CliEnums.PrettyStage.raw);
      loader.tyckModule(new PrimFactory(), ctx, program, (moduleResolve, defs) -> {
        ayaFile.tyckAdditional(moduleResolve);
        ayaFile.pretty(flags, program, reporter, CliEnums.PrettyStage.scoped);
        ayaFile.pretty(flags, defs, reporter, CliEnums.PrettyStage.typed);
        ayaFile.pretty(flags, program, reporter, CliEnums.PrettyStage.literate);
        if (moduleCallback != null) moduleCallback.onModuleTycked(moduleResolve, defs);
      });
    });
  }
}
