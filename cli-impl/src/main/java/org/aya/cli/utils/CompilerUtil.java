// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.utils;

import kala.function.CheckedRunnable;
import org.aya.cli.single.CompilerFlags;
import org.aya.compiler.CompiledModule;
import org.aya.generic.InterruptException;
import org.aya.resolve.ResolveInfo;
import org.aya.resolve.module.FileModuleLoader;
import org.aya.util.error.Panic;
import org.aya.util.reporter.CountingReporter;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class CompilerUtil {
  public static int catching(
    @NotNull CountingReporter reporter,
    @NotNull CompilerFlags flags,
    @NotNull CheckedRunnable<IOException> block
  ) throws IOException {
    try {
      block.runChecked();
    } catch (Panic e) {
      FileModuleLoader.handleInternalError(e);
      reporter.reportString("Internal error");
      return e.exitCode();
    } catch (InterruptException e) {
      reporter.reportString(e.stage().name() + " interrupted due to:");
      if (flags.interruptedTrace()) e.printStackTrace();
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

  public static void saveCompiledCore(@NotNull Path coreFile, @NotNull ResolveInfo resolveInfo) throws IOException {
    var compiledAya = CompiledModule.from(resolveInfo);
    try (var outputStream = coreWriter(coreFile)) {
      outputStream.writeObject(compiledAya);
    }
  }

  private static @NotNull ObjectOutputStream coreWriter(@NotNull Path coreFile) throws IOException {
    Files.createDirectories(coreFile.toAbsolutePath().getParent());
    return new ObjectOutputStream(Files.newOutputStream(coreFile));
  }
}
