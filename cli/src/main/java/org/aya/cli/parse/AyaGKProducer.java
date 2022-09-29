// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.parse;

import com.intellij.openapi.util.text.LineColumn;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.builder.GenericNode;
import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableSinglyLinkedList;
import kala.control.Either;
import kala.control.Option;
import kala.function.BooleanFunction;
import kala.tuple.Tuple;
import kala.tuple.Tuple2;
import kala.value.MutableValue;
import org.aya.concrete.Expr;
import org.aya.concrete.Pattern;
import org.aya.concrete.error.BadCounterexampleWarn;
import org.aya.concrete.error.BadModifierWarn;
import org.aya.concrete.error.ParseError;
import org.aya.concrete.remark.Remark;
import org.aya.concrete.stmt.*;
import org.aya.core.term.FormTerm;
import org.aya.generic.Constants;
import org.aya.generic.Modifier;
import org.aya.generic.util.InternalException;
import org.aya.pretty.doc.Doc;
import org.aya.ref.LocalVar;
import org.aya.repl.antlr.AntlrUtil;
import org.aya.util.StringEscapeUtil;
import org.aya.util.binop.Assoc;
import org.aya.util.binop.OpDecl;
import org.aya.util.error.SourceFile;
import org.aya.util.error.SourcePos;
import org.aya.util.error.WithPos;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import static org.aya.parser.ij.AyaPsiElementTypes.*;

public record AyaGKProducer(
  @NotNull Either<SourceFile, SourcePos> source,
  @NotNull Reporter reporter
) {
  public @NotNull ImmutableSeq<Stmt> program(@NotNull GenericNode<?> node) {
    return node.childrenOfType(STMT).flatMap(this::stmt).toImmutableSeq();
  }

  public @NotNull ImmutableSeq<Stmt> stmt(@NotNull GenericNode<?> node) {
    var importCmd = node.peekChild(IMPORT_CMD);
    if (importCmd != null) return ImmutableSeq.of(importCmd(importCmd));
    var mod = node.peekChild(MODULE);
    if (mod != null) return ImmutableSeq.of(module(mod));
    var openCmd = node.peekChild(OPEN_CMD);
    if (openCmd != null) return openCmd(openCmd);
    var decl = node.peekChild(DECL);
    if (decl != null) {
      var result = decl(decl);
      var stmts = result._2.view().prepended(result._1);
      if (result._1 instanceof Decl.TopLevel top && top.personality() == Decl.Personality.COUNTEREXAMPLE) {
        var stmtOption = result._2.firstOption(stmt -> !(stmt instanceof Decl));
        if (stmtOption.isDefined()) reporter.report(new BadCounterexampleWarn(stmtOption.get()));
        return stmts.<Stmt>filterIsInstance(Decl.class).toImmutableSeq();
      }
      return stmts.toImmutableSeq();
    }
    var generalize = node.peekChild(GENERALIZE);
    if (generalize != null) return ImmutableSeq.of(generalize(generalize));
    var remark = node.peekChild(REMARK);
    if (remark != null) return ImmutableSeq.of(remark(remark));
    return unreachable(node);
  }

  public @NotNull Remark remark(@NotNull GenericNode<?> node) {
    var sb = new StringBuilder();
    for (var docComment : node.childrenOfType(DOC_COMMENT)) {
      sb.append(docComment.tokenText().substring(3)).append("\n");
    }
    return Remark.make(sb.toString(), sourcePosOf(node), new AyaParserImpl(reporter));
  }

  public @NotNull Generalize generalize(@NotNull GenericNode<?> node) {
    return new Generalize(sourcePosOf(node),
      node.childrenOfType(GENERALIZE_PARAM_NAME)
        .map(this::generalizeParamName)
        .map(id -> new GeneralizedVar(id.data(), id.sourcePos()))
        .toImmutableSeq(),
      type(node.child(TYPE)));
  }

  public @NotNull Command.Import importCmd(@NotNull GenericNode<?> node) {
    var asId = node.peekChild(WEAK_ID);
    var importMod = node.child(QUALIFIED_ID);
    return new Command.Import(
      sourcePosOf(importMod),
      qualifiedId(importMod),
      asId == null ? null : weakId(asId).data()
    );
  }

  public @NotNull ImmutableSeq<Stmt> openCmd(@NotNull GenericNode<?> node) {
    var accessibility = node.peekChild(KW_PUBLIC) == null
      ? Stmt.Accessibility.Private
      : Stmt.Accessibility.Public;
    var useHide = node.peekChild(USE_HIDE);
    var modNameNode = node.child(QUALIFIED_ID);
    var namePos = sourcePosOf(modNameNode);
    var modName = qualifiedId(modNameNode);
    var open = new Command.Open(
      namePos,
      accessibility,
      modName,
      useHide != null ? useHide(useHide) : UseHide.EMPTY,
      false
    );
    return node.peekChild(KW_IMPORT) != null
      ? ImmutableSeq.of(new Command.Import(namePos, modName, null), open)
      : ImmutableSeq.of(open);
  }

  public UseHide hideList(SeqView<GenericNode<?>> hideLists, UseHide.Strategy strategy) {
    return new UseHide(
      hideLists
        .map(h -> h.child(IDS_COMMA))
        .flatMap(this::idsComma)
        .map(id -> new UseHide.Name(id, id, Assoc.Invalid, BindBlock.EMPTY))
        .toImmutableSeq(),
      strategy);
  }

  public UseHide useList(SeqView<GenericNode<?>> useLists, UseHide.Strategy strategy) {
    return new UseHide(useLists
      .map(u -> u.child(USE_IDS_COMMA))
      .flatMap(this::useIdsComma)
      .toImmutableSeq(),
      strategy);
  }

  public SeqView<UseHide.Name> useIdsComma(@NotNull GenericNode<?> node) {
    return node.childrenOfType(USE_ID).map(id -> {
      var name = weakId(id.child(WEAK_ID)).data();
      var useAs = id.peekChild(USE_AS);
      if (useAs == null) return new UseHide.Name(name, name, Assoc.Invalid, BindBlock.EMPTY);
      var asId = weakId(useAs.child(WEAK_ID)).data();
      var asAssoc = useAs.peekChild(ASSOC);
      var asBind = useAs.peekChild(BIND_BLOCK);
      return new UseHide.Name(name, asId,
        asAssoc != null ? assoc(asAssoc) : Assoc.Invalid,
        asBind != null ? bindBlock(asBind) : BindBlock.EMPTY);
    });
  }

  public @NotNull Assoc assoc(@NotNull GenericNode<?> node) {
    if (node.peekChild(KW_INFIX) != null) return Assoc.Infix;
    if (node.peekChild(KW_INFIXL) != null) return Assoc.InfixL;
    if (node.peekChild(KW_INFIXR) != null) return Assoc.InfixR;
    if (node.peekChild(KW_FIXL) != null) return Assoc.FixL;
    if (node.peekChild(KW_FIXR) != null) return Assoc.FixR;
    return unreachable(node);
  }

  public @NotNull BindBlock bindBlock(@NotNull GenericNode<?> node) {
    return new BindBlock(sourcePosOf(node), MutableValue.create(),
      node.childrenOfType(LOOSERS)
        .flatMap(c -> c.childrenOfType(QUALIFIED_ID).map(this::qualifiedId))
        .toImmutableSeq(),
      node.childrenOfType(TIGHTERS)
        .flatMap(c -> c.childrenOfType(QUALIFIED_ID).map(this::qualifiedId))
        .toImmutableSeq(),
      MutableValue.create(), MutableValue.create());
  }

  public @NotNull UseHide useHide(@NotNull GenericNode<?> node) {
    if (node.peekChild(KW_HIDING) != null) return hideList(
      node.childrenOfType(HIDE_LIST).map(x -> x), // make compiler happy
      UseHide.Strategy.Hiding);
    if (node.peekChild(KW_USING) != null) return useList(
      node.childrenOfType(USE_LIST).map(x -> x),  // make compiler happy
      UseHide.Strategy.Using);
    return unreachable(node);
  }

  public @NotNull Command.Module module(@NotNull GenericNode<?> node) {
    var modName = weakId(node.child(WEAK_ID));
    return new Command.Module(
      modName.sourcePos(), sourcePosOf(node), modName.data(),
      node.childrenOfType(STMT).flatMap(this::stmt).toImmutableSeq());
  }

  public Tuple2<? extends Decl, ImmutableSeq<Stmt>> decl(@NotNull GenericNode<?> node) {
    var accessibility = node.peekChild(KW_PRIVATE) == null ? Stmt.Accessibility.Public : Stmt.Accessibility.Private;
    var fnDecl = node.peekChild(FN_DECL);
    if (fnDecl != null) return Tuple.of(fnDecl(fnDecl, accessibility), ImmutableSeq.empty());
    var dataDecl = node.peekChild(DATA_DECL);
    if (dataDecl != null) return dataDecl(dataDecl, accessibility);
    var structDecl = node.peekChild(STRUCT_DECL);
    if (structDecl != null) return structDecl(structDecl, accessibility);
    var primDecl = node.peekChild(PRIM_DECL);
    if (primDecl != null) return Tuple.of(primDecl(primDecl), ImmutableSeq.empty());
    return unreachable(node);
  }

  public TeleDecl.FnDecl fnDecl(@NotNull GenericNode<?> node, Stmt.Accessibility acc) {
    var sample = sampleModifiers(node.peekChild(SAMPLE_MODIFIERS));
    var modifiers = node.childrenOfType(FN_MODIFIERS).map(m -> Tuple.of(m, fnModifier(m)))
      .toImmutableSeq();
    var inline = modifiers.find(t -> t._2 == Modifier.Inline);
    var opaque = modifiers.find(t -> t._2 == Modifier.Opaque);
    if (inline.isDefined() && opaque.isDefined()) {
      var gunpowder = inline.get();
      reporter.report(new BadModifierWarn(sourcePosOf(gunpowder._1), gunpowder._2));
    }
    var tele = telescope(node.childrenOfType(TELE).map(x -> x)); // make compiler happy
    var bind = node.peekChild(BIND_BLOCK);
    var nameOrInfix = declNameOrInfix(node.child(DECL_NAME_OR_INFIX));

    var dynamite = fnBody(node.child(FN_BODY));
    if (dynamite.isRight() && inline.isDefined()) {
      var gelatin = inline.get();
      reporter.report(new BadModifierWarn(sourcePosOf(gelatin._1), gelatin._2));
    }
    var entire = sourcePosOf(node);
    return new TeleDecl.FnDecl(
      nameOrInfix._1.sourcePos(),
      sourcePosOf(node),
      sample == Decl.Personality.NORMAL ? acc : Stmt.Accessibility.Private,
      modifiers.map(Tuple2::getValue).collect(Collectors.toCollection(
        () -> EnumSet.noneOf(Modifier.class))),
      nameOrInfix._2,
      nameOrInfix._1.data(),
      tele,
      typeOrHole(node.peekChild(TYPE), entire),
      dynamite,
      bind == null ? BindBlock.EMPTY : bindBlock(bind),
      sample
    );
  }

  public @NotNull Either<Expr, ImmutableSeq<Pattern.Clause>> fnBody(@NotNull GenericNode<?> node) {
    var expr = node.peekChild(EXPR);
    if (expr != null) return Either.left(expr(expr));
    return Either.right(node.childrenOfType(BARRED_CLAUSE).map(this::bareOrBarredClause).toImmutableSeq());
  }

  public @NotNull Tuple2<TeleDecl.DataDecl, ImmutableSeq<Stmt>>
  dataDecl(GenericNode<?> node, Stmt.Accessibility acc) {
    var sample = sampleModifiers(node.peekChild(SAMPLE_MODIFIERS));
    var bind = node.peekChild(BIND_BLOCK);
    var openAcc = node.peekChild(KW_PUBLIC) != null ? Stmt.Accessibility.Public : Stmt.Accessibility.Private;
    var body = node.childrenOfType(DATA_BODY).map(this::dataBody).toImmutableSeq();
    var tele = telescope(node.childrenOfType(TELE).map(x -> x));
    var nameOrInfix = declNameOrInfix(node.child(DECL_NAME_OR_INFIX));
    var entire = sourcePosOf(node);
    var data = new TeleDecl.DataDecl(
      nameOrInfix._1.sourcePos(),
      entire,
      sample == Decl.Personality.NORMAL ? acc : Stmt.Accessibility.Private,
      nameOrInfix._2,
      nameOrInfix._1.data(),
      tele,
      typeOrHole(node.peekChild(TYPE), entire),
      body,
      bind == null ? BindBlock.EMPTY : bindBlock(bind),
      sample
    );
    return Tuple.of(data, node.peekChild(OPEN_KW) == null ? ImmutableSeq.empty() : ImmutableSeq.of(
      new Command.Open(
        sourcePosOf(node.child(OPEN_KW)),
        openAcc,
        new QualifiedID(entire, nameOrInfix._1.data()),
        UseHide.EMPTY,
        sample == Decl.Personality.EXAMPLE
      )
    ));
  }

  public @NotNull TeleDecl.DataCtor dataBody(@NotNull GenericNode<?> node) {
    var dataCtorClause = node.peekChild(DATA_CTOR_CLAUSE);
    if (dataCtorClause != null) return dataCtorClause(dataCtorClause);
    var dataCtor = node.peekChild(DATA_CTOR);
    if (dataCtor != null) return dataCtor(ImmutableSeq.empty(), dataCtor);
    return unreachable(node);
  }

  public @NotNull TeleDecl.DataCtor dataCtorClause(@NotNull GenericNode<?> node) {
    return dataCtor(patterns(node.child(PATTERNS)), node.child(DATA_CTOR));
  }

  public @NotNull Tuple2<TeleDecl.StructDecl, ImmutableSeq<Stmt>> structDecl(@NotNull GenericNode<?> node, Stmt.Accessibility acc) {
    var sample = sampleModifiers(node.peekChild(SAMPLE_MODIFIERS));
    var bind = node.peekChild(BIND_BLOCK);
    var openAcc = node.peekChild(KW_PUBLIC) != null ? Stmt.Accessibility.Public : Stmt.Accessibility.Private;
    var fields = node.childrenOfType(STRUCT_FIELD).map(this::structField).toImmutableSeq();
    var tele = telescope(node.childrenOfType(TELE).map(x -> x));
    var nameOrInfix = declNameOrInfix(node.child(DECL_NAME_OR_INFIX));
    var entire = sourcePosOf(node);
    var struct = new TeleDecl.StructDecl(
      nameOrInfix._1.sourcePos(),
      entire,
      sample == Decl.Personality.NORMAL ? acc : Stmt.Accessibility.Private,
      nameOrInfix._2,
      nameOrInfix._1.data(),
      tele,
      typeOrHole(node.peekChild(TYPE), entire),
      fields,
      bind == null ? BindBlock.EMPTY : bindBlock(bind),
      sample
    );
    return Tuple.of(struct, node.peekChild(OPEN_KW) == null ? ImmutableSeq.empty() : ImmutableSeq.of(
      new Command.Open(
        sourcePosOf(node.child(OPEN_KW)),
        openAcc,
        new QualifiedID(entire, nameOrInfix._1.data()),
        UseHide.EMPTY,
        sample == Decl.Personality.EXAMPLE
      )
    ));
  }

  public @NotNull TeleDecl.StructField structField(GenericNode<?> node) {
    var tele = telescope(node.childrenOfType(TELE).map(x -> x));
    var nameOrInfix = declNameOrInfix(node.child(DECL_NAME_OR_INFIX));
    var bind = node.peekChild(BIND_BLOCK);
    var entire = sourcePosOf(node);
    return new TeleDecl.StructField(
      nameOrInfix._1.sourcePos(),
      entire,
      nameOrInfix._2,
      nameOrInfix._1.data(),
      tele,
      typeOrHole(node.peekChild(TYPE), entire),
      Option.ofNullable(node.peekChild(EXPR)).map(this::expr),
      node.peekChild(KW_COERCE) != null,
      bind == null ? BindBlock.EMPTY : bindBlock(bind)
    );
  }

  public @NotNull TeleDecl.PrimDecl primDecl(@NotNull GenericNode<?> node) {
    var id = primName(node.child(PRIM_NAME));
    var type = node.peekChild(TYPE);
    return new TeleDecl.PrimDecl(
      id.sourcePos(),
      sourcePosOf(node),
      id.data(),
      telescope(node.childrenOfType(TELE).map(x -> x)),
      type == null ? new Expr.ErrorExpr(id.sourcePos(), Doc.plain("missing result")) : type(type)
      // ^ Question: typeOrHole?
    );
  }

  public @NotNull TeleDecl.DataCtor dataCtor(@NotNull ImmutableSeq<Pattern> patterns, @NotNull GenericNode<?> node) {
    var tele = telescope(node.childrenOfType(TELE).map(x -> x));
    var nameOrInfix = declNameOrInfix(node.child(DECL_NAME_OR_INFIX));
    var bind = node.peekChild(BIND_BLOCK);
    var clauses = node.peekChild(CLAUSES);
    return new TeleDecl.DataCtor(
      nameOrInfix._1.sourcePos(),
      sourcePosOf(node),
      nameOrInfix._2,
      nameOrInfix._1.data(),
      tele,
      clauses == null ? ImmutableSeq.empty() : clauses(clauses),
      patterns,
      node.peekChild(KW_COERCE) != null,
      bind == null ? BindBlock.EMPTY : bindBlock(bind)
    );
  }

  public @NotNull ImmutableSeq<Expr.Param> telescope(SeqView<GenericNode<?>> telescope) {
    return telescope.flatMap(this::tele).toImmutableSeq();
  }

  public @NotNull ImmutableSeq<Expr.Param> tele(@NotNull GenericNode<?> node) {
    var teleLit = node.peekChild(TELE_LIT);
    if (teleLit != null) {
      var type = literal(teleLit);
      var pos = sourcePosOf(node);
      return ImmutableSeq.of(new Expr.Param(pos, Constants.randomlyNamed(pos), type, true));
    }
    var teleEx = node.peekChild(TELE_EX);
    if (teleEx != null) return teleBinder(teleEx.child(TELE_BINDER), true);
    var teleIm = node.peekChild(TELE_IM);
    if (teleIm != null) return teleBinder(teleIm.child(TELE_BINDER), false);
    return unreachable(node);
  }

  public @NotNull ImmutableSeq<Expr.Param> teleBinder(@NotNull GenericNode<?> node, boolean explicit) {
    var pos = sourcePosOf(node);
    var typed = node.peekChild(TELE_BINDER_TYPED);
    if (typed != null) return teleBinderTyped(typed, explicit);
    var anonymous = node.peekChild(TELE_BINDER_ANONYMOUS);
    if (anonymous != null) return ImmutableSeq.of(new Expr.Param(pos,
      Constants.randomlyNamed(pos),
      expr(anonymous.child(EXPR)),
      explicit));
    return unreachable(node);
  }

  private @NotNull ImmutableSeq<Expr.Param> teleBinderTyped(@NotNull GenericNode<?> node, boolean explicit) {
    var ids = node.childrenOfType(TELE_PARAM_NAME).map(this::teleParamName);
    var type = type(node.child(TYPE));
    return ids.map(i -> new Expr.Param(
      i.sourcePos(), LocalVar.from(i), type, explicit)).toImmutableSeq();
  }

  public @NotNull ImmutableSeq<Expr.Param> lambdaTelescope(SeqView<GenericNode<?>> telescope) {
    return telescope.flatMap(this::lambdaTele).toImmutableSeq();
  }

  public @NotNull ImmutableSeq<Expr.Param> lambdaTele(@NotNull GenericNode<?> node) {
    var lamTeleLit = node.peekChild(LAMBDA_TELE_LIT);
    if (lamTeleLit != null) return lambdaTeleLit(lamTeleLit, true, sourcePosOf(node));
    var lamTeleEx = node.peekChild(LAMBDA_TELE_EX);
    if (lamTeleEx != null) return lambdaTeleBinder(lamTeleEx.child(LAMBDA_TELE_BINDER), true);
    var lamTeleIm = node.peekChild(LAMBDA_TELE_IM);
    if (lamTeleIm != null) return lambdaTeleBinder(lamTeleIm.child(LAMBDA_TELE_BINDER), false);
    return unreachable(node);
  }

  public @NotNull ImmutableSeq<Expr.Param> lambdaTeleBinder(@NotNull GenericNode<?> node, boolean explicit) {
    var pos = sourcePosOf(node);
    var typed = node.peekChild(TELE_BINDER_TYPED);
    if (typed != null) return teleBinderTyped(typed, explicit);
    var lamTeleLit = node.peekChild(LAMBDA_TELE_LIT);
    if (lamTeleLit != null) return lambdaTeleLit(lamTeleLit, explicit, pos);
    return unreachable(node);
  }

  private @NotNull ImmutableSeq<Expr.Param> lambdaTeleLit(GenericNode<?> node, boolean explicit, SourcePos pos) {
    return ImmutableSeq.of(new Expr.Param(pos,
      LocalVar.from(teleParamName(node.child(TELE_PARAM_NAME))),
      typeOrHole(null, pos),
      explicit));
  }

  public Tuple2<@NotNull WithPos<String>, OpDecl.@Nullable OpInfo> declNameOrInfix(@NotNull GenericNode<?> node) {
    var assoc = node.peekChild(ASSOC);
    var id = weakId(node.child(WEAK_ID));
    if (assoc == null) return Tuple.of(id, null);
    var infix = new OpDecl.OpInfo(id.data(), assoc(assoc));
    return Tuple.of(new WithPos<>(id.sourcePos(), infix.name()), infix);
  }

  public @NotNull Expr expr(@NotNull GenericNode<?> node) {
    var pos = sourcePosOf(node);
    var atomExpr = node.peekChild(ATOM_EXPR);
    if (atomExpr != null) return atomExpr(atomExpr);
    var appExpr = node.peekChild(APP_EXPR);
    if (appExpr != null) {
      var head = new Expr.NamedArg(true, expr(appExpr.child(EXPR)));
      var tail = appExpr.childrenOfType(ARGUMENT)
        .map(this::argument)
        .collect(MutableSinglyLinkedList.factory());
      tail.push(head);
      return new Expr.BinOpSeq(pos, tail.toImmutableSeq());
    }
    var projExpr = node.peekChild(PROJ_EXPR);
    if (projExpr != null) return buildProj(pos, expr(projExpr.child(EXPR)), projExpr.child(PROJ_FIX));
    var arrowExpr = node.peekChild(ARROW_EXPR);
    if (arrowExpr != null) {
      var exprs = arrowExpr.childrenOfType(EXPR);
      var expr0 = exprs.get(0);
      var to = expr(exprs.get(1));
      var paramPos = sourcePosOf(expr0);
      var param = new Expr.Param(paramPos, Constants.randomlyNamed(paramPos), expr(expr0), true);
      return new Expr.PiExpr(pos, false, param, to);
    }
    var newExpr = node.peekChild(NEW_EXPR);
    if (newExpr != null) {
      var struct = expr(newExpr.child(EXPR));
      var newBody = newExpr.child(NEW_BODY);
      var fields = newBody.childrenOfType(NEW_ARG).map(arg -> {
        var id = newArgField(arg.child(NEW_ARG_FIELD));
        var bindings = arg.childrenOfType(TELE_PARAM_NAME).map(this::teleParamName)
          .map(b -> b.map($ -> LocalVar.from(b)))
          .toImmutableSeq();
        var body = expr(arg.child(EXPR));
        return new Expr.Field(id, bindings, body, MutableValue.create());
      }).toImmutableSeq();
      return new Expr.NewExpr(pos, struct, fields);
    }
    var piExpr = node.peekChild(PI_EXPR);
    if (piExpr != null) return buildPi(pos, false,
      telescope(piExpr.childrenOfType(TELE).map(x -> x)).view(),
      expr(piExpr.child(EXPR)));
    var forallExpr = node.peekChild(FORALL_EXPR);
    if (forallExpr != null) return buildPi(pos, false,
      lambdaTelescope(forallExpr.childrenOfType(TELE).map(x -> x)).view(),
      expr(forallExpr.child(EXPR)));
    var sigmaExpr = node.peekChild(SIGMA_EXPR);
    if (sigmaExpr != null) {
      var last = expr(sigmaExpr.child(EXPR));
      return new Expr.SigmaExpr(pos, false,
        telescope(sigmaExpr.childrenOfType(TELE).map(x -> x))
          .appended(new Expr.Param(last.sourcePos(), LocalVar.IGNORED, last, true)));
    }
    var lambdaExpr = node.peekChild(LAMBDA_EXPR);
    if (lambdaExpr != null) {
      Expr result;
      var bodyExpr = lambdaExpr.peekChild(EXPR);
      if (bodyExpr == null) {
        var impliesToken = lambdaExpr.peekChild(IMPLIES);
        var bodyHolePos = impliesToken == null ? pos : sourcePosOf(impliesToken);
        result = new Expr.HoleExpr(bodyHolePos, false, null);
      } else result = expr(bodyExpr);
      return buildLam(pos, lambdaTelescope(lambdaExpr.childrenOfType(TELE).map(x -> x)).view(), result);
    }
    var partialExpr = node.peekChild(PARTIAL_EXPR);
    if (partialExpr != null) return partial(partialExpr, pos);
    var pathExpr = node.peekChild(PATH_EXPR);
    if (pathExpr != null) {
      var params = node.childrenOfType(PATH_TELE).map(t -> {
        var n = teleParamName(t.child(TELE_PARAM_NAME));
        return LocalVar.from(n);
      }).toImmutableSeq();
      return new Expr.Path(pos, params, type(pathExpr.child(EXPR)), partial(pathExpr.peekChild(PARTIAL_EXPR), pos));
    }
    throw new UnsupportedOperationException("TODO: " + node.elementType());
  }

  public @NotNull Expr atomExpr(@NotNull GenericNode<?> node) {
    var atomUliftExpr = node.peekChild(ATOM_ULIFT_EXPR);
    if (atomUliftExpr != null) {
      var expr = literal(atomUliftExpr.child(LITERAL));
      var lifts = atomUliftExpr.childrenOfType(ULIFT_PREFIX).toImmutableSeq().size();
      return lifts > 0 ? new Expr.LiftExpr(sourcePosOf(node), expr, lifts) : expr;
    }
    var atomTupleExpr = node.peekChild(ATOM_TUPLE_EXPR);
    if (atomTupleExpr != null) {
      var expr = atomTupleExpr.child(EXPR_LIST).childrenOfType(EXPR).toImmutableSeq();
      if (expr.size() == 1) return newBinOPScope(expr(expr.get(0)));
      return new Expr.TupExpr(sourcePosOf(node), expr.map(this::expr));
    }
    return unreachable(node);
  }

  public @NotNull Expr.NamedArg argument(@NotNull GenericNode<?> node) {
    var atomExArg = node.peekChild(ATOM_EX_ARGUMENT);
    if (atomExArg != null) {
      var fixes = atomExArg.childrenOfType(PROJ_FIX);
      var expr = atomExpr(atomExArg.child(ATOM_EXPR));
      var projected = fixes
        .foldLeft(Tuple.of(sourcePosOf(node), expr),
          (acc, proj) -> Tuple.of(acc._2.sourcePos(), buildProj(acc._1, acc._2, proj)))
        ._2;
      return new Expr.NamedArg(true, projected);
    }
    var tupleImArg = node.peekChild(TUPLE_IM_ARGUMENT);
    if (tupleImArg != null) {
      var items = tupleImArg.child(EXPR_LIST).childrenOfType(EXPR).map(this::expr).toImmutableSeq();
      if (items.sizeEquals(1)) return new Expr.NamedArg(false, newBinOPScope(items.first()));
      var tupExpr = new Expr.TupExpr(sourcePosOf(node), items);
      return new Expr.NamedArg(false, tupExpr);
    }
    var namedImArg = node.peekChild(NAMED_IM_ARGUMENT);
    if (namedImArg != null) {
      var id = weakId(namedImArg.child(WEAK_ID));
      return new Expr.NamedArg(false, id.data(), expr(namedImArg.child(EXPR)));
    }
    return unreachable(node);
  }

  public @NotNull Expr.PartEl partial(@Nullable GenericNode<?> partial, @NotNull SourcePos fallbackPos) {
    if (partial == null) return new Expr.PartEl(fallbackPos, ImmutableSeq.empty());
    var sub = partial.childrenView()
      .filter(c -> c.elementType() == BARE_SUB_SYSTEM || c.elementType() == BARRED_SUB_SYSTEM)
      .map(this::bareOrBarredSubSystem)
      .toImmutableSeq();
    return new Expr.PartEl(sourcePosOf(partial), sub);
  }

  public @NotNull Tuple2<Expr, Expr> bareOrBarredSubSystem(@NotNull GenericNode<?> node) {
    return subSystem(node.child(SUB_SYSTEM));
  }

  public @NotNull Tuple2<Expr, Expr> subSystem(@NotNull GenericNode<?> node) {
    var exprs = node.childrenOfType(EXPR).map(this::expr);
    return Tuple.of(exprs.get(0), exprs.get(1));
  }

  private Expr.@NotNull ProjExpr buildProj(
    @NotNull SourcePos sourcePos, @NotNull Expr projectee,
    @NotNull GenericNode<?> fix
  ) {
    var number = fix.peekChild(NUMBER);
    return new Expr.ProjExpr(sourcePos, projectee,
      number != null
        ? Either.left(Integer.parseInt(number.tokenText()))
        : Either.right(qualifiedId(fix.child(PROJ_FIX_ID).child(QUALIFIED_ID))));
  }

  public static @NotNull Expr buildPi(
    SourcePos sourcePos, boolean co,
    SeqView<Expr.Param> params, Expr body
  ) {
    if (params.isEmpty()) return body;
    var drop = params.drop(1);
    return new Expr.PiExpr(
      sourcePos, co, params.first(),
      buildPi(AntlrUtil.sourcePosForSubExpr(sourcePos.file(),
        drop.map(Expr.Param::sourcePos), body.sourcePos()), co, drop, body));
  }

  public static @NotNull Expr buildLam(SourcePos sourcePos, SeqView<Expr.Param> params, Expr body) {
    if (params.isEmpty()) return body;
    var drop = params.drop(1);
    return new Expr.LamExpr(
      sourcePos, params.first(),
      buildLam(AntlrUtil.sourcePosForSubExpr(sourcePos.file(),
        drop.map(Expr.Param::sourcePos), body.sourcePos()), drop, body));
  }

  public @NotNull Expr literal(@NotNull GenericNode<?> node) {
    var refExpr = node.peekChild(REF_EXPR);
    if (refExpr != null) {
      var qid = qualifiedId(refExpr.child(QUALIFIED_ID));
      return new Expr.UnresolvedExpr(qid.sourcePos(), qid);
    }
    var pos = sourcePosOf(node);
    var holeExpr = node.peekChild(HOLE_EXPR);
    if (holeExpr != null) {
      if (holeExpr.peekChild(CALM_FACE_EXPR) != null)
        return new Expr.HoleExpr(pos, false, null);
      var goalExpr = holeExpr.child(GOAL_EXPR);
      var fillingExpr = goalExpr.peekChild(EXPR);
      var filling = fillingExpr == null ? null : expr(fillingExpr);
      return new Expr.HoleExpr(pos, true, filling);
    }
    var univExpr = node.peekChild(UNIV_EXPR);
    if (univExpr != null) {
      if (univExpr.peekChild(KW_TYPE) != null) return new Expr.RawSortExpr(pos, FormTerm.SortKind.Type);
      if (univExpr.peekChild(KW_SET) != null) return new Expr.RawSortExpr(pos, FormTerm.SortKind.Set);
      if (univExpr.peekChild(KW_PROP) != null) return new Expr.RawSortExpr(pos, FormTerm.SortKind.Prop);
      if (univExpr.peekChild(KW_ISET) != null) return new Expr.RawSortExpr(pos, FormTerm.SortKind.ISet);
      return unreachable(univExpr);
    }
    var litIntExpr = node.peekChild(LIT_INT_EXPR);
    if (litIntExpr != null) try {
      return new Expr.LitIntExpr(pos, Integer.parseInt(litIntExpr.tokenText()));
    } catch (NumberFormatException ignored) {
      reporter.report(new ParseError(pos, "Unsupported integer literal `" + litIntExpr.tokenText() + "`"));
      throw new ParsingInterruptedException();
    }
    var litStringExpr = node.peekChild(LIT_STRING_EXPR);
    if (litStringExpr != null) {
      var text = litStringExpr.tokenText();
      var content = text.substring(1, text.length() - 1);
      return new Expr.LitStringExpr(pos, StringEscapeUtil.escapeStringCharacters(content));
    }
    return unreachable(node);
  }

  public @NotNull Pattern pattern(@NotNull GenericNode<?> node) {
    return atomPatterns(node.child(ATOM_PATTERNS)).apply(true, null);
  }

  public BiFunction<Boolean, LocalVar, Pattern> atomPatterns(@NotNull GenericNode<?> node) {
    var atoms = node.childrenOfType(ATOM_PATTERN)
      .map(this::atomPattern)
      .toImmutableSeq();
    if (atoms.sizeEquals(1)) return (ex, as) -> newBinOPScope(atoms.first().apply(ex), as);
    return (ex, as) -> new Pattern.BinOpSeq(
      sourcePosOf(node),
      atoms.map(p -> p.apply(true)),
      as, ex
    );
  }

  public @NotNull BooleanFunction<Pattern> atomPattern(@NotNull GenericNode<?> node) {
    var sourcePos = sourcePosOf(node);
    var bindPat = node.peekChild(ATOM_BIND_PATTERN);
    if (bindPat != null) {
      var id = weakId(bindPat.child(WEAK_ID));
      return ex -> new Pattern.Bind(sourcePos, ex, LocalVar.from(id), MutableValue.create());
    }
    var exPat = node.peekChild(ATOM_EX_PATTERN);
    var imPat = node.peekChild(ATOM_IM_PATTERN);
    if (exPat != null || imPat != null) {
      var forceEx = exPat != null;
      var pat = exPat != null ? exPat : imPat;
      var patterns = pat.child(PATTERNS);
      var asId = pat.peekChild(WEAK_ID);
      var as = asId == null ? null : LocalVar.from(weakId(asId));
      var tupElem = patterns.childrenOfType(PATTERN)
        .map(t -> atomPatterns(t.child(ATOM_PATTERNS)))
        .toImmutableSeq();
      return tupElem.sizeEquals(1)
        ? (ignored -> newBinOPScope(tupElem.first().apply(forceEx, as), as))
        : (ignored -> new Pattern.Tuple(sourcePos, forceEx, tupElem.map(p -> p.apply(true, null)), as));
    }
    var numberPat = node.peekChild(ATOM_NUMBER_PATTERN);
    if (numberPat != null) return ex -> new Pattern.Number(sourcePos, ex, Integer.parseInt(numberPat.tokenText()));
    var absurdPat = node.peekChild(ATOM_ABSURD_PATTERN);
    if (absurdPat != null) return ex -> new Pattern.Absurd(sourcePos, ex);
    var calmFacePat = node.peekChild(ATOM_CALM_FACE_PATTERN);
    if (calmFacePat != null) return ex -> new Pattern.CalmFace(sourcePos, ex);
    return unreachable(node);
  }

  public @NotNull ImmutableSeq<Pattern> patterns(@NotNull GenericNode<?> node) {
    return node.childrenOfType(PATTERN).map(this::pattern).toImmutableSeq();
  }

  public @NotNull ImmutableSeq<Pattern.Clause> clauses(@NotNull GenericNode<?> node) {
    return node.childrenView()
      .filter(c -> c.elementType() == BARE_CLAUSE || c.elementType() == BARRED_CLAUSE)
      .map(this::bareOrBarredClause)
      .toImmutableSeq();
  }

  public @NotNull Pattern.Clause clause(@NotNull GenericNode<?> node) {
    return new Pattern.Clause(sourcePosOf(node), patterns(node.child(PATTERNS)),
      Option.ofNullable(node.peekChild(EXPR)).map(this::expr));
  }

  public @NotNull Pattern.Clause bareOrBarredClause(@NotNull GenericNode<?> node) {
    return clause(node.child(CLAUSE));
  }

  public @NotNull Expr type(@NotNull GenericNode<?> node) {
    return expr(node.child(EXPR));
  }

  public @NotNull Expr typeOrHole(@Nullable GenericNode<?> node, SourcePos sourcePos) {
    return node == null
      ? new Expr.HoleExpr(sourcePos, false, null)
      : type(node);
  }

  public @NotNull WithPos<String> weakId(@NotNull GenericNode<?> node) {
    return new WithPos<>(sourcePosOf(node), node.tokenText());
  }

  public @NotNull WithPos<String> generalizeParamName(@NotNull GenericNode<?> node) {
    return teleParamName(node.child(TELE_PARAM_NAME));
  }

  public @NotNull WithPos<String> teleParamName(@NotNull GenericNode<?> node) {
    return weakId(node.child(WEAK_ID));
  }

  public @NotNull WithPos<String> primName(@NotNull GenericNode<?> node) {
    return weakId(node.child(WEAK_ID));
  }

  public @NotNull WithPos<String> newArgField(@NotNull GenericNode<?> node) {
    return weakId(node.child(WEAK_ID));
  }

  public @NotNull SeqView<String> idsComma(@NotNull GenericNode<?> node) {
    return node.childrenOfType(WEAK_ID).map(this::weakId).map(WithPos::data);
  }

  public @NotNull QualifiedID qualifiedId(@NotNull GenericNode<?> node) {
    return new QualifiedID(sourcePosOf(node),
      node.childrenOfType(WEAK_ID)
        .map(this::weakId)
        .map(WithPos::data).toImmutableSeq());
  }

  public @NotNull Modifier fnModifier(@NotNull GenericNode<?> node) {
    if (node.peekChild(KW_OPAQUE) != null) return Modifier.Opaque;
    if (node.peekChild(KW_INLINE) != null) return Modifier.Opaque;
    if (node.peekChild(KW_OVERLAP) != null) return Modifier.Opaque;
    return unreachable(node);
  }

  public @NotNull Decl.Personality sampleModifiers(@Nullable GenericNode<?> node) {
    if (node == null) return Decl.Personality.NORMAL;
    if (node.peekChild(KW_EXAMPLE) != null) return Decl.Personality.EXAMPLE;
    if (node.peekChild(KW_COUNTEREXAMPLE) != null) return Decl.Personality.COUNTEREXAMPLE;
    return unreachable(node);
  }

  private <T> T unreachable(GenericNode<?> node) {
    throw new InternalException(node.elementType() + ": " + node.tokenText());
  }

  /**
   * [kiva]: make `(expr)` into a new BinOP parser scope
   * so the `f (+)` becomes passing `+` as an argument to function `f`.
   */
  public @NotNull Expr newBinOPScope(@NotNull Expr expr) {
    return new Expr.BinOpSeq(expr.sourcePos(),
      ImmutableSeq.of(new Expr.NamedArg(true, expr)));
  }

  public @NotNull Pattern newBinOPScope(@NotNull Pattern expr, @Nullable LocalVar as) {
    return new Pattern.BinOpSeq(expr.sourcePos(),
      ImmutableSeq.of(expr), as, expr.explicit());
  }

  private @NotNull SourcePos sourcePosOf(@NotNull GenericNode<?> node) {
    return source.fold(file -> sourcePosOf(node, file), pos -> pos);
  }

  private static @NotNull SourcePos sourcePosOf(@NotNull GenericNode<?> node, @NotNull SourceFile file) {
    var start = StringUtil.offsetToLineColumn(file.sourceCode(), node.startOffset());
    var end = node.isTerminalNode()
      ? LineColumn.of(start.line, start.column + (node.endOffset() - node.startOffset()))
      : StringUtil.offsetToLineColumn(file.sourceCode(), node.endOffset());
    return new SourcePos(file, node.startOffset(), node.endOffset(),
      start.line + 1, start.column, end.line + 1, end.column);
  }
}
