// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.library.incremental;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableMap;
import org.aya.cli.library.source.LibraryOwner;
import org.aya.cli.library.source.LibrarySource;
import org.aya.core.def.GenericDef;
import org.aya.core.serde.SerTerm;
import org.aya.core.serde.Serializer;
import org.aya.resolve.ResolveInfo;
import org.aya.resolve.module.ModuleLoader;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

public record InMemoryCompilerAdvisor(
  @NotNull MutableMap<Path, FileTime> coreTimestamp,
  @NotNull MutableMap<ImmutableSeq<String>, ResolveInfo> compiledCore
) implements CompilerAdvisor {
  public InMemoryCompilerAdvisor() {
    this(MutableMap.create(), MutableMap.create());
  }

  @Override public boolean isSourceModified(@NotNull LibrarySource source) {
    var coreLastModified = coreTimestamp.getOption(source.file());
    try {
      if (coreLastModified.isEmpty()) return true;
      return Files.getLastModifiedTime(source.file())
        .compareTo(coreLastModified.get()) > 0;
    } catch (IOException ignore) {
      return true;
    }
  }

  @Override public void updateLastModified(@NotNull LibrarySource source) {
    try {
      coreTimestamp.put(source.file(), Files.getLastModifiedTime(source.file()));
    } catch (IOException ignore) {
    }
  }

  @Override public void prepareLibraryOutput(@NotNull LibraryOwner owner) {
  }

  @Override public void prepareModuleOutput(@NotNull LibrarySource source) {
    // TODO: what if module name clashes?
    compiledCore.remove(source.moduleName());
  }

  @Override
  public @Nullable ResolveInfo doLoadCompiledCore(
    SerTerm.@NotNull DeState deState,
    @NotNull Reporter reporter,
    @NotNull ImmutableSeq<String> mod,
    @Nullable Path sourcePath,
    @Nullable Path corePath,
    @NotNull ModuleLoader recurseLoader
  ) {
    // TODO: what if module name clashes?
    return compiledCore.getOrNull(mod);
  }

  @Override public void doSaveCompiledCore(
    Serializer.@NotNull State serState,
    @NotNull LibrarySource file,
    @NotNull ResolveInfo resolveInfo,
    @NotNull ImmutableSeq<GenericDef> defs
  ) {
    // TODO: what if module name clashes?
    compiledCore.put(file.moduleName(), resolveInfo);
  }
}
