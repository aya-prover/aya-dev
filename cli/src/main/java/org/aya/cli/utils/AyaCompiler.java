// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.utils;

import kala.function.CheckedRunnable;
import org.aya.api.error.CountingReporter;
import org.aya.api.util.InternalException;
import org.aya.api.util.InterruptException;
import org.aya.cli.single.CompilerFlags;
import org.aya.concrete.resolve.module.FileModuleLoader;
import org.aya.core.def.PrimDef;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

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
}
