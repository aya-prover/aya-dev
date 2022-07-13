// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.library.incremental;

import kala.collection.immutable.ImmutableSeq;
import org.aya.cli.library.source.LibrarySource;
import org.aya.core.def.GenericDef;
import org.aya.core.serde.SerTerm;
import org.aya.core.serde.Serializer;
import org.aya.generic.util.InternalException;
import org.aya.resolve.ResolveInfo;
import org.aya.resolve.module.ModuleLoader;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Advises the compiler to be incremental, and support in-memory analysis.
 */
public interface CompilerAdvisor {
  boolean isSourceModified(@NotNull LibrarySource source);
  void updateLastModified(@NotNull LibrarySource source);

  /**
   * Try to load the compiled core. Returns null if the core path does not exist
   * or either source or core path is null.
   */
  @Nullable ResolveInfo doLoadCompiledCore(
    @NotNull SerTerm.DeState deState,
    @NotNull Reporter reporter,
    @NotNull ImmutableSeq<String> mod,
    @Nullable Path sourcePath,
    @Nullable Path corePath,
    @NotNull ModuleLoader recurseLoader
  ) throws IOException, ClassNotFoundException;

  void doSaveCompiledCore(
    @NotNull Serializer.State serState,
    @NotNull LibrarySource file,
    @NotNull ResolveInfo resolveInfo,
    @NotNull ImmutableSeq<GenericDef> defs
  ) throws IOException;

  default @Nullable ResolveInfo loadCompiledCore(
    @NotNull SerTerm.DeState deState,
    @NotNull Reporter reporter,
    @NotNull ImmutableSeq<String> mod,
    @Nullable Path sourcePath,
    @Nullable Path corePath,
    @NotNull ModuleLoader recurseLoader
  ) {
    try {
      return doLoadCompiledCore(deState, reporter, mod, sourcePath, corePath, recurseLoader);
    } catch (IOException | ClassNotFoundException e) {
      throw new InternalException("Compiled aya found but cannot be loaded", e);
    }
  }

  default void saveCompiledCore(
    @NotNull Serializer.State serState,
    @NotNull LibrarySource file,
    @NotNull ResolveInfo resolveInfo,
    @NotNull ImmutableSeq<GenericDef> defs
  ) {
    try {
      doSaveCompiledCore(serState, file, resolveInfo, defs);
      updateLastModified(file);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
