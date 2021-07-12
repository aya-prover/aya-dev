// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.visitor;

import kala.collection.Seq;
import kala.collection.SeqLike;
import kala.collection.immutable.ImmutableSeq;
import kala.control.Option;
import kala.tuple.Unit;
import org.aya.api.error.SourcePos;
import org.aya.api.ref.LevelGenVar;
import org.aya.api.util.Arg;
import org.aya.api.util.WithPos;
import org.aya.concrete.*;
import org.aya.core.visitor.CoreDistiller;
import org.aya.generic.Level;
import org.aya.generic.Matching;
import org.aya.generic.Modifier;
import org.aya.pretty.doc.Doc;
import org.aya.util.Constants;
import org.aya.util.StringEscapeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * @author ice1000, kiva
 * @see CoreDistiller
 */
public final class ConcreteDistiller implements
  Stmt.Visitor<Unit, Doc>,
  Pattern.Visitor<Boolean, Doc>,
  Expr.Visitor<Boolean, Doc> {
  public static final @NotNull ConcreteDistiller INSTANCE = new ConcreteDistiller();

  private ConcreteDistiller() {
  }

  @Override public Doc visitRef(Expr.@NotNull RefExpr expr, Boolean nestedCall) {
    return Doc.plain(expr.resolvedVar().name());
  }

  @Override public Doc visitUnresolved(Expr.@NotNull UnresolvedExpr expr, Boolean nestedCall) {
    return Doc.plain(expr.name().join());
  }

  @Override public Doc visitLam(Expr.@NotNull LamExpr expr, Boolean nestedCall) {
    return Doc.cat(
      Doc.styled(CoreDistiller.KEYWORD, Doc.symbol("\\")),
      Doc.plain(" "),
      expr.param().toDoc(),
      expr.body() instanceof Expr.HoleExpr
        ? Doc.empty()
        : Doc.cat(Doc.symbol(" => "), expr.body().toDoc())
    );
  }

  @Override public Doc visitPi(Expr.@NotNull PiExpr expr, Boolean nestedCall) {
    // TODO[kiva]: expr.co
    return Doc.cat(
      Doc.styled(CoreDistiller.KEYWORD, Doc.symbol("Pi")),
      Doc.plain(" "),
      expr.param().toDoc(),
      Doc.symbol(" -> "),
      expr.last().toDoc());
  }

  @Override public Doc visitSigma(Expr.@NotNull SigmaExpr expr, Boolean nestedCall) {
    // TODO[kiva]: expr.co
    return Doc.cat(
      Doc.styled(CoreDistiller.KEYWORD, Doc.symbol("Sig")),
      Doc.plain(" "),
      visitTele(expr.params().dropLast(1)),
      Doc.symbol(" ** "),
      Objects.requireNonNull(expr.params().last().type()).toDoc());
  }

  @Override public Doc visitRawUniv(Expr.@NotNull RawUnivExpr expr, Boolean nestedCall) {
    return Doc.styled(CoreDistiller.KEYWORD, "Type");
  }

  @Override public Doc visitUniv(Expr.@NotNull UnivExpr expr, Boolean nestedCall) {
    if (expr.hLevel() instanceof Level.Constant<LevelGenVar> t) {
      if (t.value() == 1) return CoreDistiller.INSTANCE.univDoc(nestedCall, "Prop", expr.uLevel());
      if (t.value() == 2) return CoreDistiller.INSTANCE.univDoc(nestedCall, "Set", expr.uLevel());
    } else if (expr.hLevel() instanceof Level.Infinity<LevelGenVar> t) {
      return CoreDistiller.INSTANCE.univDoc(nestedCall, "ooType", expr.uLevel());
    }
    return CoreDistiller.INSTANCE.visitCalls(
      Doc.styled(CoreDistiller.KEYWORD, "Type"),
      Seq.of(expr.hLevel(), expr.uLevel()).view().map(Arg::explicit),
      (nc, l) -> l.toDoc(), nestedCall);
  }

  @Override public Doc visitApp(Expr.@NotNull AppExpr expr, Boolean nestedCall) {
    return CoreDistiller.INSTANCE.visitCalls(
      expr.function().toDoc(),
      expr.arguments(),
      (nest, arg) -> arg.expr().accept(this, nest),
      nestedCall);
  }

  @Override public Doc visitLsuc(Expr.@NotNull LSucExpr expr, Boolean nestedCall) {
    return CoreDistiller.INSTANCE.visitCalls(
      Doc.styled(CoreDistiller.KEYWORD, "lsuc"),
      ImmutableSeq.of(Arg.explicit(expr.expr())),
      (nest, arg) -> arg.accept(this, nest),
      nestedCall);
  }

  @Override public Doc visitLmax(Expr.@NotNull LMaxExpr expr, Boolean nestedCall) {
    return CoreDistiller.INSTANCE.visitCalls(
      Doc.styled(CoreDistiller.KEYWORD, "lmax"),
      expr.levels().map(Arg::explicit),
      (nest, arg) -> arg.accept(this, nest),
      nestedCall);
  }

  @Override public Doc visitHole(Expr.@NotNull HoleExpr expr, Boolean nestedCall) {
    if (!expr.explicit()) return Doc.symbol("_");
    var filling = expr.filling();
    if (filling == null) return Doc.symbol("{??}");
    return Doc.hsep(Doc.symbol("{?"), filling.toDoc(), Doc.symbol("?}"));
  }

  @Override public Doc visitTup(Expr.@NotNull TupExpr expr, Boolean nestedCall) {
    return Doc.cat(Doc.symbol("("),
      Doc.join(Doc.plain(", "), expr.items().stream()
        .map(Expr::toDoc)),
      Doc.symbol(")"));
  }

  @Override public Doc visitProj(Expr.@NotNull ProjExpr expr, Boolean nestedCall) {
    return Doc.cat(expr.tup().toDoc(), Doc.plain("."), Doc.plain(expr.ix().fold(
      Objects::toString, WithPos::data
    )));
  }

  @Override public Doc visitNew(Expr.@NotNull NewExpr expr, Boolean aBoolean) {
    return Doc.cat(
      Doc.styled(CoreDistiller.KEYWORD, "new "),
      expr.struct().toDoc(),
      Doc.symbol(" { "),
      expr.fields().isEmpty() ? Doc.empty() : Doc.hsep(expr.fields().view().map(t ->
        Doc.hsep(Doc.plain("|"), Doc.plain(t.name()),
          t.bindings().isEmpty() ? Doc.empty() :
            Doc.join(Doc.plain(" "), t.bindings().map(v -> Doc.plain(v.data().name()))),
          Doc.plain("=>"), t.body().toDoc())
      )),
      Doc.symbol(" }")
    );
  }

  @Override public Doc visitLitInt(Expr.@NotNull LitIntExpr expr, Boolean nestedCall) {
    return Doc.plain(String.valueOf(expr.integer()));
  }

  @Override public Doc visitLitString(Expr.@NotNull LitStringExpr expr, Boolean nestedCall) {
    return Doc.plain("\"" + StringEscapeUtil.escapeStringCharacters(expr.string()) + "\"");
  }

  @Override public Doc visitError(Expr.@NotNull ErrorExpr error, Boolean nestedCall) {
    return Doc.angled(error.description());
  }

  @Override
  public Doc visitBinOpSeq(Expr.@NotNull BinOpSeq binOpSeq, Boolean nestedCall) {
    return CoreDistiller.INSTANCE.visitCalls(
      binOpSeq.seq().first().expr().toDoc(),
      binOpSeq.seq().view().drop(1).map(e -> new Arg<>(e.expr(), e.explicit())),
      (nest, arg) -> arg.accept(this, nest),
      nestedCall
    );
  }

  @Override public Doc visitTuple(Pattern.@NotNull Tuple tuple, Boolean nestedCall) {
    boolean ex = tuple.explicit();
    var tup = Doc.wrap(ex ? "(" : "{", ex ? ")" : "}",
      Doc.join(Doc.plain(", "), tuple.patterns().view().map(Pattern::toDoc)));
    return tuple.as() == null ? tup
      : Doc.cat(tup, Doc.styled(CoreDistiller.KEYWORD, " as "), Doc.plain(tuple.as().name()));
  }

  @Override public Doc visitNumber(Pattern.@NotNull Number number, Boolean nestedCall) {
    var doc = Doc.plain(String.valueOf(number.number()));
    return number.explicit() ? doc : Doc.braced(doc);
  }

  @Override public Doc visitBind(Pattern.@NotNull Bind bind, Boolean nestedCall) {
    var doc = Doc.plain(bind.bind().name());
    return bind.explicit() ? doc : Doc.braced(doc);
  }

  @Override public Doc visitAbsurd(Pattern.@NotNull Absurd absurd, Boolean aBoolean) {
    var doc = Doc.styled(CoreDistiller.KEYWORD, "impossible");
    return absurd.explicit() ? doc : Doc.braced(doc);
  }

  @Override public Doc visitCalmFace(Pattern.@NotNull CalmFace calmFace, Boolean nestedCall) {
    var doc = Doc.plain(Constants.ANONYMOUS_PREFIX);
    return calmFace.explicit() ? doc : Doc.braced(doc);
  }

  @Override public Doc visitCtor(Pattern.@NotNull Ctor ctor, Boolean nestedCall) {
    var ctorDoc = Doc.cat(
      Doc.styled(CoreDistiller.CON_CALL, ctor.name().data()),
      visitMaybeCtorPatterns(ctor.params(), true, Doc.plain(" "))
    );
    return CoreDistiller.ctorDoc(nestedCall, ctor.explicit(), ctorDoc, ctor.as(), ctor.params().isEmpty());
  }

  private Doc visitMaybeCtorPatterns(SeqLike<Pattern> patterns, boolean nestedCall, @NotNull Doc delim) {
    return patterns.isEmpty() ? Doc.empty() : Doc.cat(Doc.plain(" "), Doc.join(delim,
      patterns.view().map(p -> p.accept(this, nestedCall))));
  }

  public Doc matchy(Pattern.@NotNull Clause match) {
    var doc = visitMaybeCtorPatterns(match.patterns, false, Doc.plain(", "));
    return match.expr.map(e -> Doc.cat(doc, Doc.plain(" => "), e.toDoc())).getOrDefault(doc);
  }

  public Doc matchy(Matching<Pattern, Expr> match) {
    return matchy(new Pattern.Clause(SourcePos.NONE, match.patterns(), Option.some(match.body())));
  }

  private Doc visitAccess(Stmt.@NotNull Accessibility accessibility) {
    return Doc.styled(CoreDistiller.KEYWORD, accessibility.keyword);
  }

  @Override public Doc visitImport(Stmt.@NotNull ImportStmt cmd, Unit unit) {
    return Doc.cat(
      Doc.styled(CoreDistiller.KEYWORD, "import"),
      Doc.plain(" "),
      Doc.symbol(cmd.path().joinToString("::")),
      Doc.plain(" "),
      Doc.styled(CoreDistiller.KEYWORD, "as"),
      Doc.plain(" "),
      cmd.asName() == null ? Doc.symbol(cmd.path().joinToString("::")) : Doc.plain(cmd.asName())
    );
  }

  @Override public Doc visitOpen(Stmt.@NotNull OpenStmt cmd, Unit unit) {
    return Doc.cat(
      visitAccess(cmd.accessibility()),
      Doc.plain(" "),
      Doc.styled(CoreDistiller.KEYWORD, "open"),
      Doc.plain(" "),
      Doc.plain(cmd.path().joinToString("::")),
      Doc.plain(" "),
      Doc.styled(CoreDistiller.KEYWORD, switch (cmd.useHide().strategy()) {
        case Using -> "using ";
        case Hiding -> "hiding ";
      }),
      Doc.plain("("),
      Doc.plain(cmd.useHide().list().joinToString(", ")),
      Doc.plain(")")
    );
  }

  @Override public Doc visitModule(Stmt.@NotNull ModuleStmt mod, Unit unit) {
    return Doc.cat(
      visitAccess(mod.accessibility()),
      Doc.plain(" "),
      Doc.styled(CoreDistiller.KEYWORD, "\\module"),
      Doc.plain(" "),
      Doc.plain(mod.name()),
      Doc.plain(" {"),
      Doc.hardLine(),
      Doc.vcat(mod.contents().stream().map(Stmt::toDoc)),
      Doc.hardLine(),
      Doc.plain("}")
    );
  }

  @Override public Doc visitBind(Stmt.@NotNull BindStmt bind, Unit unit) {
    return Doc.cat(
      visitAccess(bind.accessibility()),
      Doc.plain(" "),
      Doc.styled(CoreDistiller.KEYWORD, "bind"),
      Doc.plain(" "),
      Doc.plain(bind.op().join()),
      Doc.plain(" "),
      Doc.styled(CoreDistiller.KEYWORD, switch (bind.pred()) {
        case Looser -> "looser";
        case Tighter -> "tighter";
      }),
      Doc.plain(" "),
      Doc.plain(bind.target().join())
    );
  }

  @Override public Doc visitData(Decl.@NotNull DataDecl decl, Unit unit) {
    return Doc.cat(
      visitAccess(decl.accessibility()),
      Doc.plain(" "),
      Doc.styled(CoreDistiller.KEYWORD, "data"),
      Doc.plain(" "),
      Doc.plain(decl.ref.name()),
      visitTele(decl.telescope),
      decl.result instanceof Expr.HoleExpr
        ? Doc.empty()
        : Doc.cat(Doc.plain(" : "), decl.result.toDoc()),
      decl.body.isEmpty() ? Doc.empty()
        : Doc.cat(Doc.line(), Doc.nest(2, Doc.vcat(
        decl.body.stream().map(ctor -> visitCtor(ctor, Unit.unit())))))
    );
  }

  @Override public Doc visitCtor(Decl.@NotNull DataCtor ctor, Unit unit) {
    var doc = Doc.cat(
      ctor.coerce ? Doc.styled(CoreDistiller.KEYWORD, "\\coerce ") : Doc.empty(),
      Doc.plain(ctor.ref.name()),
      visitTele(ctor.telescope),
      visitClauses(ctor.clauses, true)
    );
    if (ctor.patterns.isNotEmpty()) {
      var pats = Doc.join(Doc.plain(", "), ctor.patterns.stream().map(Pattern::toDoc));
      return Doc.hcat(Doc.plain("| "), pats, Doc.plain(" => "), doc);
    } else return Doc.hcat(Doc.plain("| "), doc);
  }

  private Doc visitClauses(@NotNull ImmutableSeq<Pattern.Clause> clauses, boolean wrapInBraces) {
    if (clauses.isEmpty()) return Doc.empty();
    var clausesDoc = Doc.vcat(
      clauses.stream()
        .map(this::matchy)
        .map(doc -> Doc.hcat(Doc.plain("|"), doc)));
    return wrapInBraces ? Doc.braced(clausesDoc) : clausesDoc;
  }

  @Override public Doc visitStruct(@NotNull Decl.StructDecl decl, Unit unit) {
    return Doc.cat(
      visitAccess(decl.accessibility()),
      Doc.plain(" "),
      Doc.styled(CoreDistiller.KEYWORD, "struct"),
      Doc.plain(" "),
      Doc.plain(decl.ref.name()),
      visitTele(decl.telescope),
      decl.result instanceof Expr.HoleExpr
        ? Doc.empty()
        : Doc.cat(Doc.plain(" : "), decl.result.toDoc()),
      decl.fields.isEmpty() ? Doc.empty()
        : Doc.cat(Doc.line(), Doc.nest(2, Doc.vcat(
        decl.fields.stream().map(field -> visitField(field, Unit.unit())))))
    );
  }

  @Override public Doc visitField(Decl.@NotNull StructField field, Unit unit) {
    return Doc.hcat(
      Doc.plain("| "),
      field.coerce ? Doc.styled(CoreDistiller.KEYWORD, "\\coerce ") : Doc.empty(),
      Doc.plain(field.ref.name()),
      visitTele(field.telescope),
      field.result instanceof Expr.HoleExpr
        ? Doc.empty()
        : Doc.cat(Doc.plain(" : "), field.result.toDoc()),
      field.body.isEmpty()
        ? Doc.empty()
        : Doc.cat(Doc.symbol(" => "), field.body.get().toDoc()),
      visitClauses(field.clauses, true)
    );
  }

  @Override public Doc visitFn(Decl.@NotNull FnDecl decl, Unit unit) {
    return Doc.cat(
      visitAccess(decl.accessibility()),
      Doc.plain(" "),
      Doc.styled(CoreDistiller.KEYWORD, "def"),
      decl.modifiers.isEmpty() ? Doc.plain(" ") :
        decl.modifiers.stream().map(this::visitModifier).reduce(Doc.empty(), Doc::hsep),
      Doc.plain(decl.ref.name()),
      visitTele(decl.telescope),
      decl.result instanceof Expr.HoleExpr
        ? Doc.empty()
        : Doc.cat(Doc.plain(" : "), decl.result.toDoc()),
      decl.body.isLeft() ? Doc.symbol(" => ") : Doc.empty(),
      decl.body.fold(Expr::toDoc, clauses ->
        Doc.hcat(Doc.line(), Doc.nest(2, visitClauses(clauses, false)))),
      decl.abuseBlock.sizeEquals(0)
        ? Doc.empty()
        : Doc.cat(Doc.plain(" "), Doc.styled(CoreDistiller.KEYWORD, "abusing"), Doc.plain(" "), visitAbuse(decl.abuseBlock))
    );
  }

  @Override public Doc visitPrim(@NotNull Decl.PrimDecl decl, Unit unit) {
    return CoreDistiller.primDoc(decl.ref);
  }

  private Doc visitModifier(@NotNull Modifier modifier) {
    return Doc.styled(CoreDistiller.KEYWORD, switch (modifier) {
      case Inline -> "\\inline";
      case Erase -> "\\erase";
    });
  }

  /*package-private*/ Doc visitTele(@NotNull ImmutableSeq<Expr.Param> telescope) {
    return telescope.isEmpty() ? Doc.empty() : Doc.cat(Doc.plain(" "),
      Doc.hsep(telescope.map(Expr.Param::toDoc)));
  }

  private Doc visitAbuse(@NotNull ImmutableSeq<Stmt> block) {
    return block.sizeEquals(1)
      ? block.get(0).toDoc()
      : Doc.vcat(block.stream().map(Stmt::toDoc));
  }

  @Override public Doc visitLevels(Generalize.@NotNull Levels levels, Unit unit) {
    var vars = levels.levels().map(WithPos::data).map(t ->
      Doc.linkDef(Doc.styled(CoreDistiller.GENERALIZED, t.name()), t.hashCode()));
    return Doc.hcat(
      Doc.styled(CoreDistiller.KEYWORD, levels.kind().keyword),
      Doc.plain(" "),
      Doc.hsep(vars));
  }

  @Override public Doc visitExample(Sample.@NotNull Working example, Unit unit) {
    return Doc.hsep(Doc.styled(CoreDistiller.KEYWORD, "example"),
      example.delegate().accept(this, unit));
  }

  @Override public Doc visitCounterexample(Sample.@NotNull Counter example, Unit unit) {
    return Doc.hsep(Doc.styled(CoreDistiller.KEYWORD, "counterexample"),
      example.delegate().accept(this, unit));
  }
}
