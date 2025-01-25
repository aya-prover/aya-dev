// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp.tester;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.collection.mutable.MutableMap;
import org.aya.cli.library.incremental.DiskCompilerAdvisor;
import org.aya.cli.library.incremental.InMemoryCompilerAdvisor;
import org.aya.cli.library.source.LibrarySource;
import org.aya.resolve.ResolveInfo;
import org.aya.resolve.module.ModuleLoader;
import org.aya.syntax.core.def.TyckDef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.function.Function;

public class LspTestCompilerAdvisor extends DiskCompilerAdvisor {
  public @Nullable ImmutableSeq<ImmutableSeq<LibrarySource>> lastJob;
  public final @NotNull MutableMap<Path, FileTime> newlyModified = MutableMap.create();
  public final @NotNull MutableList<ResolveInfo> newlyCompiled = MutableList.create();

  public @NotNull SeqView<LibrarySource> lastCompiled() {
    var lastJob = this.lastJob;
    if (lastJob == null) return SeqView.empty();
    return lastJob.view().flatMap(Function.identity());
  }

  public void mutate(@NotNull LibrarySource source) {
    newlyModified.put(source.underlyingFile(), FileTime.from(Instant.now()));
  }

  @Override
  public boolean isSourceModified(@NotNull LibrarySource source) {
    var tempFileTime = newlyModified.getOrNull(source.underlyingFile());
    if (tempFileTime != null) {
      try {
        // TODO: duplicate code as in [InMemoryCompilerAdvisor] and [DiskCompilerAdvisor]
        var core = source.compiledCorePath();
        if (!Files.exists(core)) return true;
        return tempFileTime.compareTo(Files.getLastModifiedTime(core)) > 0;
      } catch (IOException e) {
        return true;
      }
    }

    return super.isSourceModified(source);
  }

  public void prepareCompile() {
    lastJob = null;
    newlyCompiled.clear();
  }

  @Override public void
  notifyIncrementalJob(@NotNull ImmutableSeq<LibrarySource> modified, @NotNull ImmutableSeq<ImmutableSeq<LibrarySource>> affected) {
    this.lastJob = affected;
  }

  @Override public @NotNull ResolveInfo
  doSaveCompiledCore(
    @NotNull LibrarySource file,
    @NotNull ResolveInfo resolveInfo,
    @NotNull ImmutableSeq<TyckDef> defs,
    @NotNull ModuleLoader recurseLoader
  ) throws IOException, ClassNotFoundException {
    var info = super.doSaveCompiledCore(file, resolveInfo, defs, recurseLoader);
    newlyCompiled.append(resolveInfo);
    return info;
  }
}
