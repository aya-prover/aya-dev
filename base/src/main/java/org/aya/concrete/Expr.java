// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.control.Either;
import kala.tuple.Tuple;
import kala.tuple.Tuple2;
import kala.value.MutableValue;
import org.aya.concrete.stmt.QualifiedID;
import org.aya.core.pat.Pat;
import org.aya.distill.BaseDistiller;
import org.aya.distill.ConcreteDistiller;
import org.aya.generic.AyaDocile;
import org.aya.generic.ParamLike;
import org.aya.generic.SortKind;
import org.aya.guest0x0.cubical.Restr;
import org.aya.pretty.doc.Doc;
import org.aya.ref.AnyVar;
import org.aya.ref.DefVar;
import org.aya.ref.LocalVar;
import org.aya.resolve.context.ModuleContext;
import org.aya.resolve.visitor.ExprResolver;
import org.aya.resolve.visitor.StmtShallowResolver;
import org.aya.tyck.ExprTycker;
import org.aya.util.ForLSP;
import org.aya.util.binop.BinOpParser;
import org.aya.util.distill.DistillerOptions;
import org.aya.util.error.SourceNode;
import org.aya.util.error.SourcePos;
import org.aya.util.error.WithPos;
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
   * @see org.aya.concrete.stmt.Stmt#resolve
   * @see StmtShallowResolver
   */
  @Contract(pure = true)
  default Expr resolve(@NotNull ModuleContext context) {
    var exprResolver = new ExprResolver(ExprResolver.RESTRICTIVE);
    exprResolver.enterBody();
    return exprResolver.resolve(this, context);
  }

  @Override default @NotNull Doc toDoc(@NotNull DistillerOptions options) {
    return new ConcreteDistiller(options).term(BaseDistiller.Outer.Free, this);
  }

  @ForLSP
  sealed interface WithTerm extends Expr {
    @NotNull MutableValue<ExprTycker.Result> theCore();
    default @Nullable ExprTycker.Result core() {
      return theCore().get();
    }
  }

  /**
   * @author re-xyr
   */
  record UnresolvedExpr(
    @NotNull SourcePos sourcePos,
    @NotNull QualifiedID name
  ) implements Expr {
    public UnresolvedExpr(@NotNull SourcePos sourcePos, @NotNull String name) {
      this(sourcePos, new QualifiedID(sourcePos, name));
    }

    @Override public @NotNull UnresolvedExpr descent(@NotNull UnaryOperator<@NotNull Expr> f) {
      return this;
    }
  }

  record ErrorExpr(@NotNull SourcePos sourcePos, @NotNull AyaDocile description) implements Expr {
    public ErrorExpr(@NotNull SourcePos sourcePos, @NotNull Doc description) {
      this(sourcePos, options -> description);
    }

    @Override public @NotNull ErrorExpr descent(@NotNull UnaryOperator<@NotNull Expr> f) {
      return this;
    }
  }

  /**
   * @author ice1000
   */
  record HoleExpr(
    @Override @NotNull SourcePos sourcePos,
    boolean explicit,
    @Nullable Expr filling,
    MutableValue<ImmutableSeq<LocalVar>> accessibleLocal
  ) implements Expr {
    public HoleExpr(@NotNull SourcePos sourcePos, boolean explicit, @Nullable Expr filling) {
      this(sourcePos, explicit, filling, MutableValue.create());
    }

    public @NotNull HoleExpr update(@Nullable Expr filling) {
      return filling == filling() ? this : new HoleExpr(sourcePos, explicit, filling);
    }

    @Override public @NotNull HoleExpr descent(@NotNull UnaryOperator<@NotNull Expr> f) {
      return update(filling == null ? null : f.apply(filling));
    }
  }

  /**
   * @author re-xyr
   */
  record AppExpr(
    @NotNull SourcePos sourcePos,
    @NotNull Expr function,
    @NotNull NamedArg argument
  ) implements Expr {
    public @NotNull AppExpr update(@NotNull Expr function, @NotNull NamedArg argument) {
      return function == function() && argument == argument() ? this : new AppExpr(sourcePos, function, argument);
    }

    @Override public @NotNull AppExpr descent(@NotNull UnaryOperator<@NotNull Expr> f) {
      return update(f.apply(function), argument.descent(f));
    }
  }

  static @NotNull Expr unapp(@NotNull Expr expr, @Nullable MutableList<NamedArg> args) {
    while (expr instanceof AppExpr app) {
      if (args != null) args.append(app.argument);
      expr = app.function;
    }
    if (args != null) args.reverse();
    return expr;
  }

  /**
   * @author AustinZhu
   */
  record NamedArg(boolean explicit, @Nullable String name, @NotNull Expr expr)
    implements AyaDocile, SourceNode, BinOpParser.Elem<Expr> {

    public NamedArg(boolean explicit, @NotNull Expr expr) {
      this(explicit, null, expr);
    }


    public @NotNull NamedArg update(@NotNull Expr expr) {
      return expr == expr() ? this : new NamedArg(explicit, name, expr);
    }

    public @NotNull NamedArg descent(@NotNull Function<@NotNull Expr, @NotNull Expr> f) {
      return update(f.apply(expr));
    }

    @Override public @NotNull Doc toDoc(@NotNull DistillerOptions options) {
      var doc = name == null ? expr.toDoc(options) :
        Doc.braced(Doc.sep(Doc.plain(name), Doc.symbol("=>"), expr.toDoc(options)));
      return Doc.bracedUnless(doc, explicit);
    }

    @Override public @NotNull SourcePos sourcePos() {
      return expr.sourcePos();
    }
  }

  /**
   * @author re-xyr
   */
  record PiExpr(
    @NotNull SourcePos sourcePos,
    boolean co,
    @NotNull Param param,
    @NotNull Expr last
  ) implements Expr {
    public @NotNull PiExpr update(@NotNull Param param, @NotNull Expr last) {
      return param == param() && last == last() ? this : new PiExpr(sourcePos, co, param, last);
    }

    @Override public @NotNull PiExpr descent(@NotNull UnaryOperator<@NotNull Expr> f) {
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
  ) {
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
  record LamExpr(
    @NotNull SourcePos sourcePos,
    @NotNull Param param,
    @NotNull Expr body
  ) implements Expr {
    public @NotNull LamExpr update(@NotNull Param param, @NotNull Expr body) {
      return param == param() && body == body() ? this : new LamExpr(sourcePos, param, body);
    }

    @Override public @NotNull LamExpr descent(@NotNull UnaryOperator<@NotNull Expr> f) {
      return update(param.descent(f), f.apply(body));
    }
  }

  /**
   * @author re-xyr
   */
  record SigmaExpr(
    @NotNull SourcePos sourcePos,
    boolean co,
    @NotNull ImmutableSeq<@NotNull Param> params
  ) implements Expr {
    public @NotNull SigmaExpr update(@NotNull ImmutableSeq<@NotNull Param> params) {
      return params.sameElements(params(), true) ? this : new SigmaExpr(sourcePos, co, params);
    }

    @Override public @NotNull SigmaExpr descent(@NotNull UnaryOperator<@NotNull Expr> f) {
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
  record RefExpr(
    @NotNull SourcePos sourcePos,
    @NotNull AnyVar resolvedVar,
    @NotNull MutableValue<ExprTycker.Result> theCore
  ) implements Expr, WithTerm {
    public RefExpr(@NotNull SourcePos sourcePos, @NotNull AnyVar resolvedVar) {
      this(sourcePos, resolvedVar, MutableValue.create());
    }

    @Override public @NotNull RefExpr descent(@NotNull UnaryOperator<@NotNull Expr> f) {
      return this;
    }
  }

  record LiftExpr(@NotNull SourcePos sourcePos, @NotNull Expr expr, int lift) implements Expr {
    public @NotNull LiftExpr update(@NotNull Expr expr) {
      return expr == expr() ? this : new LiftExpr(sourcePos, expr, lift);
    }

    @Override public @NotNull LiftExpr descent(@NotNull UnaryOperator<@NotNull Expr> f) {
      return update(f.apply(expr));
    }
  }

  /**
   * @author tsao-chi
   */
  record RawSortExpr(@NotNull SourcePos sourcePos, @NotNull SortKind kind) implements Expr {
    @Override public @NotNull RawSortExpr descent(@NotNull UnaryOperator<@NotNull Expr> f) {
      return this;
    }
  }

  sealed interface SortExpr extends Expr {
    int lift();

    SortKind kind();

    @Override default @NotNull SortExpr descent(@NotNull UnaryOperator<@NotNull Expr> f) {
      return this;
    }
  }

  record TypeExpr(@NotNull SourcePos sourcePos, @Override int lift) implements SortExpr {
    @Override public SortKind kind() {
      return SortKind.Type;
    }
  }

  record SetExpr(@NotNull SourcePos sourcePos, @Override int lift) implements SortExpr {
    @Override public SortKind kind() {
      return SortKind.Set;
    }
  }

  record PropExpr(@NotNull SourcePos sourcePos) implements SortExpr {
    @Override public int lift() {
      return 0;
    }

    @Override public SortKind kind() {
      return SortKind.Prop;
    }
  }

  record ISetExpr(@NotNull SourcePos sourcePos) implements SortExpr {
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
  record TupExpr(
    @Override @NotNull SourcePos sourcePos,
    @NotNull ImmutableSeq<@NotNull Expr> items
  ) implements Expr {
    public @NotNull TupExpr update(@NotNull ImmutableSeq<@NotNull Expr> items) {
      return items.sameElements(items(), true) ? this : new TupExpr(sourcePos, items);
    }

    @Override public @NotNull TupExpr descent(@NotNull UnaryOperator<@NotNull Expr> f) {
      return update(items.map(f));
    }
  }

  /**
   * @param resolvedVar will be set to the field's DefVar during resolving
   * @author re-xyr
   */
  record ProjExpr(
    @NotNull SourcePos sourcePos,
    @NotNull Expr tup,
    @NotNull Either<Integer, QualifiedID> ix,
    @Nullable AnyVar resolvedVar,
    @NotNull MutableValue<ExprTycker.Result> theCore
  ) implements Expr, WithTerm {
    public ProjExpr(
      @NotNull SourcePos sourcePos, @NotNull Expr tup,
      @NotNull Either<Integer, QualifiedID> ix
    ) {
      this(sourcePos, tup, ix, null, MutableValue.create());
    }

    public @NotNull ProjExpr update(@NotNull Expr tup) {
      return tup == tup() ? this : new ProjExpr(sourcePos, tup, ix, resolvedVar, theCore);
    }

    @Override public @NotNull ProjExpr descent(@NotNull UnaryOperator<@NotNull Expr> f) {
      return update(f.apply(tup));
    }
  }

  /** undesugared overloaded projection as coercion syntax */
  record RawProjExpr(
    @NotNull SourcePos sourcePos,
    @NotNull Expr tup,
    @NotNull QualifiedID id,
    @Nullable AnyVar resolvedVar,
    @Nullable Expr coeLeft,
    @Nullable Expr restr
  ) implements Expr {
    public @NotNull RawProjExpr update(@NotNull Expr tup, @Nullable Expr coeLeft, @Nullable Expr restr) {
      return tup == tup() && coeLeft == coeLeft() && restr == restr() ? this
        : new RawProjExpr(sourcePos, tup, id, resolvedVar, coeLeft, restr);
    }

    @Override public @NotNull RawProjExpr descent(@NotNull UnaryOperator<@NotNull Expr> f) {
      return update(f.apply(tup), coeLeft == null ? null : f.apply(coeLeft), restr == null ? null : f.apply(restr));
    }
  }

  /**
   * calls to {@link org.aya.core.def.PrimDef.ID#COE}, desugared from {@link ProjExpr} for simplicity
   *
   * @param resolvedVar will be set to the primitive coe's DefVar during resolving
   * @param restr       The cofibration under which the type should be constant
   */
  record CoeExpr(
    @Override @NotNull SourcePos sourcePos,
    @NotNull QualifiedID id,
    @NotNull DefVar<?, ?> resolvedVar,
    @NotNull Expr type,
    @NotNull Expr restr
  ) implements Expr {
    @NotNull CoeExpr update(@NotNull Expr type, @NotNull Expr restr) {
      return type == type() && restr == restr() ? this : new CoeExpr(sourcePos, id, resolvedVar, type, restr);
    }

    @Override public @NotNull CoeExpr descent(@NotNull UnaryOperator<@NotNull Expr> f) {
      return update(f.apply(type), f.apply(restr));
    }
  }

  record NewExpr(
    @NotNull SourcePos sourcePos,
    @NotNull Expr struct,
    @NotNull ImmutableSeq<Field> fields
  ) implements Expr {
    @NotNull NewExpr update(@NotNull Expr struct, @NotNull ImmutableSeq<Field> fields) {
      return struct == struct() && fields.sameElements(fields(), true) ? this
        : new NewExpr(sourcePos, struct, fields);
    }

    @Override public @NotNull NewExpr descent(@NotNull UnaryOperator<@NotNull Expr> f) {
      return update(f.apply(struct), fields.map(field -> field.descent(f)));
    }
  }

  /**
   * @param resolvedField will be modified during tycking for LSP to function properly.
   */
  record Field(
    @NotNull WithPos<String> name,
    @NotNull ImmutableSeq<WithPos<LocalVar>> bindings,
    @NotNull Expr body,
    @ForLSP @NotNull MutableValue<AnyVar> resolvedField
  ) {
    public @NotNull Field update(@NotNull Expr body) {
      return body == body() ? this : new Field(name, bindings, body, resolvedField);
    }

    public @NotNull Field descent(@NotNull UnaryOperator<@NotNull Expr> f) {
      return update(f.apply(body));
    }
  }

  record Match(
    @NotNull SourcePos sourcePos,
    @NotNull ImmutableSeq<Expr> discriminant,
    @NotNull ImmutableSeq<Pattern.Clause> clauses
  ) implements Expr {
    @NotNull Match update(@NotNull ImmutableSeq<Expr> discriminant, @NotNull ImmutableSeq<Pattern.Clause> clauses) {
      return discriminant.sameElements(discriminant(), true) && clauses.sameElements(clauses(), true) ? this
        : new Match(sourcePos, discriminant, clauses);
    }

    @Override public @NotNull Match descent(@NotNull UnaryOperator<@NotNull Expr> f) {
      return update(discriminant.map(f), clauses);
    }

    public @NotNull Match descent(@NotNull UnaryOperator<@NotNull Expr> f, @NotNull UnaryOperator<@NotNull Pattern> g) {
      return update(discriminant.map(f), clauses.map(cl -> cl.descent(f, g)));
    }
  }

  /**
   * @author kiva
   */
  record LitIntExpr(@NotNull SourcePos sourcePos, int integer) implements Expr {
    @Override public @NotNull LitIntExpr descent(@NotNull UnaryOperator<@NotNull Expr> f) {
      return this;
    }
  }

  record LitStringExpr(@NotNull SourcePos sourcePos, @NotNull String string) implements Expr {
    @Override public @NotNull LitStringExpr descent(@NotNull UnaryOperator<@NotNull Expr> f) {
      return this;
    }
  }

  record MetaPat(@NotNull SourcePos sourcePos, Pat.Meta meta) implements Expr {
    @Override public @NotNull MetaPat descent(@NotNull UnaryOperator<@NotNull Expr> f) {
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
    @NotNull BinOpSeq update(@NotNull ImmutableSeq<NamedArg> seq) {
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
      return clauses.allMatchWith(clauses(), (l, r) -> l._1 == r._1 && l._2 == r._2) ? this
        : new PartEl(sourcePos, clauses);
    }

    @Override public @NotNull PartEl descent(@NotNull UnaryOperator<@NotNull Expr> f) {
      return update(clauses.map(cls -> Tuple.of(f.apply(cls._1), f.apply(cls._2))));
    }
  }

  /** generalized path type */
  record Path(
    @NotNull SourcePos sourcePos,
    @NotNull ImmutableSeq<LocalVar> params,
    @NotNull Expr type,
    @NotNull PartEl partial
  ) implements Expr {
    @NotNull Path update(@NotNull Expr type, @NotNull PartEl partial) {
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
    boolean explicit
  ) implements ParamLike<Expr> {
    public Param(@NotNull Param param, @NotNull Expr type) {
      this(param.sourcePos, param.ref, type, param.explicit);
    }

    public Param(@NotNull SourcePos sourcePos, @NotNull LocalVar var, boolean explicit) {
      this(sourcePos, var, new HoleExpr(sourcePos, false, null), explicit);
    }

    public @NotNull Param update(@NotNull Expr type) {
      return type == type() ? this : new Param(sourcePos, ref, type, explicit);
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
      @NotNull ElementList update(@NotNull ImmutableSeq<Expr> exprList) {
        return exprList.sameElements(exprList(), true) ? this : new ElementList(exprList);
      }

      @NotNull ElementList descent(@NotNull UnaryOperator<@NotNull Expr> f) {
        return update(exprList.map(f));
      }
    }

    /**
     * <h1>Array Comp(?)</h1>
     * <p>
     * The (half?) primary part of {@link Array}<br/>
     * For example: <code>[x * y | x <- [1, 2, 3], y <- [4, 5, 6]]</code>
     *
     * @param generator <code>x * y</code> part above
     * @param binds     <code>x <- [1, 2, 3], y <- [4, 5, 6]</code> part above
     * @param bindName  the bind (>>=) function, it is {@link org.aya.generic.Constants}.monadBind in default
     * @param pureName  the pure (return) function, it is {@link org.aya.generic.Constants}.functorPure in default
     * @apiNote a ArrayCompBlock will be desugar to a do-block. For the example above, it will be desugared to
     * <pre>
     *     do
     *       x <- [1, 2, 3]
     *       y <- [4, 5, 6]
     *       return x * y
     *   </pre>
     */
    public record CompBlock(
      @NotNull Expr generator,
      @NotNull ImmutableSeq<DoBind> binds,
      @NotNull Expr bindName,
      @NotNull Expr pureName
    ) {
      @NotNull CompBlock update(@NotNull Expr generator, @NotNull ImmutableSeq<DoBind> binds, @NotNull Expr bindName, @NotNull Expr pureName) {
        return generator == generator() && binds.sameElements(binds(), true) && bindName == bindName() && pureName == pureName() ? this
          : new CompBlock(generator, binds, bindName, pureName);
      }

      @NotNull CompBlock descent(@NotNull UnaryOperator<@NotNull Expr> f) {
        return update(f.apply(generator), binds.map(bind -> bind.descent(f)), f.apply(bindName), f.apply(pureName));
      }
    }

    /**
     * helper constructor, also find constructor calls easily in IDE
     */
    public static Expr.Array newList(
      @NotNull SourcePos sourcePos,
      @NotNull ImmutableSeq<Expr> exprs) {
      return new Expr.Array(
        sourcePos,
        Either.right(new ElementList(exprs))
      );
    }

    public static Expr.Array newGenerator(
      @NotNull SourcePos sourcePos,
      @NotNull Expr generator,
      @NotNull ImmutableSeq<DoBind> bindings,
      @NotNull Expr bindName,
      @NotNull Expr pureName) {
      return new Expr.Array(
        sourcePos,
        Either.left(new CompBlock(
          generator, bindings, bindName, pureName
        ))
      );
    }
  }
}
