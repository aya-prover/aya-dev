// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.distill;

import kala.collection.Seq;
import kala.collection.SeqLike;
import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.range.primitive.IntRange;
import kala.tuple.Unit;
import org.aya.concrete.Expr;
import org.aya.concrete.Pattern;
import org.aya.concrete.remark.Remark;
import org.aya.concrete.stmt.*;
import org.aya.concrete.visitor.ExprTraversal;
import org.aya.generic.Arg;
import org.aya.generic.Constants;
import org.aya.generic.Modifier;
import org.aya.pretty.doc.Doc;
import org.aya.ref.DefVar;
import org.aya.ref.LocalVar;
import org.aya.util.StringEscapeUtil;
import org.aya.util.distill.DistillerOptions;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * @author ice1000, kiva
 * @see CoreDistiller
 */
public class ConcreteDistiller extends BaseDistiller<Expr> {
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
      case Expr.LitStringExpr expr -> Doc.plain('"' + StringEscapeUtil.unescapeStringCharacters(expr.string()) + '"');
      case Expr.PiExpr expr -> {
        var data = new boolean[]{false, false};
        new ExprTraversal<Unit>() {
          @Override public @NotNull Expr visitExpr(@NotNull Expr e, Unit unit) {
            switch (e) {
              case Expr.RefExpr ref when ref.resolvedVar() == expr.param().ref() -> data[0] = true;
              case Expr.UnresolvedExpr unresolved -> data[1] = true;
              default -> {}
            }
            return ExprTraversal.super.visitExpr(e, unit);
          }
        }.visitExpr(expr.last(), Unit.unit());
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
        var args = MutableList.of(expr.argument());
        var head = Expr.unapp(expr.function(), args);
        var infix = false;
        if (head instanceof Expr.RefExpr ref && ref.resolvedVar() instanceof DefVar<?, ?> var)
          infix = var.isInfix();
        yield visitCalls(infix,
          term(Outer.AppHead, head),
          args.view().map(arg -> new Arg<>(arg.expr(), arg.explicit())), outer,
          options.map.get(DistillerOptions.Key.ShowImplicitArgs));
      }
      case Expr.LamExpr expr -> {
        if (!options.map.get(DistillerOptions.Key.ShowImplicitPats) && !expr.param().explicit()) {
          yield term(outer, expr.body());
        }
        var prelude = MutableList.of(Doc.styled(KEYWORD, Doc.symbol("\\")),
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
        Doc.plain(expr.ix().fold(Objects::toString, QualifiedID::join)));
      case Expr.UnresolvedExpr expr -> Doc.plain(expr.name().join());
      case Expr.RefExpr expr -> {
        var ref = expr.resolvedVar();
        if (ref instanceof DefVar<?, ?> defVar) yield defVar(defVar);
        else yield varDoc(ref);
      }
      case Expr.LitIntExpr expr -> Doc.plain(String.valueOf(expr.integer()));
      case Expr.RawSortExpr e -> Doc.styled(KEYWORD, e.kind().name());
      case Expr.NewExpr expr -> Doc.cblock(
        Doc.sep(Doc.styled(KEYWORD, "new"), term(Outer.Free, expr.struct())),
        2, Doc.vcat(expr.fields().view().map(t ->
          Doc.sep(Doc.symbol("|"), Doc.styled(FIELD_CALL, t.name().data()),
            Doc.emptyIf(t.bindings().isEmpty(), () ->
              Doc.sep(t.bindings().map(v -> varDoc(v.data())))),
            Doc.plain("=>"), term(Outer.Free, t.body()))
        )));
      case Expr.SigmaExpr expr -> checkParen(outer, Doc.sep(
        Doc.styled(KEYWORD, Doc.symbol("Sig")),
        visitTele(expr.params().dropLast(1)),
        Doc.symbol("**"),
        term(Outer.Codomain, expr.params().last().type())), Outer.BinOp);
      // ^ Same as Pi
      case Expr.SortExpr expr -> {
        var fn = Doc.styled(KEYWORD, expr.kind().name());
        if (!expr.kind().hasLevel()) yield fn;
        yield visitCalls(false, fn, (nc, l) -> l.toDoc(options), outer,
          SeqView.of(new Arg<>(o -> Doc.plain(String.valueOf(expr.lift())), true)), true);
      }
      case Expr.LiftExpr expr -> Doc.sep(Seq
        .from(IntRange.closed(1, expr.lift()).iterator()).view()
        .map($ -> Doc.styled(KEYWORD, Doc.symbol("ulift")))
        .appended(term(Outer.Lifted, expr.expr())));
      case Expr.MetaPat metaPat -> metaPat.meta().toDoc(options);
      case Expr.PartEl el -> Doc.sep(Doc.symbol("{|"),
        Doc.join(Doc.spaced(Doc.symbol("|")), el.clauses().map(cl -> Doc.sep(
          cl._1.toDoc(options), Doc.symbol(":="), cl._2.toDoc(options))
        )),
        Doc.symbol("|}"));
      case Expr.Path path -> Doc.sep(
        Doc.symbol("[|"),
        Doc.commaList(path.params().map(BaseDistiller::linkDef)),
        Doc.symbol("|]"),
        path.type().toDoc(options),
        path.partial().toDoc(options)
      );
      case Expr.Idiom idiom -> Doc.wrap(
        "(|", "|)",
        Doc.join(Doc.symbol("|"), idiom.barredApps().view()
          .map(app -> term(Outer.Free, app)))
      );
      case Expr.Do doExpr -> {
        var doBlockDoc = doExpr.binds().map(bind -> {
          if (bind.var() == LocalVar.IGNORED) {
            return term(Outer.Free, bind.expr());
          } else {
            return visitDoBind(bind);
          }
        });

        // Either not flat (single line) or full flat
        yield Doc.flatAltBracedBlock(
          Doc.commaList(doBlockDoc),
          Doc.vcommaList(
            doBlockDoc.map(x -> Doc.nest(2, x))     // TODO[hoshino]: constant indent?
          )
        );
      }
    };
  }

  public @NotNull Doc pattern(@NotNull Pattern pattern, Outer outer) {
    return switch (pattern) {
      case Pattern.Tuple tuple -> {
        var tup = Doc.licit(tuple.explicit(),
          Doc.commaList(tuple.patterns().view().map(p -> pattern(p, Outer.Free))));
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
        if (param.sizeEquals(1)) yield pattern(param.first(), outer);
        var ctorDoc = visitMaybeCtorPatterns(param, Outer.AppSpine, Doc.ALT_WS);
        yield ctorDoc(outer, seq.explicit(), ctorDoc, seq.as(), param.sizeLessThanOrEquals(1));
      }
    };
  }

  private Doc visitMaybeCtorPatterns(SeqLike<Pattern> patterns, Outer outer, @NotNull Doc delim) {
    patterns = options.map.get(DistillerOptions.Key.ShowImplicitPats) ? patterns : patterns.view().filter(Pattern::explicit);
    return Doc.join(delim, patterns.view().map(p -> pattern(p, outer)));
  }

  public Doc matchy(Pattern.@NotNull Clause match) {
    var doc = visitMaybeCtorPatterns(match.patterns, Outer.Free, Doc.plain(", "));
    return match.expr.map(e -> Doc.sep(doc, Doc.plain("=>"), term(Outer.Free, e))).getOrDefault(doc);
  }

  private Doc visitAccess(Stmt.@NotNull Accessibility accessibility, Stmt.Accessibility def) {
    if (accessibility == def) return Doc.empty();
    else return Doc.styled(KEYWORD, accessibility.keyword);
  }

  public @NotNull Doc stmt(@NotNull Stmt prestmt) {
    return switch (prestmt) {
      case Decl decl -> decl(decl);
      case Command.Import cmd -> {
        var prelude = MutableList.of(Doc.styled(KEYWORD, "import"), Doc.symbol(cmd.path().join()));
        if (cmd.asName() != null) {
          prelude.append(Doc.styled(KEYWORD, "as"));
          prelude.append(Doc.plain(cmd.asName()));
        }
        yield Doc.sep(prelude);
      }
      case Generalize variables -> Doc.sep(Doc.styled(KEYWORD, "variables"), visitTele(variables.toExpr()));
      case Remark remark -> {
        var literate = remark.literate;
        yield literate != null ? literate.toDoc() : Doc.plain(remark.raw);
      }
      case Command.Open cmd -> Doc.sepNonEmpty(
        visitAccess(cmd.accessibility(), Stmt.Accessibility.Private),
        Doc.styled(KEYWORD, "open"),
        Doc.plain(cmd.path().join()),
        Doc.styled(KEYWORD, switch (cmd.useHide().strategy()) {
          case Using -> "using";
          case Hiding -> "hiding";
        }),
        Doc.parened(Doc.commaList(cmd.useHide().list().view().map(name -> name.asName().equals(name.id()) ? Doc.plain(name.id())
          : Doc.sep(Doc.plain(name.id()), Doc.styled(KEYWORD, "as"), Doc.plain(name.asName())))))
      );
      case Command.Module mod -> Doc.vcat(
        Doc.sep(visitAccess(mod.accessibility(), Stmt.Accessibility.Public),
          Doc.styled(KEYWORD, "module"),
          Doc.plain(mod.name()),
          Doc.symbol("{")),
        Doc.nest(2, Doc.vcat(mod.contents().view().map(this::stmt))),
        Doc.symbol("}")
      );
    };
  }

  private Stmt.Accessibility defaultAcc(@NotNull Decl.Personality personality) {
    return personality == Decl.Personality.NORMAL ? Stmt.Accessibility.Public : Stmt.Accessibility.Private;
  }

  public @NotNull Doc decl(@NotNull Decl predecl) {
    return switch (predecl) {
      case ClassDecl classDecl -> throw new UnsupportedOperationException("not implemented yet");
      case TeleDecl.StructDecl decl -> {
        var prelude = MutableList.of(
          visitAccess(decl.accessibility(), defaultAcc(decl.personality())),
          visitPersonality(decl.personality()),
          Doc.styled(KEYWORD, "struct"),
          linkDef(decl.ref, STRUCT_CALL),
          visitTele(decl.telescope));
        appendResult(prelude, decl.result);
        yield Doc.cat(Doc.sepNonEmpty(prelude),
          Doc.emptyIf(decl.fields.isEmpty(), () -> Doc.cat(Doc.line(), Doc.nest(2, Doc.vcat(
            decl.fields.view().map(this::decl))))),
          visitBindBlock(decl.bindBlock)
        );
      }
      case TeleDecl.FnDecl decl -> {
        var prelude = MutableList.of(
          visitAccess(decl.accessibility(), defaultAcc(decl.personality())),
          visitPersonality(decl.personality()),
          Doc.styled(KEYWORD, "def"));
        prelude.appendAll(Seq.from(decl.modifiers).view().map(this::visitModifier));
        prelude.append(linkDef(decl.ref, FN_CALL));
        prelude.append(visitTele(decl.telescope));
        appendResult(prelude, decl.result);
        yield Doc.cat(Doc.sepNonEmpty(prelude),
          decl.body.fold(expr -> Doc.cat(Doc.spaced(Doc.symbol("=>")), term(Outer.Free, expr)),
            clauses -> Doc.cat(Doc.line(), Doc.nest(2, visitClauses(clauses)))),
          visitBindBlock(decl.bindBlock)
        );
      }
      case TeleDecl.DataDecl decl -> {
        var prelude = MutableList.of(
          visitAccess(decl.accessibility(), defaultAcc(decl.personality())),
          visitPersonality(decl.personality()),
          Doc.styled(KEYWORD, "data"),
          linkDef(decl.ref, DATA_CALL),
          visitTele(decl.telescope));
        appendResult(prelude, decl.result);
        yield Doc.cat(Doc.sepNonEmpty(prelude),
          Doc.emptyIf(decl.body.isEmpty(), () -> Doc.cat(Doc.line(), Doc.nest(2, Doc.vcat(
            decl.body.view().map(this::decl))))),
          visitBindBlock(decl.bindBlock)
        );
      }
      case TeleDecl.PrimDecl decl -> primDoc(decl.ref);
      case TeleDecl.StructField field -> {
        var doc = MutableList.of(Doc.symbol("|"),
          coe(field.coerce),
          linkDef(field.ref, FIELD_CALL),
          visitTele(field.telescope));
        appendResult(doc, field.result);
        if (field.body.isDefined()) {
          doc.append(Doc.symbol("=>"));
          doc.append(term(Outer.Free, field.body.get()));
        }
        yield Doc.sepNonEmpty(doc);
      }
      case TeleDecl.DataCtor ctor -> {
        var doc = Doc.cblock(Doc.sepNonEmpty(
          coe(ctor.coerce),
          linkDef(ctor.ref, CON_CALL),
          visitTele(ctor.telescope)), 2, visitClauses(ctor.clauses));
        if (ctor.patterns.isNotEmpty()) {
          var pats = Doc.commaList(ctor.patterns.view().map(pattern -> pattern(pattern, Outer.Free)));
          yield Doc.sep(Doc.symbol("|"), pats, Doc.plain("=>"), doc);
        } else yield Doc.sep(Doc.symbol("|"), doc);
      }
    };
  }

  /**
   * This function assumed that <code>doBind.var()</code> is not {@link org.aya.ref.LocalVar#IGNORED}
   */
  public @NotNull Doc visitDoBind(@NotNull Expr.DoBind doBind) {
    return Doc.sep(
      varDoc(doBind.var()),
      Doc.symbol("<-"),
      term(Outer.Free, doBind.expr()));
  }

  public @NotNull Doc visitPersonality(@NotNull Decl.Personality personality) {
    return switch (personality) {
      case NORMAL -> Doc.empty();
      case EXAMPLE -> Doc.styled(KEYWORD, "example");
      case COUNTEREXAMPLE -> Doc.styled(KEYWORD, "counterexample");
    };
  }

  private Doc visitClauses(@NotNull ImmutableSeq<Pattern.Clause> clauses) {
    if (clauses.isEmpty()) return Doc.empty();
    return Doc.vcat(
      clauses.view()
        .map(this::matchy)
        .map(doc -> Doc.sep(Doc.symbol("|"), doc)));
  }

  private void appendResult(MutableList<Doc> prelude, Expr result) {
    if (result instanceof Expr.HoleExpr) return;
    prelude.append(Doc.symbol(":"));
    prelude.append(term(Outer.Free, result));
  }

  public Doc visitBindBlock(@NotNull BindBlock bindBlock) {
    if (bindBlock == BindBlock.EMPTY) return Doc.empty();
    var loosers = bindBlock.resolvedLoosers().get();
    var tighters = bindBlock.resolvedTighters().get();
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

  private @NotNull Doc visitModifier(@NotNull Modifier modifier) {
    return Doc.styled(KEYWORD, modifier.keyword);
  }
}
