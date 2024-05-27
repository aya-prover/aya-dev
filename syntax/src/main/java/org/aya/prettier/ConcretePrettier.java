// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.prettier;

import com.intellij.openapi.util.text.StringUtil;
import kala.collection.Seq;
import kala.collection.SeqLike;
import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.range.primitive.IntRange;
import org.aya.generic.Constants;
import org.aya.generic.Modifier;
import org.aya.generic.Nested;
import org.aya.pretty.doc.Doc;
import org.aya.syntax.concrete.Expr;
import org.aya.syntax.concrete.Pattern;
import org.aya.syntax.concrete.stmt.*;
import org.aya.syntax.concrete.stmt.Stmt.Accessibility;
import org.aya.syntax.concrete.stmt.decl.*;
import org.aya.syntax.ref.DefVar;
import org.aya.syntax.ref.LocalVar;
import org.aya.util.Arg;
import org.aya.util.binop.Assoc;
import org.aya.util.error.WithPos;
import org.aya.util.prettier.PrettierOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Objects;

import static org.aya.prettier.Tokens.*;

/**
 * @author ice1000, kiva
 */
public class ConcretePrettier extends BasePrettier<Expr> {
  public ConcretePrettier(@NotNull PrettierOptions options) { super(options); }

  public @NotNull Doc term(@NotNull Outer outer, @NotNull WithPos<Expr> expr) {
    return term(outer, expr.data());
  }

  @Override public @NotNull Doc term(@NotNull Outer outer, @NotNull Expr prexpr) {
    return switch (prexpr) {
      case Expr.Error error -> Doc.angled(error.description().toDoc(options));
      case Expr.Tuple expr -> Doc.parened(Doc.commaList(expr.items().view().map(e -> term(Outer.Free, e.data()))));
      case Expr.BinOpSeq binOpSeq -> {
        var seq = binOpSeq.seq();
        var first = seq.getFirst().term();
        if (seq.sizeEquals(1)) yield term(outer, first);
        yield visitCalls(null,
          term(Outer.AppSpine, first),
          seq.view().drop(1).map(x -> new Arg<>(x.arg().data(), x.explicit())),
          outer,
          optionImplicit()
        );
      }
      case Expr.LitString expr -> Doc.plain('"' + StringUtil.unescapeStringCharacters(expr.string()) + '"');
      case Expr.Pi expr -> {
        var visitor = new Object() {
          boolean paramRef = false;
          boolean unresolved = false;

          public void apply(@NotNull Expr e) {
            switch (e) {
              case Expr.Ref ref when ref.var() == expr.param().ref() -> paramRef = true;
              case Expr.Unresolved _ -> unresolved = true;
              default -> e.descent((_, t) -> {
                apply(t);
                return t;
              });
            }
          }
        };

        visitor.apply(expr.last().data());

        Doc doc;
        var last = term(Outer.Codomain, expr.last().data());
        if (!visitor.paramRef && !visitor.unresolved) {
          doc = Doc.sep(justType(expr.param(), Outer.Domain), ARROW, last);
        } else {
          doc = Doc.sep(KW_PI, expr.param().toDoc(options), ARROW, last);
        }
        // When outsider is neither a codomain nor non-expression, we need to add parentheses.
        yield checkParen(outer, doc, Outer.Domain);
      }
      case Expr.App(var head, var args) -> {
        Assoc assoc = null;
        if (head.data() instanceof Expr.Ref ref && ref.var() instanceof DefVar<?, ?> var)
          assoc = var.assoc();
        yield visitConcreteCalls(assoc,
          term(Outer.AppHead, head.data()),
          args.view(), outer,
          optionImplicit());
      }
      case Expr.Lambda expr -> {
        var pair = Nested.destructNested(WithPos.dummy(expr));
        var telescope = pair.component1();
        var body = pair.component2().data();

        if (!options.map.get(AyaPrettierOptions.Key.ShowImplicitPats)) {
          var exTele = telescope.filter(Expr.Param::explicit);
          if (exTele.isEmpty()) yield term(outer, body);

          telescope = exTele;
        }

        var prelude = MutableList.of(LAMBDA);
        var docTele = telescope.map(this::lambdaParam);

        prelude.appendAll(docTele);
        if (!(body instanceof Expr.Hole)) {
          prelude.append(FN_DEFINED_AS);
          prelude.append(term(Outer.Free, body));
        }
        yield checkParen(outer, Doc.sep(prelude), Outer.BinOp);
      }
      case Expr.Hole expr -> {
        if (!expr.explicit()) yield Doc.symbol(Constants.ANONYMOUS_PREFIX);
        var filling = expr.filling();
        if (filling == null) yield HOLE;
        yield Doc.sep(HOLE_LEFT, term(Outer.Free, filling.data()), HOLE_RIGHT);
      }
      case Expr.Proj expr -> Doc.cat(term(Outer.ProjHead, expr.tup().data()), PROJ,
        Doc.plain(expr.ix().fold(Objects::toString, QualifiedID::join)));
      // case Expr.Match match ->
      //   Doc.cblock(Doc.cat(Doc.styled(KEYWORD, "match"), Doc.commaList(match.discriminant().map(t -> term(Outer.Free, t)))), 2,
      //     Doc.vcat(match.clauses().view()
      //       .map(clause -> Doc.sep(Doc.symbol("|"),
      //         patterns(clause.patterns),
      //         clause.expr.map(t -> Doc.cat(Doc.symbol("=>"), term(Outer.Free, t))).getOrDefault(Doc.empty())))
      //       .toImmutableSeq()));
      case Expr.Unresolved expr -> Doc.plain(expr.name().join());
      case Expr.Ref expr -> {
        var ref = expr.var();
        yield ref instanceof DefVar<?, ?> defVar ? defVar(defVar) : varDoc(ref);
      }
      case Expr.LitInt expr -> Doc.plain(String.valueOf(expr.integer()));
      case Expr.RawSort e -> Doc.styled(KEYWORD, e.kind().name());
      // case Expr.New expr -> Doc.cblock(
      //   Doc.sep(Doc.styled(KEYWORD, "new"), term(Outer.Free, expr.struct())),
      //   2, Doc.vcat(expr.fields().view().map(t ->
      //     Doc.sep(Doc.symbol("|"), Doc.styled(MEMBER, t.name().data()),
      //       Doc.emptyIf(t.bindings().isEmpty(), () ->
      //         Doc.sep(t.bindings().map(v -> varDoc(v.data())))),
      //       Doc.plain("=>"), term(Outer.Free, t.body()))
      //   )));
      case Expr.Sigma expr -> checkParen(outer, Doc.sep(
        KW_SIGMA,
        visitTele(expr.params().dropLast(1)),
        SIGMA_RESULT,
        term(Outer.Codomain, expr.params().getLast().type())), Outer.BinOp);
      // ^ Same as Pi
      case Expr.Sort expr -> {
        var fn = Doc.styled(KEYWORD, expr.kind().name());
        if (!expr.kind().hasLevel()) yield fn;
        yield visitCalls(null, fn, (_, l) -> l.toDoc(options), outer,
          SeqView.of(new Arg<>(_ -> Doc.plain(String.valueOf(expr.lift())), true)), true);
      }
      case Expr.Lift expr -> Doc.sep(Seq
        .from(IntRange.closed(1, expr.lift()).iterator()).view()
        .map(_ -> Doc.styled(KEYWORD, Doc.symbol("ulift")))
        .appended(term(Outer.Lifted, expr.expr())));
      case Expr.Idiom idiom -> Doc.wrap(
        "(|", "|)",
        Doc.join(Doc.symbol("|"), idiom.barredApps().view()
          .map(app -> term(Outer.Free, app)))
      );
      case Expr.Do doExpr -> {
        var doBlockDoc = doExpr.binds().map(this::visitDoBinding);

        // Either not flat (single line) or full flat
        yield Doc.stickySep(
          // doExpr is atom! It cannot be `do\n{ ... }`
          KW_DO,
          Doc.flatAltBracedBlock(
            Doc.commaList(doBlockDoc),
            Doc.vcommaList(
              doBlockDoc.map(x -> Doc.nest(2, x))
            )
          ));
      }
      case Expr.Array arr -> arr.arrayBlock().fold(
        left -> Doc.sep(
          LIST_LEFT,
          term(Outer.Free, left.generator()),
          BAR,
          Doc.commaList(left.binds().map(this::visitDoBinding)),
          LIST_RIGHT
        ),
        right -> Doc.sep(
          LIST_LEFT,
          Doc.commaList(right.exprList().view().map(e -> term(Outer.Free, e))),   // Copied from Expr.Tup case
          LIST_RIGHT
        )
      );
      case Expr.Let let -> {
        var letsAndBody = Nested.destructNested(WithPos.dummy(let));
        var lets = letsAndBody.component1();
        var body = letsAndBody.component2().data();
        var oneLine = lets.sizeEquals(1);
        var letSeq = oneLine
          ? visitLetBind(lets.getFirst())
          : Doc.vcat(lets.view()
            .map(this::visitLetBind)
            // | f := g
            .map(x -> Doc.sep(BAR, x)));

        var docs = ImmutableSeq.of(KW_LET, letSeq, KW_IN);

        // ```
        // let a := b in
        // ```
        //
        // or
        //
        // ```
        // let
        // | a := b
        // | c := d
        // in
        // ```
        var halfLet = oneLine ? Doc.sep(docs) : Doc.vcat(docs);

        yield Doc.sep(halfLet, term(Outer.Free, body));
      }
      // let open Foo using (bar) in
      //   body
      case Expr.LetOpen letOpen -> Doc.vcat(
        Doc.sep(Doc.styled(KEYWORD, "let"), stmt(letOpen.openCmd()), Doc.styled(KEYWORD, "in")),
        Doc.indent(2, term(Outer.Free, letOpen.body()))
      );
    };
  }

  public @NotNull Doc patterns(@NotNull ImmutableSeq<Pattern> patterns) {
    return Doc.commaList(patterns.map(pattern -> pattern(pattern, true, Outer.Free)));
  }

  public @NotNull Doc pattern(@NotNull Arg<Pattern> pattern, Outer outer) {
    return pattern(pattern.term(), pattern.explicit(), outer);
  }

  public @NotNull Doc pattern(@NotNull Pattern pattern, boolean licit, Outer outer) {
    return switch (pattern) {
      case Pattern.Tuple tuple -> Doc.licit(licit, patterns(tuple.patterns().map(WithPos::data)));
      case Pattern.Absurd _ -> Doc.bracedUnless(PAT_ABSURD, licit);
      case Pattern.Bind bind -> Doc.bracedUnless(linkDef(bind.bind()), licit);
      case Pattern.CalmFace _ -> Doc.bracedUnless(Doc.plain(Constants.ANONYMOUS_PREFIX), licit);
      case Pattern.Number number -> Doc.bracedUnless(Doc.plain(String.valueOf(number.number())), licit);
      case Pattern.Con con -> {
        var name = refVar(con.resolved().data());
        var ctorDoc = con.params().isEmpty()
          ? name
          : Doc.sep(name, visitMaybeConPatterns(con.params(), Outer.AppSpine, Doc.ALT_WS));
        yield ctorDoc(outer, licit, ctorDoc, con.params().isEmpty());
      }
      case Pattern.QualifiedRef qref -> Doc.bracedUnless(Doc.plain(qref.qualifiedID().join()), licit);
      case Pattern.BinOpSeq(var param) -> {
        if (param.sizeEquals(1)) {
          yield pattern(param.getFirst().map(WithPos::data), outer);
        }
        var ctorDoc = visitMaybeConPatterns(param.view(), Outer.AppSpine, Doc.ALT_WS);
        yield ctorDoc(outer, licit, ctorDoc, param.sizeLessThanOrEquals(1));
      }
      case Pattern.List list -> Doc.sep(
        LIST_LEFT,
        Doc.commaList(list.elements().map(x -> pattern(x.data(), true, Outer.Free))),
        LIST_RIGHT
      );
      case Pattern.As as -> {
        var asBind = Seq.of(KW_AS, linkDef(as.as()));

        if (outer == Outer.AppSpine) {
          // {pattern as bind}
          var inner = pattern(as.pattern().data(), true, Outer.Free);
          yield Doc.licit(licit, Doc.sep(SeqView.of(inner).concat(asBind)));
        } else {
          var inner = pattern(as.pattern().data(), licit, Outer.Free);
          yield Doc.sep(SeqView.of(inner).concat(asBind));
        }
      }
    };
  }

  private Doc visitMaybeConPatterns(SeqLike<Arg<WithPos<Pattern>>> patterns, Outer outer, @NotNull Doc delim) {
    patterns = options.map.get(AyaPrettierOptions.Key.ShowImplicitPats) ? patterns : patterns.view().filter(Arg::explicit);
    return Doc.join(delim, patterns.view().map(p -> pattern(p.map(WithPos::data), outer)));
  }

  public Doc matchy(@NotNull Pattern.Clause match) {
    var doc = visitMaybeConPatterns(match.patterns, Outer.Free, Doc.plain(", "));
    return match.expr.map(e -> Doc.sep(doc, FN_DEFINED_AS, term(Outer.Free, e))).getOrDefault(doc);
  }

  private Doc visitAccess(@NotNull Accessibility acc, @Nullable Accessibility theDefault) {
    if (acc == theDefault) return Doc.empty();
    else return Doc.styled(KEYWORD, acc.keyword);
  }

  public @NotNull Doc stmt(@NotNull Stmt prestmt) {
    return switch (prestmt) {
      case Decl decl -> decl(decl);
      case Generalize variables -> Doc.sep(KW_VARIABLES, visitTele(variables.toExpr()));
      case Command.Import cmd -> {
        var prelude = MutableList.of(KW_IMPORT, Doc.symbol(cmd.path().toString()));
        if (cmd.asName() != null) {
          prelude.append(KW_AS);
          prelude.append(Doc.plain(cmd.asName()));
        }
        yield Doc.sep(prelude);
      }
      case Command.Open cmd -> Doc.sepNonEmpty(
        visitAccess(cmd.accessibility(), Accessibility.Private),
        Doc.styled(KEYWORD, "open"),
        Doc.plain(cmd.path().toString()),
        Doc.styled(KEYWORD, cmd.useHide().strategy().name().toLowerCase(Locale.ROOT)),
        Doc.parened(Doc.commaList(cmd.useHide().list().view()
          .map(name -> name.asName().isEmpty()
            || name.id().component() == ModuleName.This && name.asName().get().equals(name.id().name())
            ? Doc.plain(name.id().name())
            : Doc.sep(Doc.plain(name.id().join()), KW_AS, Doc.plain(name.asName().get())))))
      );
      case Command.Module mod -> Doc.vcat(
        Doc.sep(visitAccess(mod.accessibility(), Accessibility.Public),
          Doc.styled(KEYWORD, "module"),
          Doc.plain(mod.name()),
          Doc.symbol("{")),
        Doc.nest(2, Doc.vcat(mod.contents().view().map(this::stmt))),
        Doc.symbol("}")
      );
    };
  }

  public @NotNull Doc decl(@NotNull Decl predecl) {
    return switch (predecl) {
      /*case ClassDecl decl -> {
        var prelude = MutableList.of(Doc.styled(KEYWORD, "class"));
        prelude.append(linkDef(decl.ref, CLAZZ));
        yield Doc.cat(Doc.sepNonEmpty(prelude),
          Doc.emptyIf(decl.members.isEmpty(), () -> Doc.cat(Doc.line(), Doc.nest(2, Doc.vcat(
            decl.members.view().map(this::decl))))),
          visitBindBlock(decl.bindBlock())
        );
      }*/
      case FnDecl decl -> {
        var prelude = declPrelude(decl);
        prelude.appendAll(Seq.from(decl.modifiers).view().map(this::visitModifier));
        prelude.append(KW_DEF);
        prelude.append(defVar(decl.ref));
        prelude.append(visitTele(decl.telescope));
        appendResult(prelude, decl.result);
        yield Doc.cat(Doc.sepNonEmpty(prelude),
          switch (decl.body) {
            case FnBody.ExprBody(var expr) -> Doc.cat(Doc.spaced(FN_DEFINED_AS), term(Outer.Free, expr));
            case FnBody.BlockBody(var clauses, ImmutableSeq<WithPos<LocalVar>> elims) -> Doc.vcat(
              Doc.cat(Doc.spaced(KW_ELIM),
                Doc.commaList(elims.map(i -> varDoc(i.data())))),
              Doc.nest(2, visitClauses(clauses)));
          },
          visitBindBlock(decl.bindBlock())
        );
      }
      case DataDecl decl -> {
        var prelude = declPrelude(decl);
        prelude.append(KW_DATA);
        prelude.append(defVar(decl.ref));
        prelude.append(visitTele(decl.telescope));
        appendResult(prelude, decl.result);
        yield Doc.cat(Doc.sepNonEmpty(prelude),
          Doc.emptyIf(decl.body.isEmpty(), () -> Doc.cat(Doc.line(), Doc.nest(2, Doc.vcat(
            decl.body.view().map(this::decl))))),
          visitBindBlock(decl.bindBlock())
        );
      }/*
      case TeleDecl.ClassMember field -> {
        var doc = MutableList.of(Doc.symbol("|"),
          coe(field.coerce),
          linkDef(field.ref, MEMBER),
          visitTele(field.telescope));
        appendResult(doc, field.result);
        field.body.ifDefined(body -> {
          doc.append(Doc.symbol("=>"));
          doc.append(term(Outer.Free, body));
        });
        yield Doc.sepNonEmpty(doc);
      }*/
      case DataCon con -> {
        var ret = con.result == null ? Doc.empty() : Doc.sep(HAS_TYPE, term(Outer.Free, con.result));
        var doc = Doc.sepNonEmpty(coe(con.coerce), defVar(con.ref), visitTele(con.telescope), ret);
        if (con.patterns.isNotEmpty()) {
          yield Doc.sep(BAR, Doc.commaList(con.patterns.map(pat ->
            pattern(pat.map(WithPos::data), Outer.Free))), FN_DEFINED_AS, doc);
        } else yield Doc.sep(BAR, doc);
      }
      case PrimDecl primDecl -> primDoc(primDecl.ref);
    };
  }

  private @NotNull MutableList<Doc> declPrelude(@NotNull Decl decl) {
    return MutableList.of(visitAccess(decl.accessibility(), Accessibility.Public));
  }

  /**
   * This function assumed that <code>doBind.var()</code> is not {@link LocalVar#IGNORED}
   */
  public @NotNull Doc visitDoBinding(@NotNull Expr.DoBind doBind) {
    return doBind.var() == LocalVar.IGNORED
      ? term(Outer.Free, doBind.expr())
      : Doc.sep(varDoc(doBind.var()), LARROW, term(Outer.Free, doBind.expr()));
  }

  private Doc visitClauses(@NotNull ImmutableSeq<Pattern.Clause> clauses) {
    if (clauses.isEmpty()) return Doc.empty();
    return Doc.vcat(clauses.view()
      .map(this::matchy)
      .map(doc -> Doc.sep(BAR, doc)));
  }

  private void appendResult(MutableList<Doc> prelude, @Nullable WithPos<Expr> result) {
    if (result == null || result.data() instanceof Expr.Hole) return;
    prelude.append(HAS_TYPE);
    prelude.append(term(Outer.Free, result));
  }

  public Doc visitBindBlock(@NotNull BindBlock bindBlock) {
    if (bindBlock == BindBlock.EMPTY) return Doc.empty();
    var loosers = bindBlock.resolvedLoosers().get();
    var tighters = bindBlock.resolvedTighters().get();
    if (loosers.isEmpty() && tighters.isEmpty()) return Doc.empty();

    if (loosers.isEmpty()) return Doc.cat(Doc.line(), Doc.hang(2, Doc.sep(
      KW_TIGHTER,
      Doc.commaList(tighters.view().map(BasePrettier::defVar)))));
    else if (tighters.isEmpty()) return Doc.cat(Doc.line(), Doc.hang(2, Doc.sep(
      KW_LOOSER,
      Doc.commaList(loosers.view().map(BasePrettier::defVar)))));
    return Doc.cat(Doc.line(), Doc.hang(2, Doc.cat(KW_BIND, Doc.braced(Doc.sep(
      KW_TIGHTER, Doc.commaList(tighters.view().map(BasePrettier::defVar)),
      KW_LOOSER, Doc.commaList(loosers.view().map(BasePrettier::defVar))
    )))));
  }

  private @NotNull Doc visitLetBind(@NotNull Expr.LetBind letBind) {
    // f : G := g
    var prelude = MutableList.of(varDoc(letBind.bindName()));

    if (letBind.telescope().isNotEmpty()) {
      prelude.append(visitTele(letBind.telescope()));
    }

    appendResult(prelude, letBind.result());
    prelude.append(DEFINED_AS);
    prelude.append(term(Outer.Free, letBind.definedAs()));

    return Doc.sep(prelude);
  }

  private @NotNull Doc visitModifier(@NotNull Modifier modifier) {
    return Doc.styled(KEYWORD, modifier.keyword);
  }

  private @NotNull Doc visitConcreteCalls(
    @Nullable Assoc assoc, @NotNull Doc fn,
    @NotNull SeqView<Expr.NamedArg> args,
    @NotNull Outer outer, boolean showImplicits
  ) {
    return visitCalls(assoc, fn, args.map(x -> new Arg<>(x.term().data(), x.explicit())), outer, showImplicits);
  }
}
