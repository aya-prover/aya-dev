// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.utils;

import kala.collection.immutable.ImmutableSeq;
import kala.function.CheckedRunnable;
import kala.tuple.Unit;
import org.aya.api.error.CountingReporter;
import org.aya.api.util.InternalException;
import org.aya.api.util.InterruptException;
import org.aya.cli.library.CompiledAya;
import org.aya.cli.single.CompilerFlags;
import org.aya.concrete.resolve.ResolveInfo;
import org.aya.concrete.resolve.module.FileModuleLoader;
import org.aya.core.def.Def;
import org.aya.core.def.PrimDef;
import org.aya.core.serde.Serializer;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class AyaCompiler {
  public static int catching(
    @NotNull CountingReporter reporter,
    @NotNull CompilerFlags flags,
    @NotNull CheckedRunnable<IOException> block
  ) throws IOException {
    try {
      block.runChecked();
    } catch (InternalException e) {
      FileModuleLoader.handleInternalError(e);
      reporter.reportString("Internal error");
      return e.exitCode();
    } catch (InterruptException e) {
      reporter.reportString(e.stage().name() + " interrupted due to:");
      if (flags.interruptedTrace()) e.printStackTrace();
    } finally {
      PrimDef.Factory.INSTANCE.clear();
    }
    if (reporter.noError()) {
      reporter.reportString(flags.message().successNotion());
      return 0;
    } else {
      reporter.reportString(reporter.countToString());
      reporter.reportString(flags.message().failNotion());
      return 1;
    }
  }

  public static void saveCompiledCore(
    @NotNull Path coreFile,
    @NotNull ResolveInfo resolveInfo,
    @NotNull ImmutableSeq<Def> defs
  ) throws IOException {
    try (var outputStream = coreWriter(coreFile)) {
      var serDefs = defs.map(def -> def.accept(new Serializer(new Serializer.State()), Unit.unit()));
      var compiled = CompiledAya.from(resolveInfo, serDefs);
      outputStream.writeObject(compiled);
    }
  }

  private static @NotNull ObjectOutputStream coreWriter(@NotNull Path coreFile) throws IOException {
    Files.createDirectories(coreFile.toAbsolutePath().getParent());
    return new ObjectOutputStream(Files.newOutputStream(coreFile));
  }
}
