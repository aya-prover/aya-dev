// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.distill;

import kala.collection.Seq;
import kala.collection.SeqLike;
import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.DynamicSeq;
import kala.tuple.Unit;
import org.aya.api.distill.DistillerOptions;
import org.aya.api.ref.DefVar;
import org.aya.api.util.Arg;
import org.aya.concrete.Expr;
import org.aya.concrete.Pattern;
import org.aya.concrete.remark.Remark;
import org.aya.concrete.stmt.*;
import org.aya.concrete.visitor.ExprConsumer;
import org.aya.generic.Constants;
import org.aya.generic.Modifier;
import org.aya.generic.ref.PreLevelVar;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Docile;
import org.aya.util.StringEscapeUtil;
import org.aya.util.error.WithPos;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * @author ice1000, kiva
 * @see CoreDistiller
 */
public class ConcreteDistiller extends BaseDistiller<Expr> implements Stmt.Visitor<Unit, Doc> {
  public ConcreteDistiller(@NotNull DistillerOptions options) {
    super(options);
  }

  @Override public @NotNull Doc term(@NotNull Outer outer, @NotNull Expr prexpr) {
    return switch (prexpr) {
      case Expr.ErrorExpr error -> Doc.angled(error.description().toDoc(options));
      case Expr.TupExpr expr -> Doc.parened(Doc.commaList(expr.items().view().map(e -> term(Outer.Free, e))));
      case Expr.BinOpSeq binOpSeq -> {
        var seq = binOpSeq.seq();
        var first = seq.first().expr();
        if (seq.sizeEquals(1)) yield term(outer, first);
        yield visitCalls(false,
          term(Outer.AppSpine, first),
          seq.view().drop(1).map(e -> new Arg<>(e.expr(), e.explicit())), outer,
          options.map.get(DistillerOptions.Key.ShowImplicitArgs)
        );
      }
      case Expr.LitStringExpr expr -> Doc.plain("\"" + StringEscapeUtil.escapeStringCharacters(expr.string()) + "\"");
      case Expr.PiExpr expr -> {
        var data = new boolean[]{false, false};
        expr.last().accept(new ExprConsumer<>() {
          @Override public Unit visitRef(@NotNull Expr.RefExpr ref, Unit unit) {
            if (ref.resolvedVar() == expr.param().ref()) data[0] = true;
            return unit;
          }

          @Override public Unit visitUnresolved(@NotNull Expr.UnresolvedExpr expr, Unit unit) {
            data[1] = true;
            return unit;
          }
        }, Unit.unit());
        Doc doc;
        var last = term(Outer.Codomain, expr.last());
        if (!data[0] && !data[1]) {
          var tyDoc = expr.param().type().toDoc(options);
          doc = Doc.sep(Doc.bracedUnless(tyDoc, expr.param().explicit()), Doc.symbol("->"), last);
        } else {
          doc = Doc.sep(Doc.styled(KEYWORD, Doc.symbol("Pi")), expr.param().toDoc(options), Doc.symbol("->"), last);
        }
        // When outsider is neither a codomain nor non-expression, we need to add parentheses.
        yield checkParen(outer, doc, Outer.BinOp);
      }
      case Expr.AppExpr expr -> {
        var args = DynamicSeq.of(expr.argument());
        var head = Expr.unapp(expr.function(), args);
        var infix = false;
        if (head instanceof Expr.RefExpr ref && ref.resolvedVar() instanceof DefVar var)
          infix = var.isInfix();
        yield visitCalls(infix,
          term(Outer.AppHead, head),
          args.view().map(arg -> new Arg<>(arg.expr(), arg.explicit())), outer,
          options.map.get(DistillerOptions.Key.ShowImplicitArgs));
      }
      case Expr.LMaxExpr expr -> visitCalls(false, Doc.styled(KEYWORD, "lmax"),
        expr.levels().view().map(term -> new Arg<>(term, true)), outer, true);
      case Expr.LamExpr expr -> {
        if (!options.map.get(DistillerOptions.Key.ShowImplicitPats) && !expr.param().explicit()) {
          yield term(outer, expr.body());
        }
        var prelude = DynamicSeq.of(Doc.styled(KEYWORD, Doc.symbol("\\")),
          lambdaParam(expr.param()));
        if (!(expr.body() instanceof Expr.HoleExpr)) {
          prelude.append(Doc.symbol("=>"));
          prelude.append(term(Outer.Free, expr.body()));
        }
        yield checkParen(outer, Doc.sep(prelude), Outer.BinOp);
      }
      case Expr.HoleExpr expr -> {
        if (!expr.explicit()) yield Doc.symbol(Constants.ANONYMOUS_PREFIX);
        var filling = expr.filling();
        if (filling == null) yield Doc.symbol("{??}");
        yield Doc.sep(Doc.symbol("{?"), term(Outer.Free, filling), Doc.symbol("?}"));
      }
      case Expr.ProjExpr expr -> Doc.cat(term(Outer.ProjHead, expr.tup()), Doc.symbol("."),
        Doc.plain(expr.ix().fold(Objects::toString, WithPos::data)));
      case Expr.UnivArgsExpr expr -> Doc.sep(Doc.styled(KEYWORD, "universe"),
        Doc.commaList(expr.univArgs().view().map(Docile::toDoc)));
      case Expr.UnresolvedExpr expr -> Doc.plain(expr.name().join());
      case Expr.RefExpr expr -> {
        var ref = expr.resolvedVar();
        if (ref instanceof DefVar<?, ?> defVar) yield defVar(defVar);
        else if (ref instanceof PreLevelVar levelVar) yield linkRef(levelVar, GENERALIZED);
        else yield varDoc(ref);
      }
      case Expr.LitIntExpr expr -> Doc.plain(String.valueOf(expr.integer()));
      case Expr.RawUnivExpr e -> Doc.styled(KEYWORD, "Type");
      case Expr.RawUnivArgsExpr expr -> Doc.sep(Doc.styled(KEYWORD, "universe"),
        Doc.commaList(expr.univArgs().view().map(e -> term(Outer.Free, e))));
      case Expr.NewExpr expr -> Doc.cblock(
        Doc.sep(Doc.styled(KEYWORD, "new"), term(Outer.Free, expr.struct())),
        2, Doc.vcat(expr.fields().view().map(t ->
          Doc.sep(Doc.symbol("|"), Doc.plain(t.name()),
            Doc.emptyIf(t.bindings().isEmpty(), () ->
              Doc.sep(t.bindings().map(v -> varDoc(v.data())))),
            Doc.plain("=>"), term(Outer.Free, t.body()))
        )));
      case Expr.LSucExpr expr -> visitCalls(false, Doc.styled(KEYWORD, "lsuc"),
        SeqView.of(new Arg<>(expr.expr(), true)), outer, true);
      case Expr.SigmaExpr expr -> checkParen(outer, Doc.sep(
        Doc.styled(KEYWORD, Doc.symbol("Sig")),
        visitTele(expr.params().dropLast(1)),
        Doc.symbol("**"),
        term(Outer.Codomain, expr.params().last().type())), Outer.BinOp);
      // ^ Same as Pi
      case Expr.UnivExpr expr -> {
        var fn = Doc.styled(KEYWORD, "Type");
        if (!options.map.get(DistillerOptions.Key.ShowLevels)) yield fn;
        yield visitCalls(false, fn, (nc, l) -> l.toDoc(options), outer,
          SeqView.of(new Arg<>(o -> expr.level().toDoc(), true)), true);
      }
    };
  }

  public @NotNull Doc visitPattern(@NotNull Pattern pattern, Outer outer) {
    return switch (pattern) {
      case Pattern.Tuple tuple -> {
        var tup = Doc.licit(tuple.explicit(),
          Doc.commaList(tuple.patterns().view().map(p -> visitPattern(p, Outer.Free))));
        yield tuple.as() == null ? tup
          : Doc.sep(tup, Doc.styled(KEYWORD, "as"), linkDef(tuple.as()));
      }
      case Pattern.Absurd absurd -> Doc.bracedUnless(Doc.styled(KEYWORD, "()"), absurd.explicit());
      case Pattern.Bind bind -> Doc.bracedUnless(linkDef(bind.bind()), bind.explicit());
      case Pattern.CalmFace calmFace -> Doc.bracedUnless(Doc.plain(Constants.ANONYMOUS_PREFIX), calmFace.explicit());
      case Pattern.Number number -> Doc.bracedUnless(Doc.plain(String.valueOf(number.number())), number.explicit());
      case Pattern.Ctor ctor -> {
        var name = linkRef(ctor.resolved().data(), CON_CALL);
        var ctorDoc = ctor.params().isEmpty() ? name : Doc.sep(name, visitMaybeCtorPatterns(ctor.params(), Outer.AppSpine, Doc.ALT_WS));
        yield ctorDoc(outer, ctor.explicit(), ctorDoc, ctor.as(), ctor.params().isEmpty());
      }
      case Pattern.BinOpSeq seq -> {
        var param = seq.seq();
        if (param.sizeEquals(1)) yield visitPattern(param.first(), outer);
        var ctorDoc = visitMaybeCtorPatterns(param, Outer.AppSpine, Doc.ALT_WS);
        yield ctorDoc(outer, seq.explicit(), ctorDoc, seq.as(), param.sizeLessThanOrEquals(1));
      }
    };
  }

  private Doc visitMaybeCtorPatterns(SeqLike<Pattern> patterns, Outer outer, @NotNull Doc delim) {
    patterns = options.map.get(DistillerOptions.Key.ShowImplicitPats) ? patterns : patterns.view().filter(Pattern::explicit);
    return Doc.join(delim, patterns.view().map(p -> visitPattern(p, outer)));
  }

  public Doc matchy(Pattern.@NotNull Clause match) {
    var doc = visitMaybeCtorPatterns(match.patterns, Outer.Free, Doc.plain(", "));
    return match.expr.map(e -> Doc.sep(doc, Doc.plain("=>"), term(Outer.Free, e))).getOrDefault(doc);
  }

  private Doc visitAccess(Stmt.@NotNull Accessibility accessibility, Stmt.Accessibility def) {
    if (accessibility == def) return Doc.empty();
    else return Doc.styled(KEYWORD, accessibility.keyword);
  }

  @Override public Doc visitImport(Command.@NotNull Import cmd, Unit unit) {
    var prelude = DynamicSeq.of(Doc.styled(KEYWORD, "import"), Doc.symbol(cmd.path().join()));
    if (cmd.asName() != null) {
      prelude.append(Doc.styled(KEYWORD, "as"));
      prelude.append(Doc.plain(cmd.asName()));
    }
    return Doc.sep(prelude);
  }

  @Override public Doc visitOpen(Command.@NotNull Open cmd, Unit unit) {
    return Doc.sepNonEmpty(
      visitAccess(cmd.accessibility(), Stmt.Accessibility.Private),
      Doc.styled(KEYWORD, "open"),
      Doc.plain(cmd.path().join()),
      Doc.styled(KEYWORD, switch (cmd.useHide().strategy()) {
        case Using -> "using";
        case Hiding -> "hiding";
      }),
      Doc.parened(Doc.commaList(cmd.useHide().list().view().map(Doc::plain)))
    );
  }

  @Override public Doc visitModule(Command.@NotNull Module mod, Unit unit) {
    return Doc.vcat(
      Doc.sep(visitAccess(mod.accessibility(), Stmt.Accessibility.Public),
        Doc.styled(KEYWORD, "module"),
        Doc.plain(mod.name()),
        Doc.plain("{")),
      Doc.nest(2, Doc.vcat(mod.contents().view().map(stmt -> stmt.accept(this, Unit.unit())))),
      Doc.plain("}")
    );
  }

  @Override public Doc visitRemark(@NotNull Remark remark, Unit unit) {
    var literate = remark.literate;
    return literate != null ? literate.toDoc() : Doc.plain(remark.raw);
  }

  @Override public Doc visitData(Decl.@NotNull DataDecl decl, Unit unit) {
    var prelude = DynamicSeq.of(
      visitAccess(decl.accessibility(), Stmt.Accessibility.Public),
      Doc.styled(KEYWORD, "data"),
      linkDef(decl.ref, DATA_CALL),
      visitTele(decl.telescope));
    appendResult(prelude, decl.result);
    return Doc.cat(Doc.sepNonEmpty(prelude),
      Doc.emptyIf(decl.body.isEmpty(), () -> Doc.cat(Doc.line(), Doc.nest(2, Doc.vcat(
        decl.body.view().map(ctor -> visitCtor(ctor, Unit.unit())))))),
      visitBindBlock(decl.bindBlock)
    );
  }

  @Override public Doc visitCtor(Decl.@NotNull DataCtor ctor, Unit unit) {
    var doc = Doc.cblock(Doc.sepNonEmpty(
      coe(ctor.coerce),
      linkDef(ctor.ref, CON_CALL),
      visitTele(ctor.telescope)), 2, visitClauses(ctor.clauses));
    if (ctor.patterns.isNotEmpty()) {
      var pats = Doc.commaList(ctor.patterns.view().map(pattern -> visitPattern(pattern, Outer.Free)));
      return Doc.sep(Doc.symbol("|"), pats, Doc.plain("=>"), doc);
    } else return Doc.sep(Doc.symbol("|"), doc);
  }

  private Doc visitClauses(@NotNull ImmutableSeq<Pattern.Clause> clauses) {
    if (clauses.isEmpty()) return Doc.empty();
    return Doc.vcat(
      clauses.view()
        .map(this::matchy)
        .map(doc -> Doc.sep(Doc.symbol("|"), doc)));
  }

  @Override public Doc visitStruct(@NotNull Decl.StructDecl decl, Unit unit) {
    var prelude = DynamicSeq.of(visitAccess(decl.accessibility(), Stmt.Accessibility.Public),
      Doc.styled(KEYWORD, "struct"),
      linkDef(decl.ref, STRUCT_CALL),
      visitTele(decl.telescope));
    appendResult(prelude, decl.result);
    return Doc.cat(Doc.sepNonEmpty(prelude),
      Doc.emptyIf(decl.fields.isEmpty(), () -> Doc.cat(Doc.line(), Doc.nest(2, Doc.vcat(
        decl.fields.view().map(field -> visitField(field, Unit.unit())))))),
      visitBindBlock(decl.bindBlock)
    );
  }

  private void appendResult(DynamicSeq<Doc> prelude, Expr result) {
    if (result instanceof Expr.HoleExpr) return;
    prelude.append(Doc.symbol(":"));
    prelude.append(term(Outer.Free, result));
  }

  @Override public Doc visitField(Decl.@NotNull StructField field, Unit unit) {
    var doc = DynamicSeq.of(Doc.symbol("|"),
      coe(field.coerce),
      linkDef(field.ref, FIELD_CALL),
      visitTele(field.telescope));
    appendResult(doc, field.result);
    if (field.body.isDefined()) {
      doc.append(Doc.symbol("=>"));
      doc.append(term(Outer.Free, field.body.get()));
    }
    return Doc.cblock(Doc.sepNonEmpty(doc), 2, visitClauses(field.clauses));
  }

  @Override public Doc visitFn(Decl.@NotNull FnDecl decl, Unit unit) {
    var prelude = DynamicSeq.of(visitAccess(decl.accessibility(), Stmt.Accessibility.Public), Doc.styled(KEYWORD, "def"));
    prelude.appendAll(Seq.from(decl.modifiers).view().map(this::visitModifier));
    prelude.append(linkDef(decl.ref, FN_CALL));
    prelude.append(visitTele(decl.telescope));
    appendResult(prelude, decl.result);
    return Doc.cat(Doc.sepNonEmpty(prelude),
      decl.body.fold(expr -> Doc.cat(Doc.ONE_WS, Doc.symbol("=>"), Doc.ONE_WS, term(Outer.Free, expr)),
        clauses -> Doc.cat(Doc.line(), Doc.nest(2, visitClauses(clauses)))),
      visitBindBlock(decl.bindBlock)
    );
  }

  public Doc visitBindBlock(@NotNull BindBlock bindBlock) {
    if (bindBlock == BindBlock.EMPTY) return Doc.empty();
    var loosers = bindBlock.resolvedLoosers().value;
    var tighters = bindBlock.resolvedTighters().value;
    if (loosers.isEmpty() && tighters.isEmpty()) return Doc.empty();

    if (loosers.isEmpty()) return Doc.cat(Doc.line(), Doc.hang(2, Doc.sep(
      Doc.styled(KEYWORD, "bind"), Doc.styled(KEYWORD, "tighter"),
      Doc.commaList(tighters.view().map(BaseDistiller::defVar)))));
    else if (tighters.isEmpty()) return Doc.cat(Doc.line(), Doc.hang(2, Doc.sep(
      Doc.styled(KEYWORD, "bind"), Doc.styled(KEYWORD, "looser"),
      Doc.commaList(loosers.view().map(BaseDistiller::defVar)))));
    return Doc.cat(Doc.line(), Doc.hang(2, Doc.cat(Doc.styled(KEYWORD, "bind"), Doc.braced(Doc.sep(
      Doc.styled(KEYWORD, "tighter"), Doc.commaList(tighters.view().map(BaseDistiller::defVar)),
      Doc.styled(KEYWORD, "looser"), Doc.commaList(loosers.view().map(BaseDistiller::defVar))
    )))));
  }

  @Override public Doc visitPrim(@NotNull Decl.PrimDecl decl, Unit unit) {
    return primDoc(decl.ref);
  }

  private @NotNull Doc visitModifier(@NotNull Modifier modifier) {
    return Doc.styled(KEYWORD, modifier.keyword);
  }

  @Override public Doc visitLevels(Generalize.@NotNull Levels levels, Unit unit) {
    var vars = levels.levels().map(t -> linkDef(t.data(), GENERALIZED));
    return Doc.sep(Doc.styled(KEYWORD, "universe"), Doc.sep(vars));
  }

  @Override public Doc visitVariables(Generalize.@NotNull Variables variables, Unit unit) {
    return Doc.sep(Doc.styled(KEYWORD, "variables"), visitTele(variables.toExpr()));
  }

  @Override public Doc visitExample(Sample.@NotNull Working example, Unit unit) {
    return Doc.sep(Doc.styled(KEYWORD, "example"),
      example.delegate().accept(this, unit));
  }

  @Override public Doc visitCounterexample(Sample.@NotNull Counter example, Unit unit) {
    return Doc.sep(Doc.styled(KEYWORD, "counterexample"),
      example.delegate().accept(this, unit));
  }
}
