// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.telescope;

import kala.collection.ArraySeq;
import kala.collection.Seq;
import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.immutable.primitive.ImmutableIntSeq;
import kala.collection.mutable.FreezableMutableList;
import kala.collection.mutable.MutableList;
import kala.collection.mutable.MutableSeq;
import kala.range.primitive.IntRange;
import org.aya.generic.term.DTKind;
import org.aya.generic.term.ParamLike;
import org.aya.syntax.core.Closure;
import org.aya.syntax.core.RichParam;
import org.aya.syntax.core.term.DepTypeTerm;
import org.aya.syntax.core.term.FreeTerm;
import org.aya.syntax.core.term.Param;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.ref.GenerateKind;
import org.aya.syntax.ref.LocalVar;
import org.aya.util.Panic;
import org.aya.util.position.SourcePos;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.function.BiFunction;

/// Index-safe telescope
public interface AbstractTele {
  /// Replace [org.aya.syntax.core.term.FreeTerm] in {@param tele} with appropriate index
  ///
  /// @implNote it will call [Seq#sliceView] several times on {@param binds} so it's not a good idea to
  /// take it as a view.
  @Contract(mutates = "param2")
  static void bindTele(Seq<LocalVar> binds, MutableSeq<Param> tele) {
    tele.replaceAllIndexed((i, p) -> p.descent(t -> t.bindTele(binds.sliceView(0, i))));
  }

  static @NotNull ImmutableSeq<ParamLike<Term>> enrich(@NotNull AbstractTele tele) {
    var richTele = FreezableMutableList.<ParamLike<Term>>create();

    for (var i = 0; i < tele.telescopeSize(); ++i) {
      var binds = richTele.<Term>map(x -> new FreeTerm(x.ref()));
      var type = tele.telescope(i, binds);
      richTele.append(new RichParam(
        new LocalVar(tele.telescopeName(i), SourcePos.NONE, GenerateKind.Basic.Pretty),
        type,
        tele.telescopeLicit(i))
      );
    }

    return richTele.toSeq();
  }

  /// @param teleArgs the arguments before {@param i}, for constructor, it also contains the arguments to the data
  default @NotNull Term telescope(int i, Term[] teleArgs) {
    return telescope(i, ArraySeq.wrap(teleArgs));
  }

  /// Get the type of {@param i}-th (count from `0`) parameter.
  /// The default implementation is for empty telescope, because there are many such cases.
  ///
  /// @param teleArgs the arguments to the former parameters
  /// @return the type of {@param i}-th parameter.
  default @NotNull Term telescope(int i, Seq<Term> teleArgs) {
    return Panic.unreachable();
  }

  /// Get the result of this signature
  ///
  /// @param teleArgs the arguments to all parameters.
  @NotNull Term result(Seq<Term> teleArgs);

  /// Return the amount of parameters.
  int telescopeSize();

  /// Return the licit of {@param i}-th parameter.
  ///
  /// @return true if explicit
  boolean telescopeLicit(int i);

  /// Get the name of {@param i}-th parameter.
  @NotNull String telescopeName(int i);

  /// Get all information of {@param i}-th parameter
  ///
  /// @see #telescope
  /// @see #telescopeName
  /// @see #telescopeLicit
  default @NotNull Param telescopeRich(int i, Term... teleArgs) {
    return new Param(telescopeName(i), telescope(i, teleArgs), telescopeLicit(i));
  }

  default @NotNull Term result(Term... teleArgs) {
    return result(ArraySeq.wrap(teleArgs));
  }

  default @NotNull SeqView<String> namesView() {
    return ImmutableIntSeq.from(IntRange.closedOpen(0, telescopeSize()))
      .view().mapToObj(this::telescopeName);
  }

  default @NotNull Term makePi() {
    return makePi(Seq.empty());
  }

  default @NotNull Term makePi(@NotNull Seq<Term> initialArgs) {
    return new PiBuilder(this).make(0, initialArgs);
  }

  record PiBuilder(AbstractTele telescope) {
    public @NotNull Term make(int i, Seq<Term> args) {
      return i == telescope.telescopeSize() ? telescope.result(args) :
        new DepTypeTerm(DTKind.Pi, telescope.telescope(i, args), new Closure.Jit(arg ->
          make(i + 1, args.appended(arg))));
    }
  }

  default @NotNull AbstractTele lift(int i) { return i == 0 ? this : new Lift(this, i); }

  public record VarredParam(@NotNull LocalVar var, @NotNull Param type) {}

  /// Default implementation of {@link AbstractTele}
  ///
  /// @param telescope bound parameters, that is, the later parameter can refer to the former parameters
  ///                                  by {@link org.aya.syntax.core.term.LocalTerm}
  /// @param result    bound result
  record Locns(@NotNull ImmutableSeq<Param> telescope, @NotNull Term result) implements AbstractTele {
    @Override public int telescopeSize() { return telescope.size(); }
    @Override public boolean telescopeLicit(int i) { return telescope.get(i).explicit(); }
    @Override public @NotNull String telescopeName(int i) { return telescope.get(i).name(); }
    @Override public @NotNull Term telescope(int i, Seq<Term> teleArgs) {
      return telescope.get(i).type().instTele(teleArgs.sliceView(0, i));
    }
    @Override public @NotNull Term result(Seq<Term> teleArgs) { return result.instTele(teleArgs.view()); }
    @Override public @NotNull SeqView<String> namesView() {
      return telescope.view().map(Param::name);
    }

    public @NotNull Locns bind(@NotNull LocalVar var, @NotNull Param type) {
      var boundTele = telescope.view().mapIndexed((idx, p) -> p.descent(t -> t.bindAt(var, idx)));
      return new Locns(boundTele.prepended(type).toSeq(), result.bindAt(var, telescope.size()));
    }

    public @NotNull Locns bindTele(@NotNull SeqView<VarredParam> tele) {
      return tele.foldRight(this, (pair, acc) ->
        acc.bind(pair.var, pair.type));
    }

    // public @NotNull Locns drop(int count) {
    //   assert count <= telescopeSize();
    //   return new Locns(telescope.drop(count), result);
    // }

    @Override public @NotNull Locns inst(ImmutableSeq<Term> preArgs) {
      if (preArgs.isEmpty()) return this;
      assert preArgs.size() <= telescopeSize();
      var view = preArgs.view();
      var cope = telescope.view()
        .drop(preArgs.size())
        .mapIndexed((idx, p) -> p.descent(t -> t.instTeleFrom(idx, view)))
        .toSeq();
      var result = this.result.instTeleFrom(cope.size(), view);
      return new Locns(cope, result);
    }

    /// Perform {@param mapper} on each parameters and result.
    ///
    /// @param mapper accept a sequence of names of previous parameters and an instantiated type.
    /// @implNote By checking `vars.sizeEquals(telescopeSize())` to tell if it is the result
    @NotNull Locns map(BiFunction<SeqView<LocalVar>, Term, Term> mapper) {
      var vars = MutableList.<LocalVar>create();
      var newTele = MutableList.<Param>create();

      for (var param : telescope) {
        var freeType = param.type().instTeleVar(vars.view());
        newTele.append(param.update(mapper.apply(vars.view(), freeType)));
        vars.append(LocalVar.generate(param.name()));
      }

      // vars.size == telescopeSize
      var freeResult = result.instTeleVar(vars.view());
      var newResult = mapper.apply(vars.view(), freeResult);

      var boundResult = newResult.bindTele(vars.view());
      AbstractTele.bindTele(vars, newTele);
      return new Locns(newTele.toSeq(), boundResult);
    }
  }

  record Lift(
    @NotNull AbstractTele signature,
    int lift
  ) implements AbstractTele {
    @Override public int telescopeSize() { return signature.telescopeSize(); }
    @Override public boolean telescopeLicit(int i) { return signature.telescopeLicit(i); }
    @Override public @NotNull String telescopeName(int i) { return signature.telescopeName(i); }
    @Override public @NotNull Term telescope(int i, Seq<Term> teleArgs) {
      return signature.telescope(i, teleArgs).elevate(lift);
    }
    @Override public @NotNull Term result(Seq<Term> teleArgs) {
      return signature.result(teleArgs).elevate(lift);
    }
    @Override public @NotNull AbstractTele lift(int i) { return new Lift(signature, lift + i); }
    @Override public @NotNull SeqView<String> namesView() { return signature.namesView(); }
  }

  default @NotNull AbstractTele inst(ImmutableSeq<Term> preArgs) {
    if (preArgs.isEmpty()) return this;
    return new Inst(this, preArgs);
  }

  /// Apply first {@code args.size()} parameters with {@param args} of {@param signature}
  record Inst(
    @NotNull AbstractTele signature,
    @NotNull ImmutableSeq<Term> args
  ) implements AbstractTele {
    @Override public @NotNull Term telescope(int i, Seq<Term> teleArgs) {
      return signature.telescope(i + args.size(), args.appendedAll(teleArgs));
    }

    @Override public @NotNull Term result(Seq<Term> teleArgs) {
      return signature.result(args.appendedAll(teleArgs));
    }
    @Override public int telescopeSize() { return signature.telescopeSize() - args.size(); }
    @Override public boolean telescopeLicit(int i) { return signature.telescopeLicit(i + args.size()); }
    @Override public @NotNull String telescopeName(int i) { return signature.telescopeName(i + args.size()); }
  }
}
