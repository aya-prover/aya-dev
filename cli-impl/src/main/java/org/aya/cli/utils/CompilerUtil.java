// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.utils;

import kala.collection.immutable.ImmutableSeq;
import kala.function.CheckedRunnable;
import org.aya.cli.single.CompilerFlags;
import org.aya.compiler.CompiledModule;
import org.aya.generic.InterruptException;
import org.aya.resolve.ResolveInfo;
import org.aya.syntax.core.def.TyckDef;
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
      handleInternalError(e);
      reporter.reportString("Internal error");
      return e.exitCode();
    } catch (InterruptException e) {
      reporter.reportString(e.stage().name() + " interrupted due to:");
      if (flags.interruptedTrace()) e.printStackTrace();
    }
    if (reporter.noError()) {
      reporter.reportString(flags.message().successNotation());
      return 0;
    } else {
      reporter.reportString(reporter.countToString());
      reporter.reportString(flags.message().failNotation());
      return 1;
    }
  }

  public static void saveCompiledCore(
    @NotNull Path coreFile, @NotNull ImmutableSeq<TyckDef> defs,
    @NotNull ResolveInfo resolveInfo
  ) throws IOException {
    var compiledAya = CompiledModule.from(resolveInfo, defs);
    try (var outputStream = coreWriter(coreFile)) {
      outputStream.writeObject(compiledAya);
    }
  }

  private static @NotNull ObjectOutputStream coreWriter(@NotNull Path coreFile) throws IOException {
    Files.createDirectories(coreFile.toAbsolutePath().getParent());
    return new ObjectOutputStream(Files.newOutputStream(coreFile));
  }
  public static void handleInternalError(@NotNull Panic e) {
    e.printStackTrace();
    e.printHint();
    System.err.println("""
      Please report the stacktrace to the developers so a better error handling could be made.
      Don't forget to inform the version of Aya you're using and attach your code for reproduction.""");
  }
}
