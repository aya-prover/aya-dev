// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl.config;

import kala.collection.immutable.ImmutableSeq;

public final class Configs {
  private Configs() {
  }

  public static ImmutableSeq<ConfigSaver> configSavers() {
    return ImmutableSeq.of(
      PromptConfigSaver.INSTANCE,
      NormalizeModeConfigSaver.INSTNACE,
      PrintWidthConfigSaver.INSTANCE,
      EnableUnicodeConfigSaver.INSTANCE
    );
  }
}
