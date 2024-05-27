// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.context;

import kala.collection.Seq;
import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.collection.mutable.MutableMap;
import kala.control.Result;
import kala.value.primitive.MutableBooleanValue;
import org.aya.resolve.error.NameProblem;
import org.aya.syntax.concrete.stmt.ModuleName;
import org.aya.syntax.concrete.stmt.QualifiedID;
import org.aya.syntax.concrete.stmt.UseHide;
import org.aya.syntax.ref.DefVar;
import org.aya.util.error.WithPos;
import org.aya.util.reporter.Problem;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * A data class that contains all public definitions/re-exports of some module.
 */
public record ModuleExport(
  @NotNull ModuleSymbol<DefVar<?, ?>> symbols,
  @NotNull MutableMap<ModuleName.Qualified, ModuleExport> modules
) {
  public ModuleExport() {
    this(new ModuleSymbol<>(), MutableMap.create());
  }

  public ModuleExport(@NotNull ModuleExport that) {
    this(new ModuleSymbol<>(that.symbols), MutableMap.from(that.modules));
  }

  @Contract(pure = true)
  @NotNull ExportResult filter(@NotNull ImmutableSeq<QualifiedID> names, UseHide.Strategy strategy) {
    ModuleExport newModule;
    var badNames = MutableList.<QualifiedID>create();
    var ambiNames = MutableList.<WithPos<String>>create();

    switch (strategy) {
      case Using -> {
        newModule = new ModuleExport();
        names.forEach(qname -> {
          var unit = getMaybe(qname.component(), qname.name());

          if (unit.isOk()) unit.get().forEach(
            symbol -> newModule.export(qname.component(), qname.name(), symbol),
            module -> newModule.export(qname.component().resolve(qname.name()), module)
          );
          else switch (unit.getErr()) {
            case NotFound -> badNames.append(qname);
            case Ambiguous -> ambiNames.append(new WithPos<>(qname.sourcePos(), qname.name()));
          }
        });
      }
      case Hiding -> {
        var aNewModule = new ModuleExport(this);
        newModule = aNewModule;

        names.forEach(qname -> {
          var oldUnit = aNewModule.removeMaybe(qname.component(), qname.name());

          switch (oldUnit.getErrOrNull()) {
            case NotFound -> badNames.append(qname);
            case Ambiguous -> ambiNames.append(new WithPos<>(qname.sourcePos(), qname.name()));
            case null -> { }
          }
        });
      }
      default -> throw new AssertionError("I mean, this case is impossible.");
    }

    var hasError = badNames.isNotEmpty() || ambiNames.isNotEmpty();

    return new ExportResult(
      hasError ? this : newModule,
      badNames.toImmutableSeq(),
      ambiNames.toImmutableSeq(),
      ImmutableSeq.empty());
  }

  @Contract(pure = true)
  @NotNull ExportResult map(@NotNull Seq<WithPos<UseHide.Rename>> mapper) {
    var newExport = new ModuleExport(this);
    var badNames = MutableList.<QualifiedID>create();
    var ambiNames = MutableList.<WithPos<String>>create();
    var shadowNames = MutableList.<WithPos<String>>create();

    mapper.forEach(pair -> {
      var pos = pair.sourcePos();
      var fromModule = ModuleName.from(pair.data().fromModule());
      var fromName = pair.data().name();
      var to = pair.data().to();
      if (fromModule == ModuleName.This && fromName.equals(to)) return;

      var thing = newExport.removeMaybe(fromModule, fromName);
      if (thing.isOk()) {
        var reportShadow = MutableBooleanValue.create(false);

        thing.get().forEach(symbol -> {
            var candidates = newExport.symbols.resolveUnqualified(to).asMut().get();
            var isShadow = candidates.isNotEmpty();
            // If there is an export with name `to`, shadow!
            if (isShadow) {
              // do clear
              candidates.clear();
              reportShadow.set(true);
            }

            // now, {candidates} is empty
            candidates.put(ModuleName.This, symbol);
          }, module -> {
            var isShadow = newExport.modules.containsKey(new ModuleName.Qualified(to));

            if (isShadow) {
              reportShadow.set(true);
            }

            newExport.modules.put(new ModuleName.Qualified(to), module);
          }
        );

        if (reportShadow.get()) {
          shadowNames.append(new WithPos<>(pos, to));
        }
      } else {
        switch (thing.getErr()) {
          case NotFound -> badNames.append(new QualifiedID(pos, fromModule, fromName));
          case Ambiguous -> ambiNames.append(new WithPos<>(pos, fromName));
        }
      }
    });

    var hasError = badNames.isNotEmpty() || ambiNames.isNotEmpty();

    return new ExportResult(
      hasError ? this : newExport,
      badNames.toImmutableSeq(),
      ambiNames.toImmutableSeq(),
      shadowNames.toImmutableSeq()
    );
  }

  /**
   * @return false if there already exist a symbol with the same name.
   */
  public boolean export(@NotNull ModuleName modName, @NotNull String name, @NotNull DefVar<?, ?> ref) {
    var exists = symbols.add(modName, name, ref);
    return exists.isEmpty();
  }

  public boolean export(@NotNull ModuleName.Qualified componentName, @NotNull ModuleExport module) {
    var exists = modules.put(componentName, module);
    return exists.isEmpty();
  }

  /// region Helper Methods for Mapping/Filtering

  private Result<ExportUnit, ModuleSymbol.Error> getMaybe(@NotNull ModuleName component, @NotNull String name) {
    var symbol = symbols.getMaybe(component, name);
    var module = modules.getOption(component.resolve(name));      // `getOption` for beauty

    if (symbol.getErrOrNull() == ModuleSymbol.Error.Ambiguous)
      return Result.err(ModuleSymbol.Error.Ambiguous);
    if (symbol.isEmpty() && module.isEmpty()) return Result.err(ModuleSymbol.Error.NotFound);

    return Result.ok(new ExportUnit(symbol.getOrNull(), module.getOrNull()));
  }

  private Result<ExportUnit, ModuleSymbol.Error> removeMaybe(@NotNull ModuleName component, @NotNull String name) {
    var symbol = symbols.removeDefinitely(component, name);
    if (symbol.getErrOrNull() == ModuleSymbol.Error.Ambiguous) return Result.err(ModuleSymbol.Error.Ambiguous);

    var module = modules.remove(component.resolve(name));
    // symbol.isEmpty <-> symbol.getErr() == NotFound
    if (symbol.isEmpty() && module.isEmpty()) return Result.err(ModuleSymbol.Error.NotFound);

    return Result.ok(new ExportUnit(symbol.getOrNull(), module.getOrNull()));
  }

  private record ExportUnit(@Nullable DefVar<?, ?> symbol, @Nullable ModuleExport module) {
    public ExportUnit {
      assert symbol != null || module != null : "Sanity check";
    }

    public void forEach(Consumer<DefVar<?, ?>> symbolConsumer, Consumer<ModuleExport> moduleConsumer) {
      if (symbol != null) symbolConsumer.accept(symbol);
      if (module != null) moduleConsumer.accept(module);
    }
  }

  /// endregion

  /**
   * @param result         the new module export if success, the old module export if failed.
   * @param ambiguousNames Ambiguous always occurs with unqualified name, so it is {@link String} instead of {@link QualifiedID}
   */
  record ExportResult(
    @NotNull ModuleExport result,
    @NotNull ImmutableSeq<QualifiedID> invalidNames,
    @NotNull ImmutableSeq<WithPos<String>> ambiguousNames,
    @NotNull ImmutableSeq<WithPos<String>> shadowNames
  ) {
    public boolean anyError() {
      return invalidNames().isNotEmpty() || ambiguousNames().isNotEmpty();
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

      SeqView<Problem> ambiguousNameProblems = ambiguousNames().view()
        .map(name -> {
          var old = result();
          var disambi = old.symbols().resolveUnqualified(name.data()).moduleNames();
          return new NameProblem.AmbiguousNameError(name.data(), disambi, name.sourcePos());
        });

      SeqView<Problem> shadowNameProblems = shadowNames().view()
        .map(name -> new NameProblem.ShadowingWarn(name.data(), name.sourcePos()));

      return shadowNameProblems.concat(invalidNameProblems).concat(ambiguousNameProblems);
    }
  }
}
