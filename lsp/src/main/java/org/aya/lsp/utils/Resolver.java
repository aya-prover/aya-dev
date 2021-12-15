// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp.utils;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.control.Option;
import org.aya.api.ref.DefVar;
import org.aya.cli.library.source.LibraryOwner;
import org.aya.cli.library.source.LibrarySource;
import org.aya.core.def.DataDef;
import org.aya.core.def.Def;
import org.aya.core.def.StructDef;
import org.jetbrains.annotations.NotNull;

public interface Resolver {
  private static @NotNull SeqView<Def> withSubLevel(@NotNull Def def) {
    return switch (def) {
      case DataDef data -> SeqView.<Def>of(data).appendedAll(data.body);
      case StructDef struct -> SeqView.<Def>of(struct).appendedAll(struct.fields);
      default -> SeqView.of(def);
    };
  }

  private static @NotNull Option<LibrarySource> findModule(@NotNull LibraryOwner owner, @NotNull ImmutableSeq<String> module) {
    if (module.isEmpty()) return Option.none();
    var mod = owner.findModule(module);
    return mod != null ? Option.of(mod) : findModule(owner, module.dropLast(1));
  }

  static @NotNull Option<Def> resolve(
    @NotNull LibraryOwner owner,
    @NotNull DefVar<?, ?> defVar
  ) {
    return resolve(owner, defVar.module, defVar.name());
  }

  @NotNull private static Option<@NotNull Def> resolve(
    @NotNull LibraryOwner owner,
    @NotNull ImmutableSeq<String> module,
    @NotNull String name
  ) {
    var mod = findModule(owner, module);
    return mod.mapNotNull(m -> m.tycked().value)
      .map(defs -> defs.flatMap(Resolver::withSubLevel))
      .mapNotNull(defs -> defs.find(def -> def.ref().name().equals(name)).getOrNull());
  }
}
