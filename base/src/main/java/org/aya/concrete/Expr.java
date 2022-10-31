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

/**
 * @author re-xyr
 */
public sealed interface Expr extends AyaDocile, SourceNode, Restr.TermLike<Expr> {
  default @NotNull Expr descent(@NotNull Function<@NotNull Expr, @NotNull Expr> f) {
    return switch (this) {
      case Expr.RefExpr ref -> ref;
      case Expr.UnresolvedExpr unresolved -> unresolved;
      case Expr.LamExpr lam -> {
        var param = lam.param().descent(f);
        var body = f.apply(lam.body());
        if (param == lam.param() && body == lam.body()) yield lam;
        yield new Expr.LamExpr(lam.sourcePos(), param, body);
      }
      case Expr.PiExpr pi -> {
        var param = pi.param().descent(f);
        var last = f.apply(pi.last());
        if (param == pi.param() && last == pi.last()) yield pi;
        yield new Expr.PiExpr(pi.sourcePos(), param, last);
      }
      case Expr.SigmaExpr sigma -> {
        var params = sigma.params().map(param -> param.descent(f));
        if (params.sameElements(sigma.params(), true)) yield sigma;
        yield new Expr.SigmaExpr(sigma.sourcePos(), params);
      }
      case Expr.RawSortExpr rawType -> rawType;
      case Expr.LiftExpr lift -> {
        var inner = f.apply(lift.expr());
        if (inner == lift.expr()) yield lift;
        yield new Expr.LiftExpr(lift.sourcePos(), inner, lift.lift());
      }
      case Expr.SortExpr univ -> univ;
      case Expr.AppExpr(var pos,var fun,var a) -> {
        var func = f.apply(fun);
        var arg = a.descent(f);
        if (func == fun && arg == a) yield this;
        yield new Expr.AppExpr(pos, func, arg);
      }
      case Expr.HoleExpr hole -> {
        var filling = hole.filling();
        var committed = filling != null ? f.apply(filling) : null;
        if (committed == filling) yield hole;
        yield new Expr.HoleExpr(hole.sourcePos(), hole.explicit(), committed, hole.accessibleLocal());
      }
      case Expr.TupExpr tup -> {
        var items = tup.items().map(f);
        if (items.sameElements(tup.items(), true)) yield tup;
        yield new Expr.TupExpr(tup.sourcePos(), items);
      }
      case Expr.ProjExpr proj -> {
        var tup = f.apply(proj.tup());
        if (tup == proj.tup()) yield proj;
        yield new Expr.ProjExpr(proj.sourcePos(), tup, proj.ix(), proj.resolvedVar(), proj.theCore());
      }
      case Expr.RawProjExpr proj -> {
        var tup = f.apply(proj.tup());
        var coeLeft = proj.coeLeft() != null ? f.apply(proj.coeLeft()) : null;
        var restr = proj.restr() != null ? f.apply(proj.restr()) : null;
        if (tup == proj.tup() && coeLeft == proj.coeLeft() && restr == proj.restr()) yield proj;
        yield new Expr.RawProjExpr(proj.sourcePos(), tup, proj.id(), proj.resolvedVar(), coeLeft, restr);
      }
      case Expr.CoeExpr coe -> {
        var type = f.apply(coe.type());
        var restr = f.apply(coe.restr());
        if (type == coe.type() && restr == coe.restr()) yield coe;
        yield new Expr.CoeExpr(coe.sourcePos(), coe.id(), coe.resolvedVar(), type, restr);
      }
      case Expr.NewExpr neu -> {
        var struct = f.apply(neu.struct());
        var fields = neu.fields().map(field ->
          new Expr.Field(field.name(), field.bindings(), f.apply(field.body()), field.resolvedField()));
        if (struct == neu.struct() && fields.sameElements(neu.fields(), true)) yield neu;
        yield new Expr.NewExpr(neu.sourcePos(), struct, fields);
      }
      case Expr.PartEl el -> {
        var clauses = el.clauses().map(cls -> Tuple.of(f.apply(cls._1), f.apply(cls._2)));
        if (clauses.allMatchWith(el.clauses(), (l, r) ->
          l._1 == r._1 && l._2 == r._2)) yield el;
        yield new Expr.PartEl(el.sourcePos(), clauses);
      }
      case Expr.Path path -> {
        var partial = (PartEl) path.partial().descent(f);
        var type = f.apply(path.type());
        if (partial == path.partial() && type == path.type()) yield path;
        yield new Expr.Path(path.sourcePos(), path.params(), type, partial);
      }
      case Expr.LitIntExpr litInt -> litInt;
      case Expr.LitStringExpr litStr -> litStr;
      case Expr.BinOpSeq binOpSeq -> {
        var seq = binOpSeq.seq().map(arg -> arg.descent(f));
        if (seq.sameElements(binOpSeq.seq(), true)) yield binOpSeq;
        yield new Expr.BinOpSeq(binOpSeq.sourcePos(), seq);
      }
      case Expr.ErrorExpr error -> error;
      case Expr.MetaPat meta -> meta;
      case Expr.Idiom idiom -> {
        var newInner = idiom.barredApps().map(f);
        var newNames = idiom.names().fmap(f);
        if (newInner.sameElements(idiom.barredApps()) && newNames.identical(idiom.names())) yield idiom;
        yield new Expr.Idiom(idiom.sourcePos(), newNames, newInner);
      }
      case Expr.Do doNotation -> {
        var lamExprs = doNotation.binds().map(x ->
          new Expr.DoBind(x.sourcePos(), x.var(), f.apply(x.expr())));
        var bindName = f.apply(doNotation.bindName());
        if (lamExprs.sameElements(doNotation.binds()) && bindName == doNotation.bindName())
          yield doNotation;
        yield new Expr.Do(doNotation.sourcePos(), bindName, lamExprs);
      }
      case Expr.Array arrayExpr -> arrayExpr.arrayBlock().fold(
        left -> {
          var generator = f.apply(left.generator());
          var bindings = left.binds().map(binding ->
            new Expr.DoBind(binding.sourcePos(), binding.var(), f.apply(binding.expr()))
          );
          var bindName = f.apply(left.bindName());
          var pureName = f.apply(left.pureName());

          if (generator == left.generator() && bindings.sameElements(left.binds()) && bindName == left.bindName() && pureName == left.pureName()) {
            return arrayExpr;
          } else {
            return Expr.Array.newGenerator(arrayExpr.sourcePos(), generator, bindings, bindName, pureName);
          }
        },
        right -> {
          var exprs = right.exprList().map(f);

          if (exprs.sameElements(right.exprList())) {
            return arrayExpr;
          } else {
            return Expr.Array.newList(arrayExpr.sourcePos(), exprs);
          }
        }
      );
    };
  }
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

    public @NotNull NamedArg descent(@NotNull Function<@NotNull Expr, @NotNull Expr> f) {
      var expr = f.apply(expr());
      if (expr == expr()) return this;
      return new NamedArg(explicit(), name(), expr);
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

    public @NotNull Param descent(@NotNull Function<@NotNull Expr, @NotNull Expr> f) {
      var type = f.apply(type());
      if (type == type()) return this;
      return new Param(this, type);
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
    public record ElementList(@NotNull ImmutableSeq<Expr> exprList) {}

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
