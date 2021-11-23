// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.library;

import kala.collection.Seq;
import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.value.Ref;
import org.aya.api.error.Reporter;
import org.aya.api.error.SourceFileLocator;
import org.aya.concrete.parse.AyaParsing;
import org.aya.concrete.resolve.ResolveInfo;
import org.aya.concrete.resolve.context.EmptyContext;
import org.aya.concrete.resolve.module.CachedModuleLoader;
import org.aya.concrete.resolve.module.FileModuleLoader;
import org.aya.concrete.resolve.module.ModuleLoader;
import org.aya.core.serde.CompiledAya;
import org.aya.core.serde.SerTerm;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * This module loader is used to load source/compiled modules in a library.
 * For modules belonging to this library, both compiled/source modules are searched and loaded (compiled first).
 * For modules belonging to dependencies, only compiled modules are searched and loaded.
 * This is archived by not storing source dirs of dependencies to
 * {@link LibraryModuleLoader#thisModulePath} and always trying to find compiled cores in
 * {@link LibraryModuleLoader#thisOutRoot}.
 *
 * <p>
 * Unlike {@link FileModuleLoader}, this loader only resolves the module rather than tycking it.
 * Tyck decision is made by {@link LibraryCompiler} by judging whether the source file
 * is modified since last build.
 *
 * @param thisModulePath Source dirs of this module, out dirs of all dependencies.
 * @param thisOutRoot    Out dir of this module.
 * @author kiva
 * @see FileModuleLoader
 */
public record LibraryModuleLoader(
  @NotNull Reporter reporter,
  @NotNull SourceFileLocator locator,
  @NotNull SeqView<Path> thisModulePath,
  @NotNull Path thisOutRoot,
  @NotNull Ref<CachedModuleLoader> cachedSelf,
  @NotNull SerTerm.DeState deState
) implements ModuleLoader {
  public static @NotNull Path resolveCompiledCore(@NotNull Path basePath, @NotNull Seq<@NotNull String> moduleName) {
    var withoutExt = moduleName.foldLeft(basePath, Path::resolve);
    return withoutExt.resolveSibling(withoutExt.getFileName() + ".ayac");
  }

  public static @Nullable Path resolveCompiledDepCore(@NotNull SeqView<Path> modulePath, @NotNull Seq<String> moduleName) {
    for (var p : modulePath) {
      var file = resolveCompiledCore(p, moduleName);
      if (Files.exists(file)) return file;
    }
    return null;
  }

  public static @Nullable Path resolveFile(@NotNull SeqView<Path> modulePath, @NotNull Seq<String> moduleName) {
    for (var p : modulePath) {
      var file = FileModuleLoader.resolveFile(p, moduleName);
      if (Files.exists(file)) return file;
    }
    return null;
  }

  @Override
  public @Nullable ResolveInfo load(@NotNull ImmutableSeq<@NotNull String> mod, @NotNull ModuleLoader recurseLoader) {
    var sourcePath = resolveFile(thisModulePath, mod);
    if (sourcePath == null) {
      // We are loading a module belonging to dependencies, find the compiled core.
      // The compiled core should always exist, otherwise the dependency is not built.
      var depCorePath = resolveCompiledDepCore(thisModulePath, mod);
      assert depCorePath != null : "dependencies not built?";
      return loadCompiledCore(mod, depCorePath, depCorePath);
    }

    // we are loading a module belonging to this library, try finding compiled core first.
    // If found, check modifications and decide whether to proceed with compiled core.
    var corePath = resolveCompiledCore(thisOutRoot, mod);
    if (Files.exists(corePath)) {
      return loadCompiledCore(mod, corePath, sourcePath);
    }

    // No compiled core is found, or source file is modified, compile it from source.
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
      return compiledAya.toResolveInfo(cachedSelf.value, context, deState);
    } catch (IOException | ClassNotFoundException e) {
      return null;
    }
  }

  private @NotNull ObjectInputStream openCompiledCore(@NotNull Path corePath) throws IOException {
    return new ObjectInputStream(Files.newInputStream(corePath));
  }
}
