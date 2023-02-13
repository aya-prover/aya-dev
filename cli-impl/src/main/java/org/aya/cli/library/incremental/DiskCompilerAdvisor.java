// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.library.incremental;

import kala.collection.immutable.ImmutableSeq;
import org.aya.cli.library.source.LibraryOwner;
import org.aya.cli.library.source.LibrarySource;
import org.aya.cli.utils.CompilerUtil;
import org.aya.core.def.GenericDef;
import org.aya.core.serde.CompiledAya;
import org.aya.core.serde.SerTerm;
import org.aya.core.serde.Serializer;
import org.aya.resolve.ResolveInfo;
import org.aya.resolve.context.EmptyContext;
import org.aya.resolve.module.ModuleLoader;
import org.aya.util.FileUtil;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class DiskCompilerAdvisor implements CompilerAdvisor {
  @Override public boolean isSourceModified(@NotNull LibrarySource source) {
    try {
      var core = source.compiledCorePath();
      if (!Files.exists(core)) return true;
      return Files.getLastModifiedTime(source.underlyingFile())
        .compareTo(Files.getLastModifiedTime(core)) > 0;
    } catch (IOException ignore) {
      return true;
    }
  }

  @Override public void updateLastModified(@NotNull LibrarySource source) {
    try {
      var core = source.compiledCorePath();
      Files.setLastModifiedTime(core, Files.getLastModifiedTime(source.underlyingFile()));
    } catch (IOException ignore) {
    }
  }

  @Override public void prepareLibraryOutput(@NotNull LibraryOwner owner) throws IOException {
    Files.createDirectories(owner.outDir());
  }

  @Override public void clearLibraryOutput(@NotNull LibraryOwner owner) throws IOException {
    FileUtil.deleteRecursively(owner.outDir());
  }

  @Override public void clearModuleOutput(@NotNull LibrarySource source) throws IOException {
    Files.deleteIfExists(source.compiledCorePath());
  }

  @Override public @Nullable ResolveInfo doLoadCompiledCore(
    @NotNull SerTerm.DeState deState,
    @NotNull Reporter reporter,
    @NotNull ImmutableSeq<String> mod,
    @Nullable Path sourcePath,
    @Nullable Path corePath,
    @NotNull ModuleLoader recurseLoader
  ) throws IOException, ClassNotFoundException {
    if (corePath == null || sourcePath == null) return null;
    if (!Files.exists(corePath)) return null;

    var context = new EmptyContext(reporter, sourcePath).derive(mod);
    try (var inputStream = FileUtil.ois(corePath)) {
      var compiledAya = (CompiledAya) inputStream.readObject();
      return compiledAya.toResolveInfo(recurseLoader, context, deState);
    }
  }

  @Override public void doSaveCompiledCore(
    @NotNull Serializer.State serState,
    @NotNull LibrarySource file,
    @NotNull ResolveInfo resolveInfo,
    @NotNull ImmutableSeq<GenericDef> defs
  ) throws IOException {
    var coreFile = file.compiledCorePath();
    CompilerUtil.saveCompiledCore(coreFile, resolveInfo, defs, serState);
  }
}
