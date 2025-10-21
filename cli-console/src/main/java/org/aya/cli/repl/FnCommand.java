// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl;

import kala.collection.immutable.ImmutableSeq;
import org.aya.repl.Command;
import org.aya.syntax.core.def.FnDef;
import org.aya.syntax.ref.AnyDefVar;
import org.aya.syntax.ref.DefVar;
import org.jetbrains.annotations.NotNull;

public abstract class FnCommand extends Command {
  public FnCommand(@NotNull ImmutableSeq<String> names, @NotNull String help) {
    super(names, help);
  }

  @Entry public @NotNull Command.Result execute(@NotNull AyaRepl repl, @NotNull ReplCommands.Code code) {
    var resolved = repl.replCompiler.parseToAnyVar(code.code());
    if (!(resolved instanceof AnyDefVar anyDefVar)) return Result.err("Not a valid reference", true);
    if (!(anyDefVar instanceof DefVar<?, ?> defVar)) return Result.err("JIT-compiled defs are unsupported", true);
    if (!(defVar.core instanceof FnDef fn)) return Result.err("Not a function", true);
    return executeFn(repl, fn);
  }
  public abstract @NotNull Result executeFn(@NotNull AyaRepl repl, FnDef fn);
}
