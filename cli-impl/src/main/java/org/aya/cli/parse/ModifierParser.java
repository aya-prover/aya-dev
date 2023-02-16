// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.parse;

import kala.collection.Seq;
import kala.collection.SeqLike;
import kala.collection.immutable.ImmutableMap;
import kala.collection.immutable.ImmutableSeq;
import kala.control.Option;
import kala.function.Functions;
import org.aya.cli.parse.error.ContradictModifierError;
import org.aya.cli.parse.error.DuplicatedModifierWarn;
import org.aya.cli.parse.error.NotSuitableModifierError;
import org.aya.concrete.stmt.Stmt;
import org.aya.concrete.stmt.decl.DeclInfo;
import org.aya.generic.util.InternalException;
import org.aya.util.error.SourcePos;
import org.aya.util.error.WithPos;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.Contract;
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
    Personality,
    Alpha
  }

  public enum Modifier {
    // Common Modifiers
    Private(ModifierGroup.Accessibility, "private"),
    Example(ModifierGroup.Personality, "example", Private),
    Counterexample(ModifierGroup.Personality, "counterexample", Private),

    // ModuleLike Modifiers
    Open(ModifierGroup.None, "open"),

    // Function Modifiers
    Opaque(ModifierGroup.Alpha, "opaque"),
    Inline(ModifierGroup.Alpha, "inline"),
    Overlap(ModifierGroup.None, "overlap");

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

  /**
   * @param defaultValue Forall (x : Modifier), filter x = true -> ((defaultValue x) success)
   */
  public record Filter(
    @NotNull Modifiers defaultValue,
    @NotNull Predicate<Modifier> filter
  ) {}

  public interface Modifiers {
    Filter declFilter = ofDefault(
      new WithPos<>(SourcePos.NONE, Stmt.Accessibility.Public),
      new WithPos<>(SourcePos.NONE, DeclInfo.Personality.NORMAL),
      Option.none(), null, null, null
    );

    Filter fnFilter = ofDefault(
      new WithPos<>(SourcePos.NONE, Stmt.Accessibility.Public),
      new WithPos<>(SourcePos.NONE, DeclInfo.Personality.NORMAL),
      null, Option.none(), Option.none(), Option.none()
    );

    Filter subDeclFilter = new Filter(ofDefault(
      new WithPos<>(SourcePos.NONE, Stmt.Accessibility.Public),
      new WithPos<>(SourcePos.NONE, DeclInfo.Personality.NORMAL),
      null, null, null, null
    ).defaultValue, x -> false);

    /**
     * Forall parameters:<br/>
     * null implies not allowed,<br/>
     * none implies no default value,<br/>
     * some implies yes default value<br/>
     */
    static @NotNull Filter ofDefault(
      @Nullable WithPos<Stmt.Accessibility> accessibility,
      @Nullable WithPos<DeclInfo.Personality> personality,
      @Nullable Option<SourcePos> openKw,
      @Nullable Option<SourcePos> opaque,
      @Nullable Option<SourcePos> inline,
      @Nullable Option<SourcePos> overlap
    ) {
      Predicate<Modifier> predi = (modi) -> switch (modi) {
        case Private -> accessibility != null;
        case Example, Counterexample -> personality != null;
        case Open -> openKw != null;
        case Opaque -> opaque != null;
        case Inline -> inline != null;
        case Overlap -> overlap != null;
      };

      return new Filter(new Modifiers() {
        @Override
        public @NotNull WithPos<Stmt.Accessibility> accessibility() {
          if (accessibility == null) throw new UnsupportedOperationException();
          return accessibility;
        }

        @Override
        public @NotNull WithPos<DeclInfo.Personality> personality() {
          if (personality == null) throw new UnsupportedOperationException();
          return personality;
        }

        @Override
        public @Nullable SourcePos openKw() {
          if (openKw == null) throw new UnsupportedOperationException();
          return openKw.getOrNull();
        }

        @Override
        public @Nullable SourcePos opaque() {
          if (opaque == null) throw new UnsupportedOperationException();
          return opaque.getOrNull();
        }

        @Override
        public @Nullable SourcePos inline() {
          if (inline == null) throw new UnsupportedOperationException();
          return inline.getOrNull();
        }

        @Override
        public @Nullable SourcePos overlap() {
          if (overlap == null) throw new UnsupportedOperationException();
          return overlap.getOrNull();
        }
      }, predi);
    }

    /**
     * @throws UnsupportedOperationException
     */
    @Contract(pure = true)
    @NotNull WithPos<Stmt.Accessibility> accessibility();
    @Contract(pure = true)
    @NotNull WithPos<DeclInfo.Personality> personality();
    @Contract(pure = true)
    @Nullable SourcePos openKw();
    @Contract(pure = true)
    @Nullable SourcePos opaque();
    @Contract(pure = true)
    @Nullable SourcePos inline();
    @Contract(pure = true)
    @Nullable SourcePos overlap();
  }

  private record ModifierSet(
    @NotNull ImmutableMap<Modifier, SourcePos> mods,
    @NotNull Modifiers parent
  ) implements Modifiers {
    @Override
    @Contract(pure = true)
    public @NotNull WithPos<Stmt.Accessibility> accessibility() {
      return mods.getOption(Modifier.Private)
        .map(x -> new WithPos<>(SourcePos.NONE, Stmt.Accessibility.Private))
        .getOrElse(parent::accessibility);
    }

    @Override
    @Contract(pure = true)
    public @NotNull WithPos<DeclInfo.Personality> personality() {
      return mods.getOption(Modifier.Example)
        .map(x -> new WithPos<>(SourcePos.NONE, DeclInfo.Personality.EXAMPLE))
        .getOrElse(() -> mods.getOption(Modifier.Counterexample)
          .map(x -> new WithPos<>(SourcePos.NONE, DeclInfo.Personality.COUNTEREXAMPLE))
          .getOrElse(parent::personality));
    }

    @Override
    @Contract(pure = true)
    public @Nullable SourcePos openKw() {
      return mods.getOrElse(Modifier.Open, parent::openKw);
    }

    @Override
    @Contract(pure = true)
    public @Nullable SourcePos opaque() {
      return mods.getOrElse(Modifier.Opaque, parent::opaque);
    }

    @Override
    @Contract(pure = true)
    public @Nullable SourcePos inline() {
      return mods.getOrElse(Modifier.Inline, parent::inline);
    }

    @Override
    @Contract(pure = true)
    public @Nullable SourcePos overlap() {
      return mods.getOrElse(Modifier.Overlap, parent::overlap);
    }
  }

  @TestOnly public @NotNull Modifiers parse(@NotNull ImmutableSeq<WithPos<Modifier>> modifiers) {
    var filter = Modifiers.ofDefault(
      new WithPos<>(SourcePos.NONE, Stmt.Accessibility.Public),
      new WithPos<>(SourcePos.NONE, DeclInfo.Personality.NORMAL),
      Option.none(), Option.none(), Option.none(), Option.none()
    );
    return parse(modifiers, filter);
  }

  private @NotNull ImmutableSeq<WithPos<Modifier>> implication(@NotNull SeqLike<WithPos<Modifier>> modifiers) {
    var result = modifiers
      .flatMap(modi -> Seq.from(modi.data().implies)
        .map(imply -> new WithPos<>(modi.sourcePos(), imply)))
      .collect(Collectors.toUnmodifiableMap(WithPos::data, Functions.identity(), (a, b) -> a))
      .values();

    return ImmutableSeq.from(result);
  }

  /**
   * @param filter The filter also performs on the modifiers that expanded from input.
   */
  public @NotNull Modifiers parse(@NotNull ImmutableSeq<WithPos<Modifier>> modifiers, @NotNull Filter filter) {
    EnumMap<ModifierGroup, EnumMap<Modifier, SourcePos>> map = new EnumMap<>(ModifierGroup.class);

    modifiers = implication(modifiers).concat(modifiers);

    // parsing
    for (var data : modifiers) {
      var pos = data.sourcePos();
      var modifier = data.data();

      // do filter
      if (!filter.filter.test(data.data())) {
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

    return new ModifierSet(ImmutableMap.from(
      ImmutableSeq.from(map.values()).flatMap(EnumMap::entrySet)
    ), filter.defaultValue);
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
