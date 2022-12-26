// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.prettier;

import com.intellij.openapi.util.text.StringUtil;
import kala.collection.Seq;
import kala.collection.SeqLike;
import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.range.primitive.IntRange;
import kala.tuple.Tuple;
import kala.tuple.Tuple2;
import org.aya.concrete.Expr;
import org.aya.concrete.Pattern;
import org.aya.concrete.stmt.*;
import org.aya.concrete.visitor.ExprConsumer;
import org.aya.generic.Constants;
import org.aya.generic.Modifier;
import org.aya.pretty.doc.Doc;
import org.aya.ref.DefVar;
import org.aya.ref.LocalVar;
import org.aya.util.Arg;
import org.aya.util.binop.Assoc;
import org.aya.util.prettier.PrettierOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * @author ice1000, kiva
 * @see CorePrettier
 */
public class ConcretePrettier extends BasePrettier<Expr> {
  public ConcretePrettier(@NotNull PrettierOptions options) {
    super(options);
  }

  @Override public @NotNull Doc term(@NotNull Outer outer, @NotNull Expr prexpr) {
    return switch (prexpr) {
      case Expr.Error error -> Doc.angled(error.description().toDoc(options));
      case Expr.Tuple expr -> Doc.parened(Doc.commaList(expr.items().view().map(e -> term(Outer.Free, e))));
      case Expr.BinOpSeq binOpSeq -> {
        var seq = binOpSeq.seq();
        var first = seq.first().term();
        if (seq.sizeEquals(1)) yield term(outer, first);
        yield visitCalls(null,
          term(Outer.AppSpine, first),
          seq.view().drop(1), outer,
          options.map.get(AyaPrettierOptions.Key.ShowImplicitArgs)
        );
      }
      case Expr.LitString expr -> Doc.plain('"' + StringUtil.unescapeStringCharacters(expr.string()) + '"');
      case Expr.Pi expr -> {
        var data = new boolean[]{false, false};
        new ExprConsumer() {
          @Override public void pre(@NotNull Expr e) {
            switch (e) {
              case Expr.Ref ref when ref.resolvedVar() == expr.param().ref() -> data[0] = true;
              case Expr.Unresolved ignored -> data[1] = true;
              default -> ExprConsumer.super.pre(e);
            }
          }
        }.accept(expr.last());
        Doc doc;
        var last = term(Outer.Codomain, expr.last());
        if (!data[0] && !data[1]) {
          doc = Doc.sep(justType(expr.param(), Outer.Domain), Doc.symbol("->"), last);
        } else {
          doc = Doc.sep(Doc.styled(KEYWORD, Doc.symbol("Pi")), expr.param().toDoc(options), Doc.symbol("->"), last);
        }
        // When outsider is neither a codomain nor non-expression, we need to add parentheses.
        yield checkParen(outer, doc, Outer.Domain);
      }
      case Expr.App expr -> {
        var args = MutableList.of(expr.argument());
        var head = Expr.unapp(expr.function(), args);
        Assoc assoc = null;
        if (head instanceof Expr.Ref ref && ref.resolvedVar() instanceof DefVar<?, ?> var)
          assoc = var.assoc();
        yield visitCalls(assoc,
          term(Outer.AppHead, head),
          args.view(), outer,
          options.map.get(AyaPrettierOptions.Key.ShowImplicitArgs));
      }
      case Expr.Lambda expr -> {
        if (!options.map.get(AyaPrettierOptions.Key.ShowImplicitPats) && !expr.param().explicit()) {
          yield term(outer, expr.body());
        }
        var prelude = MutableList.of(Doc.styled(KEYWORD, Doc.symbol("\\")),
          lambdaParam(expr.param()));
        if (!(expr.body() instanceof Expr.Hole)) {
          prelude.append(Doc.symbol("=>"));
          prelude.append(term(Outer.Free, expr.body()));
        }
        yield checkParen(outer, Doc.sep(prelude), Outer.BinOp);
      }
      case Expr.Hole expr -> {
        if (!expr.explicit()) yield Doc.symbol(Constants.ANONYMOUS_PREFIX);
        var filling = expr.filling();
        if (filling == null) yield Doc.symbol("{??}");
        yield Doc.sep(Doc.symbol("{?"), term(Outer.Free, filling), Doc.symbol("?}"));
      }
      case Expr.Proj expr -> Doc.cat(term(Outer.ProjHead, expr.tup()), Doc.symbol("."),
        Doc.plain(expr.ix().fold(Objects::toString, QualifiedID::join)));
      case Expr.Match match ->
        Doc.cblock(Doc.cat(Doc.styled(KEYWORD, "match"), Doc.commaList(match.discriminant().map(t -> term(Outer.Free, t)))), 2,
          Doc.vcat(match.clauses().view()
            .map(clause -> Doc.sep(Doc.symbol("|"),
              Doc.commaList(clause.patterns.map(p -> pattern(p, Outer.Free))),
              clause.expr.map(t -> Doc.cat(Doc.symbol("=>"), term(Outer.Free, t))).getOrDefault(Doc.empty())))
            .toImmutableSeq()));
      case Expr.RawProj expr -> Doc.sepNonEmpty(Doc.cat(term(Outer.ProjHead, expr.tup()), Doc.symbol("."),
          Doc.plain(expr.id().join())), expr.coeLeft() != null ? term(Outer.AppSpine, expr.coeLeft()) : Doc.empty(),
        expr.restr() != null ? Doc.sep(Doc.styled(KEYWORD, "freeze"), term(Outer.AppSpine, expr.restr())) : Doc.empty());
      case Expr.Coe expr -> visitCalls(expr.resolvedVar(), PRIM,
        ImmutableSeq.of(new Arg<>(expr.type(), true), new Arg<>(expr.restr(), true)),
        outer, options.map.get(AyaPrettierOptions.Key.ShowImplicitArgs));
      case Expr.Unresolved expr -> Doc.plain(expr.name().join());
      case Expr.Ref expr -> {
        var ref = expr.resolvedVar();
        if (ref instanceof DefVar<?, ?> defVar) yield defVar(defVar);
        else yield varDoc(ref);
      }
      case Expr.LitInt expr -> Doc.plain(String.valueOf(expr.integer()));
      case Expr.RawSort e -> Doc.styled(KEYWORD, e.kind().name());
      case Expr.New expr -> Doc.cblock(
        Doc.sep(Doc.styled(KEYWORD, "new"), term(Outer.Free, expr.struct())),
        2, Doc.vcat(expr.fields().view().map(t ->
          Doc.sep(Doc.symbol("|"), Doc.styled(FIELD, t.name().data()),
            Doc.emptyIf(t.bindings().isEmpty(), () ->
              Doc.sep(t.bindings().map(v -> varDoc(v.data())))),
            Doc.plain("=>"), term(Outer.Free, t.body()))
        )));
      case Expr.Sigma expr -> checkParen(outer, Doc.sep(
        Doc.styled(KEYWORD, Doc.symbol("Sig")),
        visitTele(expr.params().dropLast(1)),
        Doc.symbol("**"),
        term(Outer.Codomain, expr.params().last().type())), Outer.BinOp);
      // ^ Same as Pi
      case Expr.Sort expr -> {
        var fn = Doc.styled(KEYWORD, expr.kind().name());
        if (!expr.kind().hasLevel()) yield fn;
        yield visitCalls(null, fn, (nc, l) -> l.toDoc(options), outer,
          SeqView.of(new Arg<>(o -> Doc.plain(String.valueOf(expr.lift())), true)), true);
      }
      case Expr.Lift expr -> Doc.sep(Seq
        .from(IntRange.closed(1, expr.lift()).iterator()).view()
        .map($ -> Doc.styled(KEYWORD, Doc.symbol("ulift")))
        .appended(term(Outer.Lifted, expr.expr())));
      case Expr.PartEl el -> Doc.sep(Doc.symbol("{|"),
        partial(el),
        Doc.symbol("|}"));
      case Expr.Path path -> Doc.sep(
        Doc.symbol("[|"),
        Doc.commaList(path.params().map(BasePrettier::linkDef)),
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
        var doBlockDoc = doExpr.binds().map(this::visitDoBinding);

        // Either not flat (single line) or full flat
        yield Doc.stickySep(
          // doExpr is atom! It cannot be `do\n{ ... }`
          Doc.styled(KEYWORD, "do"),
          Doc.flatAltBracedBlock(
            Doc.commaList(doBlockDoc),
            Doc.vcommaList(
              doBlockDoc.map(x -> Doc.nest(2, x))
            )
          ));
      }
      case Expr.Array arr -> arr.arrayBlock().fold(
        left -> Doc.sep(
          Doc.symbol("["),
          term(Outer.Free, left.generator()),
          Doc.symbol("|"),
          Doc.commaList(left.binds().map(this::visitDoBinding)),
          Doc.symbol("]")
        ),
        right -> Doc.sep(
          Doc.symbol("["),
          Doc.commaList(right.exprList().view().map(e -> term(Outer.Free, e))),   // Copied from Expr.Tup case
          Doc.symbol("]")
        )
      );
      case Expr.Let let -> {
        var letsAndBody = sugarLet(let);
        var lets = letsAndBody._1;
        var body = letsAndBody._2;
        var oneLine = lets.sizeEquals(1);
        var letSeq = oneLine
          ? visitLetBind(lets.first())
          : Doc.vcat(lets.view()
            .map(this::visitLetBind)
            // | f := g
            .map(x -> Doc.sep(Doc.symbol("|"), x)));

        var docs = ImmutableSeq.of(
          Doc.styled(KEYWORD, "let"),
          letSeq,
          Doc.styled(KEYWORD, "in")
        );

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
    };
  }

  private Doc partial(Expr.PartEl el) {
    return Doc.join(Doc.spaced(Doc.symbol("|")), el.clauses().map(cl -> Doc.sep(
      term(Outer.Free, cl._1), Doc.symbol(":="), term(Outer.Free, cl._2))
    ));
  }

  public @NotNull Doc pattern(@NotNull Arg<Pattern> pattern, Outer outer) {
    return pattern(pattern.term(), pattern.explicit(), outer);
  }

  public @NotNull Doc pattern(@NotNull Pattern pattern, boolean licit, Outer outer) {
    return switch (pattern) {
      case Pattern.Tuple tuple -> Doc.licit(licit,
        Doc.commaList(tuple.patterns().view().map(p -> pattern(p, Outer.Free))));
      case Pattern.Absurd $ -> Doc.bracedUnless(Doc.styled(KEYWORD, "()"), licit);
      case Pattern.Bind bind -> Doc.bracedUnless(linkDef(bind.bind()), licit);
      case Pattern.CalmFace $ -> Doc.bracedUnless(Doc.plain(Constants.ANONYMOUS_PREFIX), licit);
      case Pattern.Number number -> Doc.bracedUnless(Doc.plain(String.valueOf(number.number())), licit);
      case Pattern.Ctor ctor -> {
        var name = linkRef(ctor.resolved().data(), CON);
        var ctorDoc = ctor.params().isEmpty() ? name : Doc.sep(name, visitMaybeCtorPatterns(ctor.params(), Outer.AppSpine, Doc.ALT_WS));
        yield ctorDoc(outer, licit, ctorDoc, ctor.params().isEmpty());
      }
      case Pattern.QualifiedRef qref -> Doc.bracedUnless(Doc.plain(qref.qualifiedID().join()), licit);
      case Pattern.BinOpSeq(var pos, var param) -> {
        if (param.sizeEquals(1)) {
          yield pattern(param.first(), outer);
        }
        var ctorDoc = visitMaybeCtorPatterns(param.view(), Outer.AppSpine, Doc.ALT_WS);
        // TODO: ditto
        yield ctorDoc(outer, licit, ctorDoc, param.sizeLessThanOrEquals(1));
      }
      case Pattern.List list -> Doc.sep(
        Doc.symbol("["),
        Doc.commaList(list.elements().map(x -> pattern(x, true, Outer.Free))),
        Doc.symbol("]")
      );
      case Pattern.As as -> {
        var asBind = Seq.of(
          Doc.styled(KEYWORD, "as"),
          linkDef(as.as())
        );

        if (outer == Outer.AppSpine) {
          // {pattern as bind}
          var inner = pattern(as.pattern(), true, Outer.Free);
          yield Doc.licit(licit, Doc.sep(SeqView.of(inner).concat(asBind)));
        } else {
          var inner = pattern(as.pattern(), licit, Outer.Free);
          yield Doc.sep(SeqView.of(inner).concat(asBind));
        }
      }
    };
  }

  private Doc visitMaybeCtorPatterns(SeqLike<Arg<Pattern>> patterns, Outer outer, @NotNull Doc delim) {
    patterns = options.map.get(AyaPrettierOptions.Key.ShowImplicitPats) ? patterns : patterns.view().filter(Arg::explicit);
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
          linkDef(decl.ref, STRUCT),
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
        prelude.append(linkDef(decl.ref, FN));
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
          linkDef(decl.ref, DATA),
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
          linkDef(field.ref, FIELD),
          visitTele(field.telescope));
        appendResult(doc, field.result);
        field.body.ifDefined(body -> {
          doc.append(Doc.symbol("=>"));
          doc.append(term(Outer.Free, body));
        });
        yield Doc.sepNonEmpty(doc);
      }
      case TeleDecl.DataCtor ctor -> {
        var ret = ctor.result == null ? Doc.empty() : Doc.sep(Doc.symbol(":"), term(Outer.Free, ctor.result));
        var doc = Doc.cblock(Doc.sepNonEmpty(
          coe(ctor.coerce),
          linkDef(ctor.ref, CON),
          visitTele(ctor.telescope),
          ret), 2, partial(ctor.clauses));
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
  public @NotNull Doc visitDoBinding(@NotNull Expr.DoBind doBind) {
    return doBind.var() == LocalVar.IGNORED
      ? term(Outer.Free, doBind.expr())
      : Doc.sep(
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

  private void appendResult(MutableList<Doc> prelude, @Nullable Expr result) {
    if (result == null || result instanceof Expr.Hole) return;
    prelude.append(Doc.symbol(":"));
    prelude.append(term(Outer.Free, result));
  }

  public Doc visitBindBlock(@NotNull BindBlock bindBlock) {
    if (bindBlock == BindBlock.EMPTY) return Doc.empty();
    var loosers = bindBlock.resolvedLoosers().get();
    var tighters = bindBlock.resolvedTighters().get();
    if (loosers.isEmpty() && tighters.isEmpty()) return Doc.empty();

    if (loosers.isEmpty()) return Doc.cat(Doc.line(), Doc.hang(2, Doc.sep(
      Doc.styled(KEYWORD, "tighter"),
      Doc.commaList(tighters.view().map(BasePrettier::defVar)))));
    else if (tighters.isEmpty()) return Doc.cat(Doc.line(), Doc.hang(2, Doc.sep(
      Doc.styled(KEYWORD, "looser"),
      Doc.commaList(loosers.view().map(BasePrettier::defVar)))));
    return Doc.cat(Doc.line(), Doc.hang(2, Doc.cat(Doc.styled(KEYWORD, "bind"), Doc.braced(Doc.sep(
      Doc.styled(KEYWORD, "tighter"), Doc.commaList(tighters.view().map(BasePrettier::defVar)),
      Doc.styled(KEYWORD, "looser"), Doc.commaList(loosers.view().map(BasePrettier::defVar))
    )))));
  }

  // Convert a parsing-time-desguared let to a sugared let
  private @NotNull Tuple2<ImmutableSeq<Expr.LetBind>, Expr> sugarLet(@NotNull Expr.Let let) {
    var letBinds = MutableList.<Expr.LetBind>create();

    Expr letOrExpr = let;
    while (letOrExpr instanceof Expr.Let mLet) {
      letBinds.append(mLet.bind());
      letOrExpr = mLet.body();
    }

    return Tuple.of(letBinds.toImmutableSeq(), letOrExpr);
  }

  private @NotNull Doc visitLetBind(@NotNull Expr.LetBind letBind) {
    // f : G := g
    var prelude = MutableList.of(
      varDoc(letBind.bindName())
    );

    if (letBind.telescope().isNotEmpty()) {
      prelude.append(visitTele(letBind.telescope()));
    }

    appendResult(prelude, letBind.result());
    prelude.append(Doc.symbol(":="));
    prelude.append(term(Outer.Free, letBind.definedAs()));

    return Doc.sep(prelude);
  }

  private @NotNull Doc visitModifier(@NotNull Modifier modifier) {
    return Doc.styled(KEYWORD, modifier.keyword);
  }
}
