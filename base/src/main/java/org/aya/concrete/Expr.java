// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.control.Either;
import kala.control.Option;
import kala.tuple.Tuple2;
import kala.value.MutableValue;
import org.aya.concrete.stmt.QualifiedID;
import org.aya.concrete.visitor.ExprView;
import org.aya.core.pat.Pat;
import org.aya.distill.BaseDistiller;
import org.aya.distill.ConcreteDistiller;
import org.aya.generic.AyaDocile;
import org.aya.generic.ParamLike;
import org.aya.generic.SortKind;
import org.aya.guest0x0.cubical.Restr;
import org.aya.pretty.doc.Doc;
import org.aya.ref.AnyVar;
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

/**
 * @author re-xyr
 */
public sealed interface Expr extends AyaDocile, SourceNode, Restr.TermLike<Expr> {
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

  }

  record ErrorExpr(@NotNull SourcePos sourcePos, @NotNull AyaDocile description) implements Expr {
    public ErrorExpr(@NotNull SourcePos sourcePos, @NotNull Doc description) {
      this(sourcePos, options -> description);
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

  }

  /**
   * @author re-xyr
   */
  record AppExpr(
    @NotNull SourcePos sourcePos,
    @NotNull Expr function,
    @NotNull NamedArg argument
  ) implements Expr {}

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
    @NotNull Expr.Param param,
    @NotNull Expr last
  ) implements Expr {}

  record Do(
    @NotNull SourcePos sourcePos,
    @NotNull Expr bindName,
    @NotNull ImmutableSeq<DoBind> binds
  ) implements Expr {}

  record DoBind(
    @NotNull SourcePos sourcePos,
    @NotNull LocalVar var,
    @NotNull Expr expr
  ) {}

  record Idiom(
    @NotNull SourcePos sourcePos,
    @NotNull Expr.IdiomNames names,
    @NotNull ImmutableSeq<Expr> barredApps
  ) implements Expr {}

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
    @NotNull Expr.Param param,
    @NotNull Expr body
  ) implements Expr {}

  /**
   * @author re-xyr
   */
  record SigmaExpr(
    @NotNull SourcePos sourcePos,
    boolean co,
    @NotNull ImmutableSeq<@NotNull Param> params
  ) implements Expr {}

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

  }

  record LiftExpr(@NotNull SourcePos sourcePos, @NotNull Expr expr, int lift) implements Expr {}

  /**
   * @author tsao-chi
   */
  record RawSortExpr(@NotNull SourcePos sourcePos, @NotNull SortKind kind) implements Expr {}

  sealed interface SortExpr extends Expr {
    int lift();

    SortKind kind();
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
  ) implements Expr {}

  /**
   * @author re-xyr
   */
  record ProjExpr(
    @NotNull SourcePos sourcePos,
    @NotNull Expr tup,
    @NotNull Either<Integer, ProjOrCoe> ix,
    @NotNull MutableValue<ExprTycker.Result> theCore
  ) implements Expr, WithTerm {
    public ProjExpr(
      @NotNull SourcePos sourcePos, @NotNull Expr tup,
      @NotNull Either<Integer, ProjOrCoe> ix
    ) {
      this(sourcePos, tup, ix, MutableValue.create());
    }
  }

  /**
   * Overloaded projection as coercion syntax
   *
   * @param resolvedIx will be set to the field's DefVar during resolving
   * @param freeze     used when the projection is overloaded as coercion with freezing.
   */
  record ProjOrCoe(
    @NotNull QualifiedID id,
    @NotNull Option<Expr> freeze,
    @Nullable AnyVar resolvedIx
  ) {}

  /** calls to {@link org.aya.core.def.PrimDef.ID#COE}, desugared from {@link ProjExpr} for simplicity */
  record CoeExpr(
    @Override @NotNull SourcePos sourcePos,
    @NotNull Expr expr,
    @NotNull Expr.ProjOrCoe coeData
  ) implements Expr {
  }

  record NewExpr(
    @NotNull SourcePos sourcePos,
    @NotNull Expr struct,
    @NotNull ImmutableSeq<Field> fields
  ) implements Expr {}

  /**
   * @param resolvedField will be modified during tycking for LSP to function properly.
   */
  record Field(
    @NotNull WithPos<String> name,
    @NotNull ImmutableSeq<WithPos<LocalVar>> bindings,
    @NotNull Expr body,
    @ForLSP @NotNull MutableValue<AnyVar> resolvedField
  ) {}

  /**
   * @author kiva
   */
  record LitIntExpr(@NotNull SourcePos sourcePos, int integer) implements Expr {}

  record LitStringExpr(@NotNull SourcePos sourcePos, @NotNull String string) implements Expr {}

  record MetaPat(@NotNull SourcePos sourcePos, Pat.Meta meta) implements Expr {}

  /**
   * @author kiva
   */
  record BinOpSeq(
    @NotNull SourcePos sourcePos,
    @NotNull ImmutableSeq<NamedArg> seq
  ) implements Expr {}

  /** partial element */
  record PartEl(
    @NotNull SourcePos sourcePos,
    @NotNull ImmutableSeq<Tuple2<Expr, Expr>> clauses
  ) implements Expr {}

  /** generalized path type */
  record Path(
    @NotNull SourcePos sourcePos,
    @NotNull ImmutableSeq<LocalVar> params,
    @NotNull Expr type,
    @NotNull PartEl partial
  ) implements Expr {
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

    public @NotNull Expr.Param mapExpr(@NotNull Function<@NotNull Expr, @NotNull Expr> mapper) {
      return new Param(sourcePos, ref, mapper.apply(type), explicit);
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
    /**
     * @param nilCtor  the Nil constructor, it is {@link org.aya.generic.Constants}.listNil in default
     * @param consCtor the Cons constructor, it is {@link org.aya.generic.Constants}.listCons in default
     */
    public record ElementList(
      @NotNull ImmutableSeq<Expr> exprList,
      @NotNull Expr nilCtor,
      @NotNull Expr consCtor
    ) {}

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
    ) {}

    /**
     * helper constructor, also find constructor calls easily in IDE
     */
    public static Expr.Array newList(
      @NotNull SourcePos sourcePos,
      @NotNull ImmutableSeq<Expr> exprs,
      @NotNull Expr nilCtor,
      @NotNull Expr consCtor) {
      return new Expr.Array(
        sourcePos,
        Either.right(new ElementList(
          exprs, nilCtor, consCtor
        ))
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

  default @NotNull ExprView view() {
    return () -> this;
  }
}
