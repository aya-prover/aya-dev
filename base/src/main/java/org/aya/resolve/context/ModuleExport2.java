// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.context;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.collection.mutable.MutableMap;
import org.aya.resolve.error.NameProblem;
import org.aya.syntax.concrete.stmt.ModuleName;
import org.aya.syntax.concrete.stmt.UseHide;
import org.aya.syntax.ref.AnyDefVar;
import org.aya.util.error.WithPos;
import org.aya.util.reporter.Problem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * ModuleExport stores symbols that imports from another module.
 * Any module should NOT export ambiguous symbol/module, they should be solved before they are exported.
 */
public record ModuleExport2(
  @NotNull MutableMap<String, AnyDefVar> symbols,
  @NotNull MutableMap<String, ModuleExport2> modules
) {
  public ModuleExport2(@NotNull ModuleExport2 other) {
    this(MutableMap.from(other.symbols), MutableMap.from(other.modules));
  }

  private record ExportUnit(@Nullable AnyDefVar symbol, @Nullable ModuleExport2 module) {
    public ExportUnit {
      assert symbol != null || module != null : "Sanity check";
    }

    public void forEach(Consumer<AnyDefVar> symbolConsumer, Consumer<ModuleExport2> moduleConsumer) {
      if (symbol != null) symbolConsumer.accept(symbol);
      if (module != null) moduleConsumer.accept(module);
    }
  }

  public @Nullable ModuleExport2 resolveModule(@NotNull ModuleName.Qualified qmod) {
    var head = qmod.head();
    var tail = qmod.tail();
    var mod = getMaybe(head);
    if (mod == null || mod.module == null) return null;
    if (tail == null) return mod.module;
    return mod.module.resolveModule(tail);
  }

  private @Nullable ExportUnit getMaybe(@NotNull String name) {
    var symbol = symbols.getOption(name);
    var module = modules.getOption(name);      // `getOption` for beauty

    if (symbol.isEmpty() && module.isEmpty()) return null;

    return new ExportUnit(symbol.getOrNull(), module.getOrNull());
  }

  private @Nullable ExportUnit removeMaybe(@NotNull String name) {
    var symbol = symbols.remove(name);
    var module = modules.remove(name);
    if (symbol.isEmpty() && module.isEmpty()) return null;

    return new ExportUnit(symbol.getOrNull(), module.getOrNull());
  }

  public @NotNull ExportResult filter(
    @NotNull ImmutableSeq<WithPos<String>> names,
    @NotNull UseHide.Strategy strategy
  ) {
    ModuleExport2 data = null;
    var badNames = MutableList.<WithPos<String>>create();

    // filter
    switch (strategy) {
      case Using -> {
        var mData = new ModuleExport2(MutableMap.create(), MutableMap.create());
        for (var name : names) {
          var unit = getMaybe(name.data());

          if (unit == null) {
            badNames.append(name);
          } else {
            unit.forEach(x -> mData.export(name.data(), x), x -> mData.export(name.data(), x));
          }
        }

        data = mData;
      }
      case Hiding -> {
        var mData = new ModuleExport2(this);
        for (var name : names) {
          var removed = removeMaybe(name.data());

          if (removed == null) {
            badNames.append(name);
          }
        }

        data = mData;
      }
    }

    return new ExportResult(data, badNames.toImmutableSeq(), ImmutableSeq.empty());
  }

  public @NotNull ExportResult map(@NotNull ImmutableSeq<WithPos<UseHide.Rename>> mapper) {
    var newOne = new ModuleExport2(this);

    var badNames = MutableList.<WithPos<String>>create();
    var shadowNames = MutableList.<WithPos<String>>create();

    for (var pair : mapper) {
      var pos = pair.sourcePos();
      var rename = pair.data();
      var from = rename.name();
      var to = rename.to();

      var symbol = getMaybe(from);
      if (symbol == null) {
        badNames.append(new WithPos<>(pos, from));
      } else {
        var notShadow = newOne.export(to, symbol);
        if (!notShadow) {
          shadowNames.append(new WithPos<>(pos, to));
        }
      }
    }

    return new ExportResult(newOne, badNames.toImmutableSeq(), shadowNames.toImmutableSeq());
  }

  record ExportResult(
    @NotNull ModuleExport2 result,
    @NotNull ImmutableSeq<WithPos<String>> invalidNames,
    @NotNull ImmutableSeq<WithPos<String>> shadowNames
  ) {
    public boolean anyError() {
      return invalidNames().isNotEmpty();
    }

    public boolean anyWarn() {
      return shadowNames().isNotEmpty();
    }

    public @NotNull ExportResult flatMap(@NotNull Function<ModuleExport2, ExportResult> bind) {
      var newResult = bind.apply(result);
      return new ExportResult(
        newResult.result,
        invalidNames.appendedAll(newResult.invalidNames),
        shadowNames.appendedAll(newResult.shadowNames)
      );
    }

    public SeqView<Problem> problems() {
      SeqView<Problem> invalidNameProblems = invalidNames().view()
        .map(name -> new NameProblem.QualifiedNameNotFoundError(
          ModuleName.This,
          name.data(),
          name.sourcePos()));

      SeqView<Problem> shadowNameProblems = shadowNames().view()
        .map(name -> new NameProblem.ShadowingWarn(name.data(), name.sourcePos()));

      return shadowNameProblems.concat(invalidNameProblems);
    }
  }

  private boolean export(@NotNull String name, @NotNull ExportUnit unit) {
    var exists = getMaybe(name);
    if (exists != null) removeMaybe(name);    // shadow
    unit.forEach(x -> export(name, x), x -> export(name, x));
    return exists == null;
  }

  /**
   * @return false if there already exist a symbol with the same name.
   */
  public boolean export(@NotNull String name, @NotNull AnyDefVar ref) {
    var exists = symbols.put(name, ref);
    return exists.isEmpty();
  }

  public boolean export(@NotNull String name, @NotNull ModuleExport2 module) {
    var exists = modules.put(name, module);
    return exists.isEmpty();
  }
}
