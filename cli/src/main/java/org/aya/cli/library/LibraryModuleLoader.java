// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.library;

import kala.collection.Seq;
import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import org.aya.api.error.Reporter;
import org.aya.api.error.SourceFileLocator;
import org.aya.concrete.parse.AyaParsing;
import org.aya.concrete.resolve.ResolveInfo;
import org.aya.concrete.resolve.context.EmptyContext;
import org.aya.concrete.resolve.module.FileModuleLoader;
import org.aya.concrete.resolve.module.ModuleLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Same as {@link FileModuleLoader}, but only resolves the module.
 *
 * @author kiva
 * @see FileModuleLoader
 */
public record LibraryModuleLoader(
  @NotNull SourceFileLocator locator,
  @NotNull LibraryCompiler.Timestamp timestamp,
  @NotNull SeqView<Path> modulePath,
  @NotNull Path outRoot,
  @NotNull Reporter reporter
) implements ModuleLoader {
  static @NotNull Path resolveCompiledCore(@NotNull Path basePath, @NotNull Seq<@NotNull String> moduleName) {
    var withoutExt = moduleName.foldLeft(basePath, Path::resolve);
    return withoutExt.resolveSibling(withoutExt.getFileName() + ".ayac");
  }

  static @NotNull Path resolveFile(@NotNull SeqView<Path> modulePath, @NotNull Seq<String> moduleName) {
    for (var p : modulePath) {
      var file = FileModuleLoader.resolveFile(p, moduleName);
      if (Files.exists(file)) return file;
    }
    throw new IllegalArgumentException("invalid module path");
  }

  @Override
  public @Nullable ResolveInfo load(@NotNull ImmutableSeq<@NotNull String> mod, @NotNull ModuleLoader recurseLoader) {
    var sourcePath = resolveFile(modulePath, mod);
    var corePath = resolveCompiledCore(outRoot, mod);
    if (Files.exists(corePath) && !timestamp().sourceModified(sourcePath))
      return loadCompiledCore(mod, corePath, sourcePath);

    try {
      var program = AyaParsing.program(locator, reporter, sourcePath);
      var context = new EmptyContext(reporter, sourcePath).derive(mod);
      return FileModuleLoader.resolveModule(context, recurseLoader, program, reporter);
    } catch (IOException e) {
      return null;
    }
  }

  private @Nullable ResolveInfo loadCompiledCore(@NotNull ImmutableSeq<String> mod, @NotNull Path corePath, @NotNull Path sourcePath) {
    var context = new EmptyContext(reporter, sourcePath).derive(mod);
    try (var inputStream = openCompiledCore(corePath)) {
      var compiledAya = (CompiledAya) inputStream.readObject();
      return compiledAya.toResolveInfo(context);
    } catch (Exception e) {
      return null;
    }
  }

  private @NotNull ObjectInputStream openCompiledCore(@NotNull Path corePath) throws IOException {
    return new ObjectInputStream(Files.newInputStream(corePath));
  }
}
