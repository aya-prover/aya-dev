// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.lsp.actions;

import org.aya.api.util.NormalizeMode;
import org.aya.core.term.Term;
import org.aya.lsp.models.ComputeTypeResult;
import org.aya.lsp.server.AyaService;
import org.jetbrains.annotations.NotNull;

public class ComputeNormal extends TermAction {
  public static ComputeTypeResult invoke(@NotNull ComputeTypeResult.Params params, @NotNull AyaService.AyaFile loadedFile) {
    return new ComputeNormal(loadedFile).invoke(params);
  }

  public ComputeNormal(AyaService.@NotNull AyaFile loadedFile) {
    super(loadedFile);
  }

  @Override protected @NotNull Term compute(Term core) {
    return core.normalize(NormalizeMode.NF);
  }
}
