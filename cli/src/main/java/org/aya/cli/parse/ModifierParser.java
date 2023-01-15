// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.parse;

import kala.collection.Map;
import kala.collection.Seq;
import kala.collection.SeqLike;
import kala.collection.immutable.ImmutableSeq;
import org.aya.cli.parse.error.ContradictModifierError;
import org.aya.cli.parse.error.DuplicatedModifierWarn;
import org.aya.cli.parse.error.NotSuitableModifierWarn;
import org.aya.concrete.stmt.DeclInfo;
import org.aya.concrete.stmt.Stmt;
import org.aya.generic.util.InternalException;
import org.aya.util.error.SourcePos;
import org.aya.util.error.WithPos;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;

import java.util.EnumMap;
import java.util.function.Predicate;

public class ModifierParser {
  public enum ModifierGroup {
    None,
    Accessibility,
    Personality
  }

  public enum Modifier {
    Private(ModifierGroup.Accessibility, "private"),
    Example(ModifierGroup.Personality, "example", Private),
    Counterexample(ModifierGroup.Personality, "counterexample", Private),
    Open(ModifierGroup.None, "open");

    public final @NotNull ModifierGroup group;
    public final @NotNull String keyword;

    /**
     * {@code implies} will/should expand only once
     */
    public final @NotNull Modifier[] implies;

    Modifier(@NotNull ModifierGroup group, @NotNull String keyword, @NotNull Modifier... implies) {
      this.group = group;
      this.keyword = keyword;
      this.implies = implies;
    }
  }

  public record ModifierSet(
    @NotNull WithPos<Stmt.Accessibility> accessibility,
    @NotNull WithPos<DeclInfo.Personality> personality,
    @NotNull WithPos<Boolean> isOpen) {
  }

  public final @NotNull Reporter reporter;

  public ModifierParser(@NotNull Reporter reporter) {
    this.reporter = reporter;
  }

  public @NotNull ModifierSet parse(@NotNull SeqLike<WithPos<Modifier>> modifiers) {
    return parse(modifiers, x -> true);
  }

  private @NotNull ImmutableSeq<WithPos<Modifier>> implication(@NotNull SeqLike<WithPos<Modifier>> modifiers) {
    EnumMap<Modifier, SourcePos> map = new EnumMap<>(Modifier.class);

    for (var modi : modifiers) {
      for (var implies : modi.data().implies) {
        map.putIfAbsent(implies, modi.sourcePos());
      }
    }

    return Map.from(map).view()
      .map((modi, pos) -> new WithPos<>(pos, modi)).toImmutableSeq();
  }

  /**
   * @param filter The filter also perform on the modifiers that expanded from input.
   */
  public @NotNull ModifierSet parse(@NotNull SeqLike<WithPos<Modifier>> modifiers, @NotNull Predicate<Modifier> filter) {
    EnumMap<ModifierGroup, EnumMap<Modifier, SourcePos>> map = new EnumMap<>(ModifierGroup.class);

    var inserts = implication(modifiers);
    modifiers = inserts.concat(modifiers);

    // parsing
    for (var data : modifiers) {
      if (!filter.test(data.data())) {
        reportUnsuitableModifier(data);
        continue;
      }

      var pos = data.sourcePos();
      var modifier = data.data();

      // getOrPut
      var exists = map.getOrDefault(modifier.group, new EnumMap<>(Modifier.class));
      map.putIfAbsent(modifier.group, exists);

      if (exists.containsKey(modifier)) {
        reportDuplicatedModifier(data);
        continue;
      }

      if (modifier.group != ModifierGroup.None
        && !exists.isEmpty()
        // In fact, this boolean expression is always true
        && !exists.containsKey(modifier)) {
        // one (not None) group one modifier
        assert exists.size() == 1;
        var contradict = Seq.from(exists.entrySet()).first();
        reportContradictModifier(data, new WithPos<>(contradict.getValue(), contradict.getKey()));
        continue;
      }

      // no contradict modifier, no duplicate modifier, everything is fine
      exists.put(modifier, pos);
    }

    // accessibility
    var accGroup = map.get(ModifierGroup.Accessibility);
    WithPos<Stmt.Accessibility> acc;

    if (accGroup != null && !accGroup.isEmpty()) {
      var entry = accGroup.entrySet().iterator().next();
      Stmt.Accessibility key = switch (entry.getKey()) {
        case Private -> Stmt.Accessibility.Private;
        default -> unreachable();
      };

      acc = new WithPos<>(entry.getValue(), key);
    } else acc = new WithPos<>(SourcePos.NONE, Stmt.Accessibility.Public);

    // personality
    var persGroup = map.get(ModifierGroup.Personality);
    WithPos<DeclInfo.Personality> pers;

    if (persGroup != null && !persGroup.isEmpty()) {
      var entry = persGroup.entrySet().iterator().next();
      DeclInfo.Personality key = switch (entry.getKey()) {
        case Example -> DeclInfo.Personality.EXAMPLE;
        case Counterexample -> DeclInfo.Personality.COUNTEREXAMPLE;
        default -> unreachable();
      };

      pers = new WithPos<>(entry.getValue(), key);
    } else pers = new WithPos<>(SourcePos.NONE, DeclInfo.Personality.NORMAL);

    // others
    var noneGroup = map.get(ModifierGroup.None);
    WithPos<Boolean> isReExport = new WithPos<>(SourcePos.NONE, false);

    if (noneGroup != null) {
      var open = noneGroup.get(Modifier.Open);
      if (open != null) isReExport = new WithPos<>(open, true);
    }

    return new ModifierSet(acc, pers, isReExport);
  }

  public void reportUnsuitableModifier(@NotNull WithPos<Modifier> data) {
    reporter.report(new NotSuitableModifierWarn(data.sourcePos(), data.data()));
  }

  public void reportDuplicatedModifier(@NotNull WithPos<Modifier> data) {
    reporter.report(new DuplicatedModifierWarn(data.sourcePos(), data.data()));
  }

  public void reportContradictModifier(@NotNull WithPos<Modifier> current, @NotNull WithPos<Modifier> that) {
    reporter.report(new ContradictModifierError(current.sourcePos(), current.data()));
  }

  public <T> T unreachable() {
    throw new InternalException("🪲");
  }
}
