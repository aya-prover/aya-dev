// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.context;

import kala.collection.Seq;
import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.collection.mutable.MutableMap;
import org.aya.resolve.error.NameProblem;
import org.aya.syntax.concrete.stmt.ModuleName;
import org.aya.syntax.concrete.stmt.QualifiedID;
import org.aya.syntax.concrete.stmt.UseHide;
import org.aya.syntax.ref.AnyDefVar;
import org.aya.util.error.WithPos;
import org.aya.util.reporter.Problem;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * ModuleExport stores symbols that imports from another module.
 * Any module should NOT export ambiguous symbol/module, they should be solved before they are exported.
 */
public record ModuleExport(
  @NotNull MutableMap<String, AnyDefVar> symbols,
  @NotNull MutableMap<ModuleName.Qualified, ModuleExport> modules
) {
  public ModuleExport() { this(MutableMap.create(), MutableMap.create()); }

  public ModuleExport(@NotNull ModuleExport that) {
    this(MutableMap.from(that.symbols), MutableMap.from(that.modules));
  }

  /**
   * @implSpec In case of qualified renaming, only the module is renamed, for example (pseudocode):
   * <pre>
   *   module foo {
   *     module bar {
   *       data A
   *     }
   *   }
   *
   *   open import foo using (bar::A as B)
   *   // only module [bar::A] is renamed, the name [B] can not be used as the type, but only
   *   // the accessor of its constructors.
   * </pre>
   * Well, the cost to also rename the type is not very expensive, we just need to make a new {@link ModuleExport}
   * to store that symbol, but I am 2 lazy 😭😭😭😭.
   */
  @Contract(pure = true)
  @NotNull ExportResult filter(@NotNull ImmutableSeq<QualifiedID> names, UseHide.Strategy strategy) {
    final ModuleExport newModule;
    var badNames = MutableList.<QualifiedID>create();

    switch (strategy) {
      case Using -> {
        newModule = new ModuleExport();
        for (var name : names) {
          var unit = get(name.component(), name.name());

          if (unit == null) {
            badNames.append(name);
          } else {
            unit.forEach(x -> {
              if (name.component() == ModuleName.This) newModule.export(name.name(), x);
            }, x -> newModule.export(name.component().resolve(name.name()), x));
          }
        }
      }
      case Hiding -> {
        newModule = new ModuleExport(this);

        names.forEach(qname -> {
          var oldUnit = newModule.remove(qname.component(), qname.name());
          if (oldUnit == null) badNames.append(qname);
        });
      }
      default -> throw new AssertionError("I mean, this case is impossible.");
    }

    return new ExportResult(
      badNames.isNotEmpty() ? this : newModule,
      badNames.toImmutableSeq(),
      ImmutableSeq.empty());
  }

  @Contract(pure = true)
  @NotNull ExportResult map(@NotNull Seq<WithPos<UseHide.Rename>> mapper) {
    var newExport = new ModuleExport(this);
    var badNames = MutableList.<QualifiedID>create();
    var shadowNames = MutableList.<WithPos<String>>create();

    for (var pair : mapper) {
      var pos = pair.sourcePos();
      var fromModule = pair.data().name().component();
      var fromName = pair.data().name().name();
      var to = pair.data().to();
      if (fromModule == ModuleName.This && fromName.equals(to)) continue;

      var thing = newExport.remove(fromModule, fromName);
      if (thing != null) {
        var dest = newExport.get(ModuleName.This, to);
        if (dest != null) {
          var isShadow = (thing.symbol != null && dest.symbol != null) || (thing.module != null && dest.module != null);
          if (isShadow) {
            shadowNames.append(new WithPos<>(pos, to));
          }
        }

        thing.forEach(x -> newExport.export(to, x), x -> newExport.export(ModuleName.of(to), x));
      } else {
        badNames.append(pair.data().name());
      }
    }

    var hasError = badNames.isNotEmpty();

    return new ExportResult(
      hasError ? this : newExport,
      badNames.toImmutableSeq(),
      shadowNames.toImmutableSeq()
    );
  }

  /**
   * @return false if there already exist a symbol with the same name.
   */
  public boolean export(@NotNull String name, @NotNull AnyDefVar ref) {
    var exists = symbols.put(name, ref);
    return exists.isEmpty();
  }

  public boolean export(@NotNull ModuleName.Qualified componentName, @NotNull ModuleExport module) {
    return modules.put(componentName, module).isEmpty();
  }

  /// region Helper Methods for Mapping/Filtering

  private @Nullable ExportUnit get(@NotNull ModuleName component, @NotNull String name) {
    var symbol = component == ModuleName.This ? symbols.getOrNull(name) : null;
    var module = modules.getOrNull(component.resolve(name));
    if (symbol == null && module == null) return null;

    return new ExportUnit(symbol, module);
  }

  private @Nullable ExportUnit remove(@NotNull ModuleName component, @NotNull String name) {
    var symbol = component == ModuleName.This ? symbols.remove(name).getOrNull() : null;
    var module = modules.remove(component.resolve(name)).getOrNull();
    if (symbol == null && module == null) return null;

    return new ExportUnit(symbol, module);
  }

  private record ExportUnit(@Nullable AnyDefVar symbol, @Nullable ModuleExport module) {
    public ExportUnit {
      assert symbol != null || module != null : "Sanity check";
    }

    public void forEach(Consumer<AnyDefVar> symbolConsumer, Consumer<ModuleExport> moduleConsumer) {
      if (symbol != null) symbolConsumer.accept(symbol);
      if (module != null) moduleConsumer.accept(module);
    }
  }

  /// endregion

  /**
   * @param result the new module export if success, the old module export if failed.
   */
  record ExportResult(
    @NotNull ModuleExport result,
    @NotNull ImmutableSeq<QualifiedID> invalidNames,
    @NotNull ImmutableSeq<WithPos<String>> shadowNames
  ) {
    public boolean anyError() {
      return invalidNames().isNotEmpty();
    }

    public boolean anyWarn() {
      return shadowNames().isNotEmpty();
    }

    public SeqView<Problem> problems(@NotNull ModuleName modName) {
      SeqView<Problem> invalidNameProblems = invalidNames().view()
        .map(name -> new NameProblem.QualifiedNameNotFoundError(
          modName.concat(name.component()),
          name.name(),
          name.sourcePos()));

      SeqView<Problem> shadowNameProblems = shadowNames().view()
        .map(name -> new NameProblem.ShadowingWarn(name.data(), name.sourcePos()));

      return shadowNameProblems.concat(invalidNameProblems);
    }
  }
}
