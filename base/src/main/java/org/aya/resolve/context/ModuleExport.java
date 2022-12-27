// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.context;

import kala.collection.Seq;
import kala.collection.SeqView;
import kala.collection.SetView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.collection.mutable.MutableMap;
import kala.control.Option;
import org.aya.concrete.stmt.UseHide;
import org.aya.ref.AnyVar;
import org.aya.resolve.error.NameProblem;
import org.aya.util.error.WithPos;
import org.aya.util.reporter.Problem;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A data class that contains all public definitions/re-exports of some module.
 */
public record ModuleExport(@NotNull MutableMap<String, AnyVar> exports) {
  public ModuleExport() {
    this(MutableMap.create());
  }

  /**
   * @return false if failed
   */
  public boolean export(@NotNull String name, @NotNull AnyVar ref) {
    var exists = putIfAbsent(name, ref);
    return exists.isEmpty();
  }

  public boolean exportAnyway(@NotNull String name, @NotNull AnyVar ref) {
    var exists = put(name, ref);
    return exists.isEmpty();
  }

  /**
   * @return Ok if all the names are valid, and returns the new {@link ModuleExport};
   * Err if some names are invalid.
   */
  @Contract(pure = true)
  public @NotNull Result filter(@NotNull ImmutableSeq<WithPos<String>> names, boolean isUse) {
    var oldExports = exports();
    var newExports = MutableMap.<String, AnyVar>create();
    var badNames = MutableList.<WithPos<String>>create();

    if (isUse) {
      // use names
      names.forEach(name -> {
        var def = oldExports.getOption(name.data());
        if (def.isDefined()) {
          newExports.put(name.data(), def.get());
        } else {
          badNames.append(name);
        }
      });
    } else {
      // hide names
      newExports.putAll(oldExports);

      names.forEach(name -> {
        // remove slowly
        var def = newExports.remove(name.data());
        if (def.isEmpty()) {
          // 😱 not defined!!
          badNames.append(name);
        }
      });
    }

    return new Result(new ModuleExport(newExports), badNames.toImmutableSeq(), ImmutableSeq.empty());
  }

  @Contract(pure = true)
  public @NotNull Result map(@NotNull Seq<WithPos<UseHide.Rename>> mapper) {
    var oldExports = exports();
    var newExports = MutableMap.from(oldExports);
    var badNames = MutableList.<WithPos<String>>create();
    var ambigNames = MutableList.<WithPos<String>>create();

    mapper.forEach(pair -> {
      var pos = pair.sourcePos();
      var from = pair.data().from();
      var to = pair.data().to();
      if (from.equals(to)) return;

      var ref = oldExports.getOption(from);
      var ambig = newExports.getOption(to).isDefined();

      if (ref.isDefined()) {
        // k didn't process ->
        //   oldExports has k <-> newExports has k
        //   /\ oldExports[k] = newExports[k]

        // If there is an export with name v, ambiguous!
        if (ambig) {
          ambigNames.append(new WithPos<>(pos, to));
        }

        // we still perform the ambiguous `as`
        newExports.remove(from);
        newExports.put(to, ref.get());
      } else {
        // not defined, not good
        badNames.append(new WithPos<>(pos, from));
      }
    });

    return new Result(new ModuleExport(newExports), badNames.toImmutableSeq(), ambigNames.toImmutableSeq());
  }

  /// region API Adapter

  public @NotNull AnyVar get(@NotNull String key) {
    return exports().get(key);
  }

  public @Nullable AnyVar getOrNull(@NotNull String key) {
    return exports().getOrNull(key);
  }

  public @NotNull Option<AnyVar> getOption(@NotNull String key) {
    return exports().getOption(key);
  }

  public boolean containsKey(@NotNull String key) {
    return exports().containsKey(key);
  }

  public @NotNull SetView<String> keysView() {
    return exports().keysView();
  }

  public @NotNull Option<AnyVar> put(@NotNull String key, @NotNull AnyVar value) {
    return exports().put(key, value);
  }

  public @NotNull Option<AnyVar> putIfAbsent(@NotNull String key, @NotNull AnyVar value) {
    return exports().putIfAbsent(key, value);
  }

  /// endregion

  public record Result(
    @NotNull ModuleExport result,
    @NotNull ImmutableSeq<WithPos<String>> invalidNames,
    @NotNull ImmutableSeq<WithPos<String>> ambiguousNames) {
    public boolean anyError() {
      return invalidNames().isNotEmpty();
    }

    public boolean anyWarn() {
      return ambiguousNames().isNotEmpty();
    }

    public SeqView<Problem> problems(@NotNull ImmutableSeq<String> modName) {
      SeqView<Problem> invalidNameProblems = invalidNames().view()
        .map(name -> new NameProblem.QualifiedNameNotFoundError(modName, name.data(), name.sourcePos()));

      SeqView<Problem> ambiguousNameProblems = ambiguousNames().view()
        .map(name -> new NameProblem.AmbiguousNameWarn(name.data(), name.sourcePos()));

      return invalidNameProblems.concat(ambiguousNameProblems);
    }
  }
}
