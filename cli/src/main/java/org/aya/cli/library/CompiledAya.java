// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.library;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.DynamicSeq;
import kala.collection.mutable.MutableMap;
import org.aya.api.ref.DefVar;
import org.aya.api.ref.Var;
import org.aya.concrete.desugar.AyaBinOpSet;
import org.aya.concrete.resolve.ResolveInfo;
import org.aya.concrete.resolve.context.PhysicalModuleContext;
import org.aya.core.def.DataDef;
import org.aya.core.def.StructDef;
import org.aya.core.serde.SerDef;
import org.aya.core.serde.SerTerm;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;

public record CompiledAya(
  @NotNull ImmutableSeq<SerDef.QName> exports,
  @NotNull ImmutableSeq<SerDef> serDefs
) implements Serializable {
  public static @NotNull CompiledAya from(@NotNull ResolveInfo resolveInfo, @NotNull ImmutableSeq<SerDef> defs) {
    if (!(resolveInfo.thisModule() instanceof PhysicalModuleContext ctx)) {
      // TODO[kiva]: how to reach here?
      throw new UnsupportedOperationException();
    }

    var modName = ctx.moduleName();
    var exports = DynamicSeq.<SerDef.QName>create();
    ctx.exports.view().forEach((k, vs) -> {
      var qnameMod = modName.appendedAll(k);
      vs.view().forEach((n, v) -> {
        var qname = new SerDef.QName(qnameMod, n);
        exports.append(qname);
      });
    });

    return new CompiledAya(exports.toImmutableSeq(), defs);
  }

  public @NotNull ResolveInfo toResolveInfo(@NotNull PhysicalModuleContext context) {
    var state = new SerTerm.DeState();
    serDefs.forEach(serDef -> de(context, serDef, state));
    return new ResolveInfo(context, ImmutableSeq.empty(), new AyaBinOpSet(context.reporter()));
  }

  private void de(@NotNull PhysicalModuleContext context, @NotNull SerDef serDef, @NotNull SerTerm.DeState state) {
    var mod = context.moduleName();
    var drop = mod.size();
    var def = serDef.de(state);
    assert def.ref().core != null;
    switch (serDef) {
      case SerDef.Fn fn -> {
        if (isExported(fn.name())) {
          export(context, drop, fn.name(), def.ref());
          export(context, fn.name().name(), def.ref());
        }
      }
      case SerDef.Data data -> {
        if (isExported(data.name())) export(context, data.name().name(), def.ref());
        data.bodies().view().zip(((DataDef) def).body).forEach(tup -> {
          if (isExported(tup._1.self())) export(context, drop, tup._1.self(), tup._2.ref);
        });
      }
      case SerDef.Struct struct -> {
        if (isExported(struct.name())) export(context, struct.name().name(), def.ref());
        struct.fields().view().zip(((StructDef) def).fields).forEach(tup -> {
          if (isExported(tup._1.self())) export(context, drop, tup._1.self(), tup._2.ref);
        });
      }
      default -> {}
    }
  }

  private void export(@NotNull PhysicalModuleContext context, int dropMod, @NotNull SerDef.QName qname, DefVar<?, ?> ref) {
    export(context, qname.mod().drop(dropMod), qname.name(), ref);
  }

  private void export(@NotNull PhysicalModuleContext context, @NotNull String name, @NotNull Var var) {
    export(context, ImmutableSeq.empty(), name, var);
  }

  private void export(@NotNull PhysicalModuleContext context, @NotNull ImmutableSeq<String> mod, @NotNull String name, @NotNull Var var) {
    context.exports.getOrPut(ImmutableSeq.empty(), MutableMap::create).put(name, var);
    context.exports.getOrPut(mod, MutableMap::create).put(name, var);
  }

  private boolean isExported(@NotNull SerDef.QName qname) {
    return exports.contains(qname);
  }
}
