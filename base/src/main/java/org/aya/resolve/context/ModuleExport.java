// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.context;

import kala.collection.Seq;
import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import org.aya.concrete.stmt.QualifiedID;
import org.aya.concrete.stmt.UseHide;
import org.aya.ref.DefVar;
import org.aya.resolve.error.NameProblem;
import org.aya.util.error.WithPos;
import org.aya.util.reporter.Problem;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * A data class that contains all public definitions/re-exports of some module.
 */
public record ModuleExport(@NotNull ModuleSymbol<DefVar<?, ?>> symbols) {
  public ModuleExport() {
    this(new ModuleSymbol<>());
  }

  @Contract(pure = true)
  @NotNull ExportResult filter(@NotNull ImmutableSeq<QualifiedID> names, UseHide.Strategy strategy) {
    var oldSymbols = symbols();
    ModuleSymbol<DefVar<?, ?>> newSymbols;
    var badNames = MutableList.<QualifiedID>create();
    var ambiNames = MutableList.<WithPos<String>>create();

    switch (strategy) {
      case Using -> {
        newSymbols = new ModuleSymbol<>();
        names.forEach(qname -> {
          var def = oldSymbols.getMaybe(qname.component(), qname.name());

          if (def.isOk()) {
            newSymbols.add(qname.component(), qname.name(), def.get());
          } else {
            switch (def.getErr()) {
              case NotFound -> badNames.append(qname);
              case Ambiguous -> ambiNames.append(new WithPos<>(qname.sourcePos(), qname.name()));
            }
          }
        });
      }
      case Hiding -> {
        newSymbols = new ModuleSymbol<>(new ModuleSymbol<>(oldSymbols));

        names.forEach(qname -> {
          var def = newSymbols.removeDefinitely(qname.component(), qname.name());

          if (def.isErr()) {
            switch (def.getErr()) {
              case NotFound -> badNames.append(qname);
              case Ambiguous -> ambiNames.append(new WithPos<>(qname.sourcePos(), qname.name()));
            }
          }
        });
      }
      default -> throw new AssertionError("I mean, this case is impossible.");
    }

    var hasError = badNames.isNotEmpty() || ambiNames.isNotEmpty();

    return new ExportResult(
      hasError ? this : new ModuleExport(newSymbols),
      badNames.toImmutableSeq(),
      ambiNames.toImmutableSeq(),
      ImmutableSeq.empty());
  }

  @Contract(pure = true)
  @NotNull ExportResult map(@NotNull Seq<WithPos<UseHide.Rename>> mapper) {
    var oldSymbols = symbols();
    var newSymbols = new ModuleSymbol<>(oldSymbols);
    var badNames = MutableList.<WithPos<String>>create();
    var ambiNames = MutableList.<WithPos<String>>create();
    var shadowNames = MutableList.<WithPos<String>>create();

    mapper.forEach(pair -> {
      var pos = pair.sourcePos();
      var fromModule = ModulePath.from(pair.data().fromModule());
      var from = pair.data().name();
      var to = pair.data().to();
      if (fromModule == ModulePath.This && from.equals(to)) return;

      var ref = oldSymbols.getMaybe(fromModule, from);
      if (ref.isOk()) {
        // ref didn't process ->
        //   oldSymbols has ref <-> newSymbols has ref
        //   /\ oldSymbols[ref] = newSymbols[ref]

        var candidates = newSymbols.getMut(to);
        var isShadow = candidates.isNotEmpty();
        // If there is an export with name `to`, shadow!
        if (isShadow) {
          // do clear
          candidates.clear();
          shadowNames.append(new WithPos<>(pos, to));
        }

        // remove `from`, should not fail.
        var result = newSymbols.removeDefinitely(fromModule, from);
        assert result.isOk() : "Bug!";
        assert result.get() == ref.get() : "Bug!";

        // now, candidates is empty
        candidates.put(ModulePath.This, ref.get());
      } else {
        // not defined or ambiguous, not good
        switch (ref.getErr()) {
          case NotFound -> badNames.append(new WithPos<>(pos, from));
          case Ambiguous -> ambiNames.append(new WithPos<>(pos, from));
        }
      }
    });

    var hasError = badNames.isNotEmpty() || ambiNames.isNotEmpty();

    return new ExportResult(
      hasError ? this : new ModuleExport(newSymbols),
      badNames.map(x -> new QualifiedID(x.sourcePos(), x.data())),
      ambiNames.toImmutableSeq(),
      shadowNames.toImmutableSeq()
    );
  }

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

    public SeqView<Problem> problems(@NotNull ModulePath modName) {
      SeqView<Problem> invalidNameProblems = invalidNames().view()
        .map(name -> new NameProblem.QualifiedNameNotFoundError(
          modName.concat(name.component()),
          name.name(),
          name.sourcePos()));

      SeqView<Problem> ambiguousNameProblems = ambiguousNames().view()
        .map(name -> {
          var old = result();
          var disambi = old.symbols().resolveUnqualified(name.data()).keysView().map(ModulePath::toImmutableSeq).toImmutableSeq();
          return new NameProblem.AmbiguousNameError(name.data(), ImmutableSeq.narrow(disambi), name.sourcePos());
        });

      SeqView<Problem> shadowNameProblems = shadowNames().view()
        .map(name -> new NameProblem.ShadowingWarn(name.data(), name.sourcePos()));

      return shadowNameProblems.concat(invalidNameProblems).concat(ambiguousNameProblems);
    }
  }

  /**
   * @return false if failed
   */
  public boolean export(@NotNull ModulePath component, @NotNull String name, @NotNull DefVar<?, ?> ref) {
    var exists = symbols.add(component, name, ref);
    return exists.isEmpty();
  }

  public boolean export(@NotNull QualifiedID qualifiedName, @NotNull DefVar<?, ?> ref) {
    return export(qualifiedName.component(), qualifiedName.name(), ref);
  }

  public void exportAnyway(@NotNull ModulePath component, @NotNull String name, @NotNull DefVar<?, ?> ref) {
    symbols.addAnyway(component, name, ref);
  }
}
