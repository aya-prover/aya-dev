// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.parse;

import kala.collection.Seq;
import kala.collection.SeqLike;
import kala.collection.immutable.ImmutableSeq;
import kala.function.Functions;
import org.aya.cli.parse.error.ContradictModifierError;
import org.aya.cli.parse.error.DuplicatedModifierWarn;
import org.aya.cli.parse.error.NotSuitableModifierError;
import org.aya.concrete.stmt.DeclInfo;
import org.aya.concrete.stmt.Stmt;
import org.aya.generic.util.InternalException;
import org.aya.util.error.SourcePos;
import org.aya.util.error.WithPos;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.EnumMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public record ModifierParser(@NotNull Reporter reporter) {
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
    @Nullable SourcePos openKw) {
  }

  @TestOnly public @NotNull ModifierSet parse(@NotNull ImmutableSeq<WithPos<Modifier>> modifiers) {
    return parse(modifiers, x -> true);
  }

  private @NotNull ImmutableSeq<WithPos<Modifier>> implication(@NotNull SeqLike<WithPos<Modifier>> modifiers) {
    var result = modifiers
      .flatMap(modi -> Seq.from(modi.data().implies)
        .map(imply -> new WithPos<>(modi.sourcePos(), imply)))
      .collect(Collectors.toMap(WithPos::data, Functions.identity()))
      .values();

    return ImmutableSeq.from(result);
  }

  /**
   * @param filter The filter also performs on the modifiers that expanded from input.
   */
  public @NotNull ModifierSet parse(@NotNull ImmutableSeq<WithPos<Modifier>> modifiers, @NotNull Predicate<Modifier> filter) {
    EnumMap<ModifierGroup, EnumMap<Modifier, SourcePos>> map = new EnumMap<>(ModifierGroup.class);

    modifiers = implication(modifiers).concat(modifiers);

    // parsing
    for (var data : modifiers) {
      var pos = data.sourcePos();
      var modifier = data.data();

      // do filter
      if (!filter.test(data.data())) {
        reportUnsuitableModifier(data);
        continue;
      }

      map.computeIfAbsent(modifier.group, k -> new EnumMap<>(Modifier.class));
      var exists = map.get(modifier.group);

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

      // no contradict modifier, no redundant modifier, everything is good
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
    var openKw = noneGroup != null ? noneGroup.get(Modifier.Open) : null;

    return new ModifierSet(acc, pers, openKw);
  }

  public void reportUnsuitableModifier(@NotNull WithPos<Modifier> data) {
    reporter.report(new NotSuitableModifierError(data.sourcePos(), data.data()));
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
