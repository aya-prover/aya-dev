// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.concrete;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableArrayList;
import kala.control.Either;
import kala.value.MutableValue;
import org.aya.generic.AyaDocile;
import org.aya.generic.Nested;
import org.aya.generic.term.DTKind;
import org.aya.generic.term.ParamLike;
import org.aya.generic.term.SortKind;
import org.aya.prettier.BasePrettier;
import org.aya.prettier.ConcretePrettier;
import org.aya.pretty.doc.Doc;
import org.aya.syntax.concrete.stmt.*;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.ref.AnyVar;
import org.aya.syntax.ref.LocalVar;
import org.aya.util.Arg;
import org.aya.util.BinOpElem;
import org.aya.util.ForLSP;
import org.aya.util.PrettierOptions;
import org.aya.util.position.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

public sealed interface Expr extends AyaDocile {
  @NotNull Expr descent(@NotNull PosedUnaryOperator<@NotNull Expr> f);
  void forEach(@NotNull PosedConsumer<@NotNull Expr> f);
  @ForLSP
  sealed interface WithTerm permits LetBind, Param, Proj, Ref, Pattern.As, Pattern.Bind {
    @NotNull MutableValue<Term> theCoreType();
    default @Nullable Term coreType() { return theCoreType().get(); }
  }

  sealed interface BindIntro {
    @NotNull LocalVar ref();
  }

  /** Yes, please */
  sealed interface Sugar { }

  @Override default @NotNull Doc toDoc(@NotNull PrettierOptions options) {
    return new ConcretePrettier(options).term(BasePrettier.Outer.Free, this);
  }

  record Param(
    @Override @NotNull SourcePos sourcePos,
    @Override @NotNull LocalVar ref,
    @NotNull WithPos<Expr> typeExpr,
    boolean explicit,
    @ForLSP MutableValue<Term> theCoreType
  ) implements SourceNode, Named, AyaDocile, ParamLike<Expr>, WithTerm, BindIntro {
    @Override
    public @NotNull SourcePos nameSourcePos() {
      return ref.definition();
    }

    @Override public @NotNull Expr type() { return typeExpr.data(); }

    public Param(@NotNull SourcePos sourcePos, @NotNull LocalVar ref, @NotNull WithPos<Expr> typeExpr, boolean explicit) {
      this(sourcePos, ref, typeExpr, explicit, MutableValue.create());
    }

    public @NotNull Param update(@NotNull WithPos<Expr> type) {
      return type == typeExpr() ? this : new Param(sourcePos, ref, type, explicit, theCoreType);
    }

    public @NotNull Param descent(@NotNull PosedUnaryOperator<Expr> f) { return update(typeExpr.descent(f)); }
    public void forEach(@NotNull PosedConsumer<Expr> f) { f.accept(typeExpr); }
  }

  /// @param filling  the inner expr of goal
  /// @param explicit whether the hole is a type-directed programming goal or a to-be-solved by tycking hole.
  record Hole(
    boolean explicit,
    @Nullable WithPos<Expr> filling,
    @NotNull MutableValue<Term> solution,
    @NotNull ImmutableSeq<LocalVar> accessibleLocal
  ) implements Expr {
    public Hole(boolean explicit, @Nullable WithPos<Expr> filling) {
      this(explicit, filling, MutableValue.create(), ImmutableSeq.empty());
    }

    public @NotNull Hole update(@Nullable WithPos<Expr> filling) {
      return filling == filling() ? this : new Hole(explicit, filling, solution, accessibleLocal);
    }

    @Override public @NotNull Hole descent(@NotNull PosedUnaryOperator<@NotNull Expr> f) {
      return update(filling == null ? null : filling.descent(f));
    }
    @Override public void forEach(@NotNull PosedConsumer<Expr> f) { if (filling != null) f.accept(filling); }
  }

  record Error(@NotNull AyaDocile description) implements Expr {
    public Error(@NotNull Doc description) { this(_ -> description); }

    @Override public @NotNull Expr.Error descent(@NotNull PosedUnaryOperator<@NotNull Expr> f) { return this; }
    @Override public void forEach(@NotNull PosedConsumer<Expr> f) { }
  }

  /// It is possible that `seq.size() == 1`, cause BinOpSeq also represents a scope of operator sequence,
  /// for example: the `(+)` in `f (+)` will be recognized as argument instead of a function call.
  ///
  /// @param seq input to the binop parser
  record BinOpSeq(@NotNull ImmutableSeq<NamedArg> seq) implements Expr, Sugar {
    public @NotNull BinOpSeq update(@NotNull ImmutableSeq<NamedArg> seq) {
      return seq.sameElements(seq(), true) ? this : new BinOpSeq(seq);
    }

    @Override public @NotNull BinOpSeq descent(@NotNull PosedUnaryOperator<@NotNull Expr> f) {
      return update(seq.map(arg -> arg.descent(f)));
    }
    @Override public void forEach(@NotNull PosedConsumer<Expr> f) { seq.forEach(arg -> arg.forEach(f)); }
  }

  record Unresolved(@NotNull QualifiedID name) implements Expr {
    public Unresolved(@NotNull SourcePos pos, @NotNull String name) {
      this(new QualifiedID(pos, name));
    }

    @Override public @NotNull Unresolved descent(@NotNull PosedUnaryOperator<@NotNull Expr> f) { return this; }
    @Override public void forEach(@NotNull PosedConsumer<Expr> f) { }
  }

  record Ref(@NotNull AnyVar var, @NotNull MutableValue<Term> theCoreType) implements Expr, WithTerm {
    public Ref(@NotNull AnyVar var) {
      this(var, MutableValue.create());
    }

    @Override public @NotNull Expr descent(@NotNull PosedUnaryOperator<@NotNull Expr> f) { return this; }
    @Override public void forEach(@NotNull PosedConsumer<Expr> f) { }
  }

  record ClauseLam(@NotNull Pattern.Clause clause) implements Expr, Sugar {
    // TODO: remove this
    public static boolean canBeBind(@NotNull Arg<WithPos<Pattern>> pat) {
      var thePat = pat.term().data();
      return thePat instanceof Pattern.Bind || thePat == Pattern.CalmFace.INSTANCE;
    }

    public ClauseLam {
      assert clause.patterns.isNotEmpty();
    }

    public @NotNull Expr.ClauseLam update(@NotNull Pattern.Clause clause) {
      return clause == this.clause ? this : new ClauseLam(clause);
    }

    public @NotNull ImmutableSeq<Arg<WithPos<Pattern>>> patterns() { return clause.patterns; }
    public @NotNull WithPos<Expr> body() { return clause.expr.get(); }

    @Override public @NotNull Expr.ClauseLam descent(@NotNull PosedUnaryOperator<@NotNull Expr> f) {
      return descent(f, PosedUnaryOperator.identity());
    }

    public @NotNull Expr.ClauseLam descent(@NotNull PosedUnaryOperator<@NotNull Expr> f, @NotNull PosedUnaryOperator<@NotNull Pattern> g) {
      return update(clause.descent(f, g));
    }

    @Override public void forEach(@NotNull PosedConsumer<Expr> f) { clause.forEach(f, (_, _) -> { }); }
  }

  record Lambda(
    @NotNull LocalVar ref,
    @Override @NotNull WithPos<Expr> body
  ) implements Expr, Nested<LocalVar, Expr, Lambda> {
    @Override public @NotNull LocalVar param() { return ref; }
    public @NotNull Lambda update(@NotNull WithPos<Expr> body) {
      return body == body() ? this : new Lambda(ref, body);
    }

    @Override public @NotNull Lambda descent(@NotNull PosedUnaryOperator<@NotNull Expr> f) {
      return update(body.descent(f));
    }
    @Override public void forEach(@NotNull PosedConsumer<Expr> f) { f.accept(body); }
  }

  record BinTuple(@NotNull WithPos<Expr> lhs, @NotNull WithPos<Expr> rhs) implements Expr {
    public @NotNull Expr.BinTuple update(@NotNull WithPos<Expr> newLhs, @NotNull WithPos<Expr> newRhs) {
      return lhs == newLhs && rhs == newRhs ? this : new BinTuple(newLhs, newRhs);
    }

    @Override public @NotNull Expr.BinTuple descent(@NotNull PosedUnaryOperator<@NotNull Expr> f) {
      return update(lhs.descent(f), rhs.descent(f));
    }
    @Override public void forEach(@NotNull PosedConsumer<Expr> f) {
      f.accept(lhs);
      f.accept(rhs);
    }
  }

  /**
   * @param resolvedVar will be set to the field's DefVar during resolving
   * @author re-xyr
   */
  record Proj(
    @NotNull WithPos<Expr> tup,
    @NotNull Either<Integer, QualifiedID> ix,
    @Nullable AnyVar resolvedVar,
    @Override @NotNull MutableValue<Term> theCoreType
  ) implements Expr, WithTerm {
    public Proj(@NotNull WithPos<Expr> tup, @NotNull Either<Integer, QualifiedID> ix) {
      this(tup, ix, null, MutableValue.create());
    }

    public @NotNull Expr.Proj update(@NotNull WithPos<Expr> tup) {
      return tup == tup() ? this : new Proj(tup, ix, resolvedVar, theCoreType);
    }

    @Override public @NotNull Expr.Proj descent(@NotNull PosedUnaryOperator<@NotNull Expr> f) {
      return update(tup.descent(f));
    }
    @Override public void forEach(@NotNull PosedConsumer<Expr> f) { f.accept(tup); }
  }

  record App(
    @NotNull WithPos<Expr> function,
    @NotNull ImmutableSeq<NamedArg> argument
  ) implements Expr {
    public @NotNull App update(@NotNull WithPos<Expr> function, @NotNull ImmutableSeq<NamedArg> argument) {
      return function == function() && argument.sameElements(argument(), true)
        ? this : new App(function, argument);
    }

    @Override public @NotNull App descent(@NotNull PosedUnaryOperator<@NotNull Expr> f) {
      return update(function.descent(f), argument.map(arg -> arg.descent(f)));
    }
    @Override public void forEach(@NotNull PosedConsumer<Expr> f) {
      f.accept(function);
      argument.forEach(arg -> arg.forEach(f));
    }
  }

  record NamedArg(
    @Override boolean explicit,
    @Nullable String name,
    @NotNull WithPos<Expr> arg
  ) implements SourceNode, BinOpElem<WithPos<Expr>>, AyaDocile {
    public NamedArg(boolean explicit, @NotNull WithPos<Expr> arg) { this(explicit, null, arg); }
    @Override public @NotNull SourcePos sourcePos() { return arg.sourcePos(); }
    @Override public @NotNull WithPos<Expr> term() { return arg; }

    public @NotNull NamedArg update(@NotNull WithPos<Expr> expr) {
      return expr == arg ? this : new NamedArg(explicit, name, expr);
    }

    @Override public @NotNull Doc toDoc(@NotNull PrettierOptions options) {
      var doc = name == null ? arg.data().toDoc(options) :
        Doc.braced(Doc.sep(Doc.plain(name), Doc.symbol("=>"), arg.data().toDoc(options)));
      return Doc.bracedUnless(doc, explicit);
    }

    public @NotNull NamedArg descent(@NotNull PosedUnaryOperator<@NotNull Expr> f) {
      return update(arg.descent(f));
    }
    public void forEach(@NotNull PosedConsumer<Expr> f) {
      f.accept(arg);
    }
  }

  record DepType(@NotNull DTKind kind, @NotNull Param param, @NotNull WithPos<Expr> last) implements Expr {
    public @NotNull Expr.DepType update(@NotNull Param param, @NotNull WithPos<Expr> last) {
      return param == param() && last == last() ? this : new DepType(kind, param, last);
    }

    @Override public @NotNull Expr.DepType descent(@NotNull PosedUnaryOperator<@NotNull Expr> f) {
      return update(param.descent(f), last.descent(f));
    }
    @Override public void forEach(@NotNull PosedConsumer<Expr> f) {
      param.forEach(f);
      f.accept(last);
    }
  }

  record Partial(@NotNull WithPos<Expr> body) implements Expr {
    public @NotNull Expr.Partial update(@NotNull WithPos<Expr> body) {
      return body == body() ? this : new Partial(body);
    }

    @Override public @NotNull Expr descent(@NotNull PosedUnaryOperator<@NotNull Expr> f) {
      return update(body.descent(f));
    }
    @Override public void forEach(@NotNull PosedConsumer<@NotNull Expr> f) { f.accept(body); }
  }

  record RawSort(@NotNull SortKind kind) implements Expr, Sugar {
    @Override public @NotNull RawSort descent(@NotNull PosedUnaryOperator<@NotNull Expr> f) { return this; }
    @Override public void forEach(@NotNull PosedConsumer<Expr> f) { }
  }

  record Sort(@NotNull SortKind kind, int lift) implements Expr {
    @Override public @NotNull Sort descent(@NotNull PosedUnaryOperator<@NotNull Expr> f) { return this; }
    @Override public void forEach(@NotNull PosedConsumer<Expr> f) { }
  }

  record Lift(@NotNull WithPos<Expr> expr, int lift) implements Expr {
    public @NotNull Lift update(@NotNull WithPos<Expr> expr) {
      return expr == expr() ? this : new Lift(expr, lift);
    }

    @Override public @NotNull Lift descent(@NotNull PosedUnaryOperator<@NotNull Expr> f) {
      return update(expr.descent(f));
    }
    @Override public void forEach(@NotNull PosedConsumer<Expr> f) { f.accept(expr); }
  }

  record LitInt(int integer) implements Expr {
    @Override public @NotNull LitInt descent(@NotNull PosedUnaryOperator<@NotNull Expr> f) { return this; }
    @Override public void forEach(@NotNull PosedConsumer<Expr> f) { }
  }

  record LitString(@NotNull String string) implements Expr {
    @Override public @NotNull LitString descent(@NotNull PosedUnaryOperator<@NotNull Expr> f) { return this; }
    @Override public void forEach(@NotNull PosedConsumer<Expr> f) { }
  }

  enum LambdaHole implements Expr {
    INSTANCE;

    @Override public @NotNull Expr descent(@NotNull PosedUnaryOperator<@NotNull Expr> f) { return this; }
    @Override public void forEach(@NotNull PosedConsumer<@NotNull Expr> f) { }
  }

  record New(@NotNull WithPos<Expr> classCall) implements Expr {
    public @NotNull Expr.New update(@NotNull WithPos<Expr> classCall) {
      return classCall == classCall() ? this : new New(classCall);
    }

    @Override public @NotNull Expr.New descent(@NotNull PosedUnaryOperator<@NotNull Expr> f) {
      return update(classCall.descent(f));
    }

    @Override public void forEach(@NotNull PosedConsumer<@NotNull Expr> f) { f.accept(classCall); }
  }

  record Idiom(
    @NotNull IdiomNames names,
    @NotNull ImmutableSeq<WithPos<Expr>> barredApps
  ) implements Expr, Sugar {
    public @NotNull Idiom update(@NotNull IdiomNames names, @NotNull ImmutableSeq<WithPos<Expr>> barredApps) {
      return names.identical(names()) && barredApps.sameElements(barredApps(), true) ? this
        : new Idiom(names, barredApps);
    }

    @Override public @NotNull Idiom descent(@NotNull PosedUnaryOperator<@NotNull Expr> f) {
      return update(names.fmap(x -> f.apply(SourcePos.NONE, x)), barredApps.map(x -> x.descent(f)));
    }
    @Override public void forEach(@NotNull PosedConsumer<Expr> f) {
      names.forEach(x -> f.accept(SourcePos.NONE, x));
      barredApps.forEach(f::accept);
    }
  }

  record IdiomNames(
    @NotNull Expr alternativeEmpty,
    @NotNull Expr alternativeOr,
    @NotNull Expr applicativeAp,
    @NotNull Expr applicativePure
  ) {
    public IdiomNames fmap(@NotNull Function<Expr, Expr> f) {
      return new IdiomNames(
        f.apply(alternativeEmpty),
        f.apply(alternativeOr),
        f.apply(applicativeAp),
        f.apply(applicativePure));
    }

    public boolean identical(@NotNull IdiomNames names) {
      return alternativeEmpty == names.alternativeEmpty
        && alternativeOr == names.alternativeOr
        && applicativeAp == names.applicativeAp
        && applicativePure == names.applicativePure;
    }

    public void forEach(@NotNull Consumer<Expr> f) {
      f.accept(alternativeEmpty);
      f.accept(alternativeOr);
      f.accept(applicativeAp);
      f.accept(applicativePure);
    }
  }

  /**
   * @param bindName guess: we don't need the source pos of it
   */
  record Do(@NotNull Expr bindName, @NotNull ImmutableSeq<DoBind> binds) implements Expr, Sugar {
    public @NotNull Do update(@NotNull Expr bindName, @NotNull ImmutableSeq<DoBind> binds) {
      return bindName == bindName() && binds.sameElements(binds(), true) ? this
        : new Do(bindName, binds);
    }

    @Override public @NotNull Do descent(@NotNull PosedUnaryOperator<@NotNull Expr> f) {
      return update(f.apply(SourcePos.NONE, bindName), binds.map(bind -> bind.descent(f)));
    }
    @Override public void forEach(@NotNull PosedConsumer<Expr> f) {
      f.accept(SourcePos.NONE, bindName);
      binds.forEach(bind -> bind.forEach(f));
    }
  }

  record DoBind(
    @NotNull SourcePos sourcePos,
    @NotNull LocalVar var,
    @NotNull WithPos<Expr> expr
  ) implements SourceNode, Named, BindIntro {
    @Override public @NotNull LocalVar ref() { return var; }
    @Override public @NotNull SourcePos nameSourcePos() {
      return var.definition();
    }

    public DoBind(@NotNull WithPos<Expr> expr) {
      this(expr.sourcePos(), LocalVar.IGNORED, expr);
    }

    public @NotNull DoBind update(@NotNull WithPos<Expr> expr) {
      return expr == expr() ? this : new DoBind(sourcePos, var, expr);
    }

    public @NotNull DoBind descent(@NotNull PosedUnaryOperator<@NotNull Expr> f) { return update(expr.descent(f)); }
    public void forEach(@NotNull PosedConsumer<Expr> f) { f.accept(expr); }
  }

  /// # Array Expr
  ///
  /// @param arrayBlock `[ x | x <- [ 1, 2, 3 ]]` (left) or `[ 1, 2, 3 ]` (right)
  /// @apiNote empty array `[]` should be a right (an empty expr seq)
  record Array(@NotNull Either<CompBlock, ElementList> arrayBlock) implements Expr {
    public @NotNull Array update(@NotNull Either<CompBlock, ElementList> arrayBlock) {
      var equal = arrayBlock.bifold(this.arrayBlock, false,
        (newOne, oldOne) -> newOne == oldOne,
        (newOne, oldOne) -> newOne == oldOne);

      return equal ? this : new Array(arrayBlock);
    }

    @Override public @NotNull Array descent(@NotNull PosedUnaryOperator<@NotNull Expr> f) {
      return update(arrayBlock.map(comp -> comp.descent(f), list -> list.descent(f)));
    }

    @Override public void forEach(@NotNull PosedConsumer<Expr> f) {
      arrayBlock.forEach(comp -> comp.forEach(f), list -> list.forEach(f));
    }

    public record ElementList(@NotNull ImmutableSeq<WithPos<Expr>> exprList) {
      public @NotNull ElementList update(@NotNull ImmutableSeq<WithPos<Expr>> exprList) {
        return exprList.sameElements(exprList(), true) ? this : new ElementList(exprList);
      }

      public @NotNull ElementList descent(@NotNull PosedUnaryOperator<@NotNull Expr> f) {
        return update(exprList.map(x -> x.descent(f)));
      }
      public void forEach(@NotNull PosedConsumer<Expr> f) { exprList.forEach(f::accept); }
    }

    public record ListCompNames(@NotNull Expr monadBind, @NotNull Expr functorPure) {
      public ListCompNames fmap(@NotNull Function<Expr, Expr> f) {
        return new ListCompNames(f.apply(monadBind), f.apply(functorPure));
      }

      public boolean identical(@NotNull ListCompNames names) {
        return monadBind == names.monadBind && functorPure == names.functorPure;
      }

      public void forEach(@NotNull Consumer<Expr> f) {
        f.accept(monadBind);
        f.accept(functorPure);
      }
    }

    /// # Array Comp(?)
    ///
    /// The (half?) primary part of [Array]
    /// For example: `[x * y | x <- [1, 2, 3], y <- [4, 5, 6]]`
    ///
    /// @param generator `x * y` part above
    /// @param binds     `x <- [1, 2, 3], y <- [4, 5, 6]` part above
    /// @param names     bind (`>>=`) is [#monadBind] by default and pure (`return`) is [#functorPure] by default
    /// @apiNote a ArrayCompBlock will be desugar to a do-block. For the example above,
    /// it will be desugared to `do x <- [1, 2, 3], y <- [4, 5, 6], return x * y`
    public record CompBlock(
      @NotNull WithPos<Expr> generator,
      @NotNull ImmutableSeq<DoBind> binds,
      @NotNull ListCompNames names
    ) {
      public @NotNull CompBlock update(@NotNull WithPos<Expr> generator, @NotNull ImmutableSeq<DoBind> binds, @NotNull ListCompNames names) {
        return generator == generator() && binds.sameElements(binds(), true) && names.identical(names())
          ? this
          : new CompBlock(generator, binds, names);
      }

      public @NotNull CompBlock descent(@NotNull PosedUnaryOperator<@NotNull Expr> f) {
        return update(generator.descent(f), binds.map(bind -> bind.descent(f)), names.fmap(x -> f.apply(SourcePos.NONE, x)));
      }
      public void forEach(@NotNull PosedConsumer<Expr> f) {
        f.accept(generator);
        binds.forEach(bind -> bind.forEach(f));
        names.forEach(x -> f.accept(SourcePos.NONE, x));
      }
    }

    /// Helper constructor, also find constructor calls easily in IDE
    public static Array newList(@NotNull ImmutableSeq<WithPos<Expr>> exprs) {
      return new Array(Either.right(new ElementList(exprs)));
    }

    public static Array newGenerator(
      @NotNull WithPos<Expr> generator,
      @NotNull ImmutableSeq<DoBind> bindings,
      @NotNull ListCompNames names
    ) {
      return new Array(Either.left(new CompBlock(generator, bindings, names)));
    }
  }

  /// # Let Expression
  /// ```
  ///   let
  ///     f (x : X) : G := g
  ///   in expr
  ///```
  /// where:
  ///
  /// - [LetBind#bindName] = `f`
  /// - [LetBind#telescope] = `(x : X)`
  /// - [LetBind#result] = `G`
  /// - [LetBind#definedAs] = `g`
  /// - [#body] = `expr`
  record Let(
    @NotNull LetBind bind,
    @NotNull WithPos<Expr> body
  ) implements Expr, Nested<LetBind, Expr, Let> {
    public @NotNull Let update(@NotNull LetBind bind, @NotNull WithPos<Expr> body) {
      return bind() == bind && body() == body ? this : new Let(bind, body);
    }

    @Override public @NotNull Expr descent(@NotNull PosedUnaryOperator<@NotNull Expr> f) {
      return update(bind.descent(f), body.descent(f));
    }

    @Override public void forEach(@NotNull PosedConsumer<Expr> f) {
      bind.forEach(f);
      f.accept(body);
    }

    @Override public @NotNull LetBind param() { return bind; }
  }

  record LetBind(
    @NotNull SourcePos sourcePos,
    @NotNull LocalVar bindName,
    @NotNull ImmutableSeq<Param> telescope,
    @NotNull WithPos<Expr> result,
    @NotNull WithPos<Expr> definedAs,
    @NotNull MutableValue<Term> theCoreType
  ) implements SourceNode, Named, BindIntro, WithTerm {
    @Override
    public @NotNull SourcePos nameSourcePos() {
      return bindName.sourcePos();
    }
    @Override public @NotNull LocalVar ref() { return bindName; }

    public @NotNull LetBind update(@NotNull ImmutableSeq<Param> telescope, @NotNull WithPos<Expr> result, @NotNull WithPos<Expr> definedAs) {
      return telescope().sameElements(telescope, true) && result() == result && definedAs() == definedAs
        ? this : new LetBind(sourcePos, bindName, telescope, result, definedAs, theCoreType);
    }

    public @NotNull LetBind descent(@NotNull PosedUnaryOperator<@NotNull Expr> f) {
      return update(telescope.map(x -> x.descent(f)), result.descent(f), definedAs.descent(f));
    }

    public void forEach(PosedConsumer<Expr> f) {
      telescope.forEach(param -> param.forEach(f));
      f.accept(result);
      f.accept(definedAs);
    }
  }

  /// I think a new type is better than `Either<LetBind, Open> bind` in `Expr.Let`.
  /// Being desugared after resolving.
  record LetOpen(
    @NotNull SourcePos sourcePos,
    @NotNull WithPos<ModuleName.Qualified> componentName,
    @NotNull UseHide useHide,
    @NotNull WithPos<Expr> body
  ) implements Expr, Sugar {
    public @NotNull LetOpen update(@NotNull WithPos<Expr> body) {
      return this.body == body ? this : new LetOpen(sourcePos, componentName, useHide, body);
    }

    @Override public @NotNull Expr descent(@NotNull PosedUnaryOperator<@NotNull Expr> f) {
      return update(body.descent(f));
    }
    @Override public void forEach(@NotNull PosedConsumer<@NotNull Expr> f) {
      f.accept(body);
    }

    public @NotNull Command.Open openCmd() {
      return new Command.Open(
        sourcePos, Stmt.Accessibility.Private,
        componentName.data(), useHide,
        false, true
      );
    }
  }

  record Match(
    @NotNull ImmutableSeq<Discriminant> discriminant,
    @NotNull ImmutableSeq<Pattern.Clause> clauses,
    @Nullable WithPos<Expr> returns
  ) implements Expr {
    public record Discriminant(
      @NotNull WithPos<Expr> discr,
      @Nullable LocalVar asBinding,
      boolean isElim
    ) {
      public @NotNull Discriminant update(@NotNull WithPos<Expr> discr) {
        return discr == discr() ? this : new Discriminant(discr, asBinding, isElim);
      }
      public @NotNull Discriminant descent(@NotNull PosedUnaryOperator<@NotNull Expr> f) {
        return update(discr.descent(f));
      }
    }

    public @NotNull Match update(
      @NotNull ImmutableSeq<Discriminant> discriminant,
      @NotNull ImmutableSeq<Pattern.Clause> clauses,
      @Nullable WithPos<Expr> returns
    ) {
      return this.discriminant.sameElements(discriminant, true)
        && this.clauses.sameElements(clauses, true) && this.returns == returns
        ? this : new Match(discriminant, clauses, returns);
    }

    @Override public @NotNull Expr descent(@NotNull PosedUnaryOperator<@NotNull Expr> f) {
      return descent(f, PosedUnaryOperator.identity());
    }

    public @NotNull Expr descent(@NotNull PosedUnaryOperator<@NotNull Expr> f, @NotNull PosedUnaryOperator<@NotNull Pattern> g) {
      return update(discriminant.map(d -> d.descent(f)),
        clauses.map(x -> x.descent(f, g)),
        returns != null ? returns.descent(f) : null);
    }

    /// Patterns will be visited externally, so we don't need to visit them here.
    ///
    /// @see StmtVisitor#visitExpr
    @Override public void forEach(@NotNull PosedConsumer<@NotNull Expr> f) {
      discriminant.forEach(d -> f.accept(d.discr()));
      clauses.forEach(clause -> clause.forEach(f, (_, _) -> { }));
      if (returns != null) f.accept(returns);
    }
  }

  static @NotNull WithPos<Expr> buildPi(@NotNull SourcePos sourcePos, @NotNull SeqView<Param> params, @NotNull WithPos<Expr> body) {
    return buildNested(sourcePos, params, body, (a, b) -> new DepType(DTKind.Pi, a, b));
  }

  static @NotNull WithPos<Expr> buildSigma(@NotNull SourcePos sourcePos, @NotNull SeqView<Param> params, @NotNull WithPos<Expr> body) {
    return buildNested(sourcePos, params, body, (a, b) -> new DepType(DTKind.Sigma, a, b));
  }

  static @NotNull WithPos<Expr> buildTuple(@NotNull SourcePos sourcePos, @NotNull SeqView<WithPos<Expr>> params) {
    return buildNested(sourcePos, params.dropLast(1), params.getLast(), BinTuple::new);
  }

  static @NotNull WithPos<Pattern> buildTupPat(@NotNull SourcePos sourcePos, @NotNull SeqView<WithPos<Pattern>> params) {
    return buildNested(sourcePos, params.dropLast(1), params.getLast(), Pattern.Tuple::new);
  }

  static @NotNull WithPos<Expr> buildLam(@NotNull SourcePos sourcePos, @NotNull SeqView<LocalVar> params, @NotNull WithPos<Expr> body) {
    return buildNested(sourcePos, params, body, Lambda::new);
  }

  static @NotNull WithPos<Expr> buildLet(@NotNull SourcePos sourcePos, @NotNull SeqView<LetBind> binds, @NotNull WithPos<Expr> body) {
    return buildNested(sourcePos, binds, body, Let::new);
  }

  /// convert flattened terms into nested right-associate terms
  ///
  /// @param sourcePos the source pos of the whole nested structure,
  ///                  should be the same as `body.sourcePos()` if `params.isEmpty()`.
  /// @implSpec `returns.sourcePos() == sourcePos`
  static <P extends SourceNode, E> @NotNull WithPos<E> buildNested(
    @NotNull SourcePos sourcePos,
    @NotNull SeqView<P> params,
    @NotNull WithPos<E> body,
    @NotNull BiFunction<P, WithPos<E>, E> constructor
  ) {
    var subPoses = MutableArrayList.<WithPos<P>>create();
    while (!params.isEmpty()) {
      subPoses.append(new WithPos<>(sourcePos, params.getFirst()));
      params = params.drop(1);
      sourcePos = body.sourcePos().sourcePosForSubExpr(sourcePos.file(),
        params.map(SourceNode::sourcePos));
    }
    return subPoses.foldRight(body, (data, acc) ->
      new WithPos<>(data.sourcePos(), constructor.apply(data.data(), acc)));
  }
}
