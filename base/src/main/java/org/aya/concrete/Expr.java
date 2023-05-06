// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.control.Either;
import kala.function.TriFunction;
import kala.tuple.Tuple2;
import kala.value.MutableValue;
import org.aya.concrete.stmt.Command;
import org.aya.concrete.stmt.QualifiedID;
import org.aya.concrete.stmt.Stmt;
import org.aya.concrete.stmt.UseHide;
import org.aya.generic.AyaDocile;
import org.aya.generic.ParamLike;
import org.aya.generic.SortKind;
import org.aya.guest0x0.cubical.Restr;
import org.aya.prettier.BasePrettier;
import org.aya.prettier.ConcretePrettier;
import org.aya.pretty.doc.Doc;
import org.aya.ref.AnyVar;
import org.aya.ref.LocalVar;
import org.aya.resolve.context.ModuleContext;
import org.aya.resolve.context.ModuleName;
import org.aya.resolve.visitor.ExprResolver;
import org.aya.resolve.visitor.StmtShallowResolver;
import org.aya.tyck.Result;
import org.aya.util.ForLSP;
import org.aya.util.binop.BinOpParser;
import org.aya.util.error.SourceNode;
import org.aya.util.error.SourcePos;
import org.aya.util.error.WithPos;
import org.aya.util.prettier.PrettierOptions;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * @author re-xyr
 */
public sealed interface Expr extends AyaDocile, SourceNode, Restr.TermLike<Expr> {
  @NotNull Expr descent(@NotNull UnaryOperator<@NotNull Expr> f);
  /**
   * Do !!!NOT!!! use in the type checker.
   * This is solely for cosmetic features, such as literate mode inline expressions, or repl.
   *
   * @see org.aya.concrete.stmt.Stmt#resolve
   * @see StmtShallowResolver
   */
  @Contract(pure = true)
  default Expr resolveLax(@NotNull ModuleContext context) {
    var resolver = new ExprResolver(context, ExprResolver.LAX);
    resolver.enterBody();
    var inner = resolver.apply(this);
    var view = resolver.allowedGeneralizes().valuesView().toImmutableSeq().view();
    return Expr.buildLam(sourcePos(), view, inner);
  }

  @Override default @NotNull Doc toDoc(@NotNull PrettierOptions options) {
    return new ConcretePrettier(options).term(BasePrettier.Outer.Free, this);
  }

  @ForLSP
  sealed interface WithTerm extends SourceNode {
    @NotNull MutableValue<Result> theCore();
    default @Nullable Result core() {
      return theCore().get();
    }
  }

  /**
   * @author re-xyr
   */
  record Unresolved(
    @NotNull SourcePos sourcePos,
    @NotNull QualifiedID name
  ) implements Expr {
    public Unresolved(@NotNull SourcePos sourcePos, @NotNull String name) {
      this(sourcePos, new QualifiedID(sourcePos, name));
    }

    @Override public @NotNull Expr.Unresolved descent(@NotNull UnaryOperator<@NotNull Expr> f) {
      return this;
    }
  }

  record Error(@NotNull SourcePos sourcePos, @NotNull AyaDocile description) implements Expr {
    public Error(@NotNull SourcePos sourcePos, @NotNull Doc description) {
      this(sourcePos, options -> description);
    }

    @Override public @NotNull Expr.Error descent(@NotNull UnaryOperator<@NotNull Expr> f) {
      return this;
    }
  }

  /**
   * @param explicit whether the hole is a type-directed programming goal or
   *                 a to-be-solved by tycking hole.
   * @author ice1000
   */
  record Hole(
    @Override @NotNull SourcePos sourcePos,
    boolean explicit,
    @Nullable Expr filling,
    MutableValue<ImmutableSeq<LocalVar>> accessibleLocal
  ) implements Expr {
    public Hole(@NotNull SourcePos sourcePos, boolean explicit, @Nullable Expr filling) {
      this(sourcePos, explicit, filling, MutableValue.create());
    }

    public @NotNull Expr.Hole update(@Nullable Expr filling) {
      return filling == filling() ? this : new Hole(sourcePos, explicit, filling);
    }

    @Override public @NotNull Expr.Hole descent(@NotNull UnaryOperator<@NotNull Expr> f) {
      return update(filling == null ? null : f.apply(filling));
    }
  }

  /**
   * @author re-xyr
   */
  record App(
    @NotNull SourcePos sourcePos,
    @NotNull Expr function,
    @NotNull NamedArg argument
  ) implements Expr {
    public @NotNull Expr.App update(@NotNull Expr function, @NotNull NamedArg argument) {
      return function == function() && argument == argument() ? this : new App(sourcePos, function, argument);
    }

    @Override public @NotNull Expr.App descent(@NotNull UnaryOperator<@NotNull Expr> f) {
      return update(f.apply(function), argument.descent(f));
    }
  }

  static @NotNull Expr app(@NotNull Expr function, @NotNull SeqView<WithPos<NamedArg>> arguments) {
    return arguments.foldLeft(function, (f, arg) -> new App(arg.sourcePos(), f, arg.data()));
  }

  static @NotNull Expr unapp(@NotNull Expr expr, @Nullable MutableList<NamedArg> args) {
    while (expr instanceof App app) {
      if (args != null) args.append(app.argument);
      expr = app.function;
    }
    if (args != null) args.reverse();
    return expr;
  }

  /**
   * @author AustinZhu
   */
  record NamedArg(@Override boolean explicit, @Nullable String name, @Override @NotNull Expr term)
    implements AyaDocile, SourceNode, BinOpParser.Elem<Expr> {

    public NamedArg(boolean explicit, @NotNull Expr expr) {
      this(explicit, null, expr);
    }


    public @NotNull NamedArg update(@NotNull Expr expr) {
      return expr == term() ? this : new NamedArg(explicit, name, expr);
    }

    public @NotNull NamedArg descent(@NotNull Function<@NotNull Expr, @NotNull Expr> f) {
      return update(f.apply(term));
    }

    @Override public @NotNull Doc toDoc(@NotNull PrettierOptions options) {
      var doc = name == null ? term.toDoc(options) :
        Doc.braced(Doc.sep(Doc.plain(name), Doc.symbol("=>"), term.toDoc(options)));
      return Doc.bracedUnless(doc, explicit);
    }

    @Override public @NotNull SourcePos sourcePos() {
      return term.sourcePos();
    }
  }

  /**
   * @author re-xyr
   */
  record Pi(
    @NotNull SourcePos sourcePos,
    @NotNull Param param,
    @NotNull Expr last
  ) implements Expr {
    public @NotNull Expr.Pi update(@NotNull Param param, @NotNull Expr last) {
      return param == param() && last == last() ? this : new Pi(sourcePos, param, last);
    }

    @Override public @NotNull Expr.Pi descent(@NotNull UnaryOperator<@NotNull Expr> f) {
      return update(param.descent(f), f.apply(last));
    }
  }

  record Do(
    @NotNull SourcePos sourcePos,
    @NotNull Expr bindName,
    @NotNull ImmutableSeq<DoBind> binds
  ) implements Expr {
    public @NotNull Do update(@NotNull Expr bindName, @NotNull ImmutableSeq<DoBind> binds) {
      return bindName == bindName() && binds.sameElements(binds(), true) ? this
        : new Do(sourcePos, bindName, binds);
    }

    @Override public @NotNull Do descent(@NotNull UnaryOperator<@NotNull Expr> f) {
      return update(f.apply(bindName), binds.map(bind -> bind.descent(f)));
    }
  }

  record DoBind(
    @NotNull SourcePos sourcePos,
    @NotNull LocalVar var,
    @NotNull Expr expr
  ) implements SourceNode {
    public @NotNull DoBind update(@NotNull Expr expr) {
      return expr == expr() ? this : new DoBind(sourcePos, var, expr);
    }

    public @NotNull DoBind descent(@NotNull UnaryOperator<@NotNull Expr> f) {
      return update(f.apply(expr));
    }
  }

  record Idiom(
    @NotNull SourcePos sourcePos,
    @NotNull IdiomNames names,
    @NotNull ImmutableSeq<Expr> barredApps
  ) implements Expr {
    public @NotNull Idiom update(@NotNull IdiomNames names, @NotNull ImmutableSeq<Expr> barredApps) {
      return names.identical(names()) && barredApps.sameElements(barredApps(), true) ? this
        : new Idiom(sourcePos, names, barredApps);
    }

    @Override public @NotNull Idiom descent(@NotNull UnaryOperator<@NotNull Expr> f) {
      return update(names.fmap(f), barredApps.map(f));
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
  }

  /**
   * @author re-xyr
   */
  record Lambda(
    @NotNull SourcePos sourcePos,
    @NotNull Param param,
    @NotNull Expr body
  ) implements Expr {
    public @NotNull Expr.Lambda update(@NotNull Param param, @NotNull Expr body) {
      return param == param() && body == body() ? this : new Lambda(sourcePos, param, body);
    }

    @Override public @NotNull Expr.Lambda descent(@NotNull UnaryOperator<@NotNull Expr> f) {
      return update(param.descent(f), f.apply(body));
    }
  }

  /**
   * @author re-xyr
   */
  record Sigma(
    @NotNull SourcePos sourcePos,
    @NotNull ImmutableSeq<@NotNull Param> params
  ) implements Expr {
    public @NotNull Expr.Sigma update(@NotNull ImmutableSeq<@NotNull Param> params) {
      return params.sameElements(params(), true) ? this : new Sigma(sourcePos, params);
    }

    @Override public @NotNull Expr.Sigma descent(@NotNull UnaryOperator<@NotNull Expr> f) {
      return update(params.map(param -> param.descent(f)));
    }
  }

  /**
   * <pre>
   * def infix + add (a b : Nat) => ...
   * def test => zero + zero
   * </pre>
   *
   * @author ice1000
   */
  record Ref(
    @NotNull SourcePos sourcePos,
    @NotNull AnyVar resolvedVar,
    @NotNull MutableValue<Result> theCore
  ) implements Expr, WithTerm {
    public Ref(@NotNull SourcePos sourcePos, @NotNull AnyVar resolvedVar) {
      this(sourcePos, resolvedVar, MutableValue.create());
    }

    @Override public @NotNull Expr.Ref descent(@NotNull UnaryOperator<@NotNull Expr> f) {
      return this;
    }
  }

  record Lift(@NotNull SourcePos sourcePos, @NotNull Expr expr, int lift) implements Expr {
    public @NotNull Expr.Lift update(@NotNull Expr expr) {
      return expr == expr() ? this : new Lift(sourcePos, expr, lift);
    }

    @Override public @NotNull Expr.Lift descent(@NotNull UnaryOperator<@NotNull Expr> f) {
      return update(f.apply(expr));
    }
  }

  /**
   * @author tsao-chi
   */
  record RawSort(@NotNull SourcePos sourcePos, @NotNull SortKind kind) implements Expr {
    @Override public @NotNull Expr.RawSort descent(@NotNull UnaryOperator<@NotNull Expr> f) {
      return this;
    }
  }

  sealed interface Sort extends Expr {
    int lift();

    SortKind kind();

    @Override default @NotNull Expr.Sort descent(@NotNull UnaryOperator<@NotNull Expr> f) {
      return this;
    }
  }

  record Type(@NotNull SourcePos sourcePos, @Override int lift) implements Sort {
    @Override public SortKind kind() {
      return SortKind.Type;
    }
  }

  record Set(@NotNull SourcePos sourcePos, @Override int lift) implements Sort {
    @Override public SortKind kind() {
      return SortKind.Set;
    }
  }

  record Prop(@NotNull SourcePos sourcePos) implements Sort {
    @Override public int lift() {
      return 0;
    }

    @Override public SortKind kind() {
      return SortKind.Prop;
    }
  }

  record ISet(@NotNull SourcePos sourcePos) implements Sort {
    @Override public int lift() {
      return 0;
    }

    @Override public SortKind kind() {
      return SortKind.ISet;
    }
  }

  /**
   * @author re-xyr
   */
  record Tuple(
    @Override @NotNull SourcePos sourcePos,
    @NotNull ImmutableSeq<@NotNull Expr> items
  ) implements Expr {
    public @NotNull Expr.Tuple update(@NotNull ImmutableSeq<@NotNull Expr> items) {
      return items.sameElements(items(), true) ? this : new Tuple(sourcePos, items);
    }

    @Override public @NotNull Expr.Tuple descent(@NotNull UnaryOperator<@NotNull Expr> f) {
      return update(items.map(f));
    }
  }

  /**
   * @param resolvedVar will be set to the field's DefVar during resolving
   * @author re-xyr
   */
  record Proj(
    @NotNull SourcePos sourcePos,
    @NotNull Expr tup,
    @NotNull Either<Integer, QualifiedID> ix,
    @Nullable AnyVar resolvedVar,
    @NotNull MutableValue<Result> theCore
  ) implements Expr, WithTerm {
    public Proj(
      @NotNull SourcePos sourcePos, @NotNull Expr tup,
      @NotNull Either<Integer, QualifiedID> ix
    ) {
      this(sourcePos, tup, ix, null, MutableValue.create());
    }

    public @NotNull Expr.Proj update(@NotNull Expr tup) {
      return tup == tup() ? this : new Proj(sourcePos, tup, ix, resolvedVar, theCore);
    }

    @Override public @NotNull Expr.Proj descent(@NotNull UnaryOperator<@NotNull Expr> f) {
      return update(f.apply(tup));
    }
  }

  record New(
    @NotNull SourcePos sourcePos,
    @NotNull Expr struct,
    @NotNull ImmutableSeq<Field<Expr>> fields
  ) implements Expr {
    public @NotNull Expr.New update(@NotNull Expr struct, @NotNull ImmutableSeq<Field<Expr>> fields) {
      return struct == struct() && fields.sameElements(fields(), true) ? this : new New(sourcePos, struct, fields);
    }

    @Override public @NotNull Expr.New descent(@NotNull UnaryOperator<@NotNull Expr> f) {
      return update(f.apply(struct), fields.map(field -> field.descent(f)));
    }
  }

  /**
   * @param resolvedField will be modified during tycking for LSP to function properly.
   */
  record Field<Term extends AyaDocile>(
    @NotNull SourcePos sourcePos,
    @NotNull WithPos<String> name,
    @NotNull ImmutableSeq<WithPos<LocalVar>> bindings,
    @NotNull Term body,
    @ForLSP @NotNull MutableValue<AnyVar> resolvedField
  ) {
    public @NotNull Field<Term> update(@NotNull Term body) {
      return body == body() ? this : new Field<Term>(sourcePos, name, bindings, body, resolvedField);
    }

    public @NotNull Field<Term> descent(@NotNull UnaryOperator<@NotNull Term> f) {
      return update(f.apply(body));
    }
  }

  record Match(
    @NotNull SourcePos sourcePos,
    @NotNull ImmutableSeq<Expr> discriminant,
    @NotNull ImmutableSeq<Pattern.Clause> clauses
  ) implements Expr {
    public @NotNull Match update(@NotNull ImmutableSeq<Expr> discriminant, @NotNull ImmutableSeq<Pattern.Clause> clauses) {
      return discriminant.sameElements(discriminant(), true) && clauses.sameElements(clauses(), true) ? this
        : new Match(sourcePos, discriminant, clauses);
    }

    @Override public @NotNull Match descent(@NotNull UnaryOperator<@NotNull Expr> f) {
      return update(discriminant.map(f), clauses.map(cl -> cl.descent(f)));
    }

    public @NotNull Match descent(@NotNull UnaryOperator<@NotNull Expr> f, @NotNull UnaryOperator<@NotNull Pattern> g) {
      return update(discriminant.map(f), clauses.map(cl -> cl.descent(f, g)));
    }
  }

  /**
   * @author kiva
   */
  record LitInt(@NotNull SourcePos sourcePos, int integer) implements Expr {
    @Override public @NotNull Expr.LitInt descent(@NotNull UnaryOperator<@NotNull Expr> f) {
      return this;
    }
  }

  record LitString(@NotNull SourcePos sourcePos, @NotNull String string) implements Expr {
    @Override public @NotNull Expr.LitString descent(@NotNull UnaryOperator<@NotNull Expr> f) {
      return this;
    }
  }

  /**
   * @author kiva
   */
  record BinOpSeq(
    @NotNull SourcePos sourcePos,
    @NotNull ImmutableSeq<NamedArg> seq
  ) implements Expr {
    public @NotNull BinOpSeq update(@NotNull ImmutableSeq<NamedArg> seq) {
      return seq.sameElements(seq(), true) ? this : new BinOpSeq(sourcePos, seq);
    }

    @Override public @NotNull BinOpSeq descent(@NotNull UnaryOperator<@NotNull Expr> f) {
      return update(seq.map(arg -> arg.descent(f)));
    }
  }

  /** partial element */
  record PartEl(
    @NotNull SourcePos sourcePos,
    @NotNull ImmutableSeq<Tuple2<Expr, Expr>> clauses
  ) implements Expr {
    public @NotNull PartEl update(@NotNull ImmutableSeq<Tuple2<Expr, Expr>> clauses) {
      return clauses.allMatchWith(clauses(), (l, r) -> l.component1() == r.component1() && l.component2() == r.component2()) ? this
        : new PartEl(sourcePos, clauses);
    }

    @Override public @NotNull PartEl descent(@NotNull UnaryOperator<@NotNull Expr> f) {
      return update(clauses.map(cls -> kala.tuple.Tuple.of(f.apply(cls.component1()), f.apply(cls.component2()))));
    }
  }

  /** generalized path type */
  record Path(
    @NotNull SourcePos sourcePos,
    @NotNull ImmutableSeq<LocalVar> params,
    @NotNull Expr type,
    @NotNull PartEl partial
  ) implements Expr {
    public @NotNull Path update(@NotNull Expr type, @NotNull PartEl partial) {
      return type == type() && partial == partial() ? this : new Path(sourcePos, params, type, partial);
    }

    @Override public @NotNull Path descent(@NotNull UnaryOperator<@NotNull Expr> f) {
      return update(f.apply(type), partial.descent(f));
    }
  }

  /**
   * @author re-xyr
   */
  record Param(
    @NotNull SourcePos sourcePos,
    @NotNull LocalVar ref,
    @NotNull Expr type,
    boolean explicit,
    @ForLSP MutableValue<Result> theCore
  ) implements ParamLike<Expr>, SourceNode, WithTerm {
    public Param(@NotNull SourcePos sourcePos, @NotNull LocalVar var, @NotNull Expr type, boolean explicit) {
      this(sourcePos, var, type, explicit, MutableValue.create());
    }

    public Param(@NotNull SourcePos sourcePos, @NotNull LocalVar var, boolean explicit) {
      this(sourcePos, var, new Hole(sourcePos, false, null), explicit, MutableValue.create());
    }

    public @NotNull Param update(@NotNull Expr type) {
      return type == type() ? this : new Param(sourcePos, ref, type, explicit, theCore);
    }

    public @NotNull Param descent(@NotNull Function<@NotNull Expr, @NotNull Expr> f) {
      return update(f.apply(type));
    }
  }

  /**
   * <h1>Array Expr</h1>
   *
   * @param arrayBlock <code>[ x | x <- [ 1, 2, 3 ] ]</code> (left) or <code>[ 1, 2, 3 ]</code> (right)
   * @author HoshinoTented
   * @apiNote the arrayBlock of an empty array <code>[]</code> should be a right (an empty expr seq)
   */
  record Array(
    @Override @NotNull SourcePos sourcePos,
    @NotNull Either<CompBlock, ElementList> arrayBlock
  ) implements Expr {
    public @NotNull Array update(@NotNull Either<CompBlock, ElementList> arrayBlock) {
      if (arrayBlock.isLeft() && arrayBlock().isLeft() && arrayBlock.getLeftValue() == arrayBlock().getLeftValue()) {
        return this;
      } else if (arrayBlock.isRight() && arrayBlock().isRight() && arrayBlock.getRightValue() == arrayBlock().getRightValue()) {
        return this;
      } else {
        return new Array(sourcePos, arrayBlock);
      }
    }

    @Override public @NotNull Array descent(@NotNull UnaryOperator<@NotNull Expr> f) {
      return update(arrayBlock.map(comp -> comp.descent(f), list -> list.descent(f)));
    }

    public record ElementList(@NotNull ImmutableSeq<Expr> exprList) {
      public @NotNull ElementList update(@NotNull ImmutableSeq<Expr> exprList) {
        return exprList.sameElements(exprList(), true) ? this : new ElementList(exprList);
      }

      public @NotNull ElementList descent(@NotNull UnaryOperator<@NotNull Expr> f) {
        return update(exprList.map(f));
      }
    }

    public record ListCompNames(
      @NotNull Expr monadBind,
      @NotNull Expr functorPure
    ) {
      public ListCompNames fmap(@NotNull Function<Expr, Expr> f) {
        return new ListCompNames(f.apply(monadBind), f.apply(functorPure));
      }

      public boolean identical(@NotNull ListCompNames names) {
        return monadBind == names.monadBind && functorPure == names.functorPure;
      }
    }

    /**
     * <h1>Array Comp(?)</h1>
     * <p>
     * The (half?) primary part of {@link Array}<br/>
     * For example: {@code [x * y | x <- [1, 2, 3], y <- [4, 5, 6]]}
     *
     * @param generator {@code x * y} part above
     * @param binds     {@code x <- [1, 2, 3], y <- [4, 5, 6]} part above
     * @param names     the bind ({@code >>=}) function, it is {@link org.aya.generic.Constants#monadBind} in default,
     *                  the pure ({@code return}) function, it is {@link org.aya.generic.Constants#functorPure} in default
     * @apiNote a ArrayCompBlock will be desugar to a do-block. For the example above,
     * it will be desugared to {@code do x <- [1, 2, 3], y <- [4, 5, 6], return x * y}
     */
    public record CompBlock(
      @NotNull Expr generator,
      @NotNull ImmutableSeq<DoBind> binds,
      @NotNull ListCompNames names
    ) {
      public @NotNull CompBlock update(@NotNull Expr generator, @NotNull ImmutableSeq<DoBind> binds, @NotNull ListCompNames names) {
        return generator == generator() && binds.sameElements(binds(), true) && names.identical(names())
          ? this
          : new CompBlock(generator, binds, names);
      }

      public @NotNull CompBlock descent(@NotNull UnaryOperator<@NotNull Expr> f) {
        return update(f.apply(generator), binds.map(bind -> bind.descent(f)), names.fmap(f));
      }
    }

    /**
     * helper constructor, also find constructor calls easily in IDE
     */
    public static Expr.Array newList(
      @NotNull SourcePos sourcePos,
      @NotNull ImmutableSeq<Expr> exprs
    ) {
      return new Expr.Array(
        sourcePos,
        Either.right(new ElementList(exprs))
      );
    }

    public static Expr.Array newGenerator(
      @NotNull SourcePos sourcePos,
      @NotNull Expr generator,
      @NotNull ImmutableSeq<DoBind> bindings,
      @NotNull ListCompNames names
    ) {
      return new Expr.Array(
        sourcePos,
        Either.left(new CompBlock(generator, bindings, names))
      );
    }
  }

  /**
   * <h1>Let Expression</h1>
   *
   * <pre>
   *   let
   *     f (x : X) : G := g
   *   in expr
   * </pre>
   * <p>
   * where:
   * <ul>
   *   <li>{@link LetBind#bindName} = f</li>
   *   <li>{@link LetBind#telescope} = (x : X)</li>
   *   <li>{@link LetBind#result} = G</li>
   *   <li>{@link LetBind#definedAs} = g</li>
   *   <li>{@link Let#body} = expr</li>
   * </ul>
   */
  record Let(
    @NotNull SourcePos sourcePos,
    @NotNull Expr.LetBind bind,
    @NotNull Expr body
  ) implements Expr {
    public @NotNull Let update(@NotNull Expr.LetBind bind, @NotNull Expr body) {
      return bind() == bind && body() == body
        ? this
        : new Let(sourcePos(), bind, body);
    }

    @Override
    public @NotNull Expr descent(@NotNull UnaryOperator<@NotNull Expr> f) {
      return update(bind().descent(f), f.apply(body()));
    }
  }

  record LetBind(
    @NotNull SourcePos sourcePos,
    @NotNull LocalVar bindName,
    @NotNull ImmutableSeq<Expr.Param> telescope,
    @NotNull Expr result,
    @NotNull Expr definedAs
  ) implements SourceNode {
    public @NotNull Expr.LetBind update(@NotNull ImmutableSeq<Expr.Param> telescope, @NotNull Expr result, @NotNull Expr definedAs) {
      return telescope().sameElements(telescope, true) && result() == result && definedAs() == definedAs
        ? this
        : new LetBind(sourcePos, bindName, telescope, result, definedAs);
    }

    public @NotNull Expr.LetBind descent(@NotNull UnaryOperator<@NotNull Expr> f) {
      return update(telescope().map(x -> x.descent(f)), f.apply(result()), f.apply(definedAs()));
    }
  }

  // I think a new type is better than `Either<LetBind, Open> bind` in `Expr.Let`
  record LetOpen(
    @NotNull SourcePos sourcePos,
    @NotNull ModuleName.Qualified componentName,
    @NotNull UseHide useHide,
    @NotNull Expr body
  ) implements Expr {
    public @NotNull LetOpen update(@NotNull Expr body) {
      return this.body == body
        ? this
        : new LetOpen(sourcePos, componentName, useHide, body);
    }

    @Override
    public @NotNull Expr descent(@NotNull UnaryOperator<@NotNull Expr> f) {
      return update(f.apply(body));
    }

    public @NotNull Command.Open openCmd() {
      return new Command.Open(
        sourcePos,
        Stmt.Accessibility.Private,
        componentName,
        useHide,
        false, true
      );
    }
  }

  static @NotNull Expr buildPi(@NotNull SourcePos sourcePos, @NotNull SeqView<Param> params, @NotNull Expr body) {
    return buildNested(sourcePos, params, body, Expr.Pi::new);
  }

  static @NotNull Expr buildLam(@NotNull SourcePos sourcePos, @NotNull SeqView<Param> params, @NotNull Expr body) {
    return buildNested(sourcePos, params, body, Expr.Lambda::new);
  }

  static @NotNull Expr buildLet(@NotNull SourcePos sourcePos, @NotNull SeqView<LetBind> binds, @NotNull Expr body) {
    return buildNested(sourcePos, binds, body, Expr.Let::new);
  }

  /** convert flattened terms into nested right-associate terms */
  static <P extends SourceNode> @NotNull Expr buildNested(
    @NotNull SourcePos sourcePos,
    @NotNull SeqView<P> params,
    @NotNull Expr body,
    @NotNull TriFunction<SourcePos, P, Expr, Expr> constructor
  ) {
    if (params.isEmpty()) return body;
    var drop = params.drop(1);
    var subPos = body.sourcePos().sourcePosForSubExpr(sourcePos.file(),
      drop.map(SourceNode::sourcePos));
    return constructor.apply(sourcePos, params.first(),
      buildNested(subPos, drop, body, constructor));
  }
}
