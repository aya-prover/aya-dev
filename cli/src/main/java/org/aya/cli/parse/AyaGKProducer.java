// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.parse;

import com.intellij.lexer.FlexLexer;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.LineColumn;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableSinglyLinkedList;
import kala.control.Either;
import kala.control.Option;
import kala.function.BooleanObjBiFunction;
import kala.text.StringView;
import kala.tuple.Tuple;
import kala.tuple.Tuple2;
import kala.value.MutableValue;
import org.aya.cli.parse.ModifierParser.ModifierSet;
import org.aya.concrete.Expr;
import org.aya.concrete.Pattern;
import org.aya.concrete.error.BadCounterexampleWarn;
import org.aya.concrete.error.BadModifierWarn;
import org.aya.concrete.error.ParseError;
import org.aya.concrete.stmt.*;
import org.aya.generic.Constants;
import org.aya.generic.Modifier;
import org.aya.generic.SortKind;
import org.aya.generic.util.InternalException;
import org.aya.parser.AyaPsiElementTypes;
import org.aya.parser.AyaPsiParser;
import org.aya.parser.GenericNode;
import org.aya.ref.LocalVar;
import org.aya.util.Arg;
import org.aya.util.binop.Assoc;
import org.aya.util.binop.OpDecl;
import org.aya.util.error.SourceFile;
import org.aya.util.error.SourcePos;
import org.aya.util.error.WithPos;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.aya.parser.AyaPsiElementTypes.*;

/**
 * Working with GK parser:
 * <ul>
 *   <li>Use {@link GenericNode#peekChild(IElementType)} if you want to check if the node has a child with desired type.</li>
 *   <li>Use {@link GenericNode#child(IElementType)} if you are sure the node has a child with desired type.</li>
 *   <li>
 *     For psi nodes with <code>extends</code> attribute in `AyaPsiParser.bnf` (like expr, decl, stmt, etc.):
 *     <ul>
 *       <li>Use {@link GenericNode#peekChild(TokenSet)}, {@link GenericNode#child(TokenSet)} if you want to obtain
 *       the node itself from its parent. Available {@link TokenSet}s are {@link AyaGKProducer#EXPR}, {@link AyaGKProducer#STMT},
 *       {@link AyaGKProducer#ARGUMENT} and something alike.</li>
 *       <li>Use {@link GenericNode#is(IElementType)} to pattern-matching on the node.</li>
 *       <li>Note that extends nodes are flattened so producing concrete tree from parse tree is different from
 *       other nodes, compare {@link AyaGKProducer#expr(GenericNode)} and its bnf rule for more details.</li>
 *       <li>You may inspect the produced node tree by the <code>toDebugString</code> method.</li>
 *       <li>If you edited extends attribute in the bnf file, do not forgot to update them here. We don't have any compile-time error
 *       thanks to the parse node being dynamically typed (we may improve it in the future) -- so be careful and patient!</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * @author kiva
 * @see AyaPsiElementTypes
 */
public record AyaGKProducer(
  @NotNull Either<SourceFile, SourcePos> source,
  @NotNull Reporter reporter
) {
  // NOTE: change here is you modified `extends` in `AyaPsiParser.bnf`
  public static final @NotNull TokenSet ARRAY_BLOCK = AyaPsiParser.EXTENDS_SETS_[0];
  public static final @NotNull TokenSet ARGUMENT = AyaPsiParser.EXTENDS_SETS_[2];
  public static final @NotNull TokenSet STMT = AyaPsiParser.EXTENDS_SETS_[3];
  public static final @NotNull TokenSet EXPR = AyaPsiParser.EXTENDS_SETS_[4];
  public static final @NotNull TokenSet DECL = TokenSet.create(DATA_DECL, FN_DECL, PRIM_DECL, STRUCT_DECL);

  public @NotNull Either<ImmutableSeq<Stmt>, Expr> program(@NotNull GenericNode<?> node) {
    var repl = node.peekChild(EXPR);
    if (repl != null) return Either.right(expr(repl));
    return Either.left(node.childrenOfType(STMT).flatMap(this::stmt).toImmutableSeq());
  }

  public @NotNull ImmutableSeq<Stmt> stmt(@NotNull GenericNode<?> node) {
    if (node.is(IMPORT_CMD)) return ImmutableSeq.of(importCmd(node));
    if (node.is(MODULE)) return ImmutableSeq.of(module(node));
    if (node.is(OPEN_CMD)) return openCmd(node);
    if (node.is(DECL)) {
      var result = decl(node);
      var stmts = result._2.view().prepended(result._1);
      if (result._1 instanceof Decl.TopLevel top && top.personality() == Decl.Personality.COUNTEREXAMPLE) {
        result._2.firstOption(stmt -> !(stmt instanceof Decl))
          .ifDefined(stmt -> reporter.report(new BadCounterexampleWarn(stmt)));
        return stmts.<Stmt>filterIsInstance(Decl.class).toImmutableSeq();
      }
      return stmts.toImmutableSeq();
    }
    if (node.is(GENERALIZE)) return ImmutableSeq.of(generalize(node));
    return unreachable(node);
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
    var openImport = node.peekChild(KW_IMPORT) != null;
    var open = new Command.Open(
      namePos,
      accessibility,
      modName,
      useHide != null ? useHide(useHide) : UseHide.EMPTY,
      false,
      openImport
    );
    return openImport
      ? ImmutableSeq.of(new Command.Import(namePos, modName, null), open)
      : ImmutableSeq.of(open);
  }

  public UseHide hideList(SeqView<GenericNode<?>> hideLists, UseHide.Strategy strategy) {
    return new UseHide(hideLists
      .mapNotNull(h -> h.peekChild(COMMA_SEP))
      .flatMap(node -> node.childrenOfType(WEAK_ID).map(this::weakId))
      .map(UseHide.Name::new)
      .toImmutableSeq(),
      strategy);
  }

  public UseHide useList(SeqView<GenericNode<?>> useLists, UseHide.Strategy strategy) {
    return new UseHide(useLists
      .mapNotNull(u -> u.peekChild(COMMA_SEP))
      .flatMap(this::useIdsComma)
      .toImmutableSeq(),
      strategy);
  }

  public SeqView<UseHide.Name> useIdsComma(@NotNull GenericNode<?> node) {
    return node.childrenOfType(USE_ID).map(id -> {
      var wholePos = sourcePosOf(id);
      var name = weakId(id.child(WEAK_ID));
      var useAs = id.peekChild(USE_AS);
      if (useAs == null) return new UseHide.Name(name);
      var asId = weakId(useAs.child(WEAK_ID)).data();
      var asAssoc = useAs.peekChild(ASSOC);
      var asBind = useAs.peekChild(BIND_BLOCK);
      return new UseHide.Name(wholePos, name.data(), asId,
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
        .flatMap(this::qualifiedIDs)
        .toImmutableSeq(),
      node.childrenOfType(TIGHTERS)
        .flatMap(this::qualifiedIDs)
        .toImmutableSeq(),
      MutableValue.create(), MutableValue.create());
  }

  private @NotNull SeqView<QualifiedID> qualifiedIDs(GenericNode<?> c) {
    return c.childrenOfType(QUALIFIED_ID).map(this::qualifiedId);
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
    if (node.is(FN_DECL)) return Tuple.of(fnDecl(node), ImmutableSeq.empty());
    if (node.is(PRIM_DECL)) return Tuple.of(primDecl(node), ImmutableSeq.empty());
    if (node.is(DATA_DECL)) return dataDecl(node);
    if (node.is(STRUCT_DECL)) return structDecl(node);
    return unreachable(node);
  }

  public @NotNull ModifierSet declModifiersOf(
    @NotNull GenericNode<?> node,
    @NotNull Predicate<WithPos<ModifierParser.Modifier>> filter) {
    var modifiers = node.childrenOfType(DECL_MODIFIERS).map(x -> {
      var pos = sourcePosOf(x);
      ModifierParser.Modifier modifier = null;
      if (x.peekChild(KW_PRIVATE) != null) modifier = ModifierParser.Modifier.Private;
      if (x.peekChild(KW_EXAMPLE) != null) modifier = ModifierParser.Modifier.Example;
      if (x.peekChild(KW_COUNTEREXAMPLE) != null) modifier = ModifierParser.Modifier.Counterexample;
      if (x.peekChild(OPEN_KW) != null) modifier = ModifierParser.Modifier.Open;
      if (modifier == null) unreachable(x);

      return new WithPos<>(pos, modifier);
    });

    return new ModifierParser(reporter()).parse(modifiers, filter);
  }

  private @NotNull ModifierSet commonDeclModifiersOf(@NotNull GenericNode<?> node) {
    return declModifiersOf(node, x -> x.data() != ModifierParser.Modifier.Open);
  }

  private @NotNull ModifierSet moduleLikeDeclModifiersOf(@NotNull GenericNode<?> node) {
    return declModifiersOf(node, x -> true);
  }

  public TeleDecl.FnDecl fnDecl(@NotNull GenericNode<?> node) {
    var modifier = commonDeclModifiersOf(node);
    var acc = modifier.accessibility().data();
    var sample = modifier.personality().data();
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
    return new TeleDecl.FnDecl(
      nameOrInfix._1.sourcePos(),
      sourcePosOf(node),
      sample == Decl.Personality.NORMAL ? acc : Stmt.Accessibility.Private,
      modifiers.map(Tuple2::getValue).collect(Collectors.toCollection(
        () -> EnumSet.noneOf(Modifier.class))),
      nameOrInfix._2,
      nameOrInfix._1.data(),
      tele,
      typeOrNull(node.peekChild(TYPE)),
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

  private @NotNull ImmutableSeq<Stmt> giveMeOpen(@NotNull ModifierSet modiSet, @NotNull TeleDecl<?> decl) {
    if (!modiSet.isReExport().data()) return ImmutableSeq.empty();

    return ImmutableSeq.of(new Command.Open(
      modiSet.isReExport().sourcePos(),
      modiSet.accessibility().data(),
      new QualifiedID(decl.sourcePos(), decl.ref().name()),
      UseHide.EMPTY,
      modiSet.personality().data() == Decl.Personality.EXAMPLE,
      true
    ));
  }

  public @NotNull Tuple2<TeleDecl.DataDecl, ImmutableSeq<Stmt>>
  dataDecl(GenericNode<?> node) {
    var modifier = moduleLikeDeclModifiersOf(node);
    var acc = modifier.accessibility().data();
    var sample = modifier.personality().data();
    var bind = node.peekChild(BIND_BLOCK);
    var body = node.childrenOfType(DATA_BODY).map(this::dataBody).toImmutableSeq();
    var tele = telescope(node.childrenOfType(TELE).map(x -> x));
    var nameOrInfix = declNameOrInfix(node.child(DECL_NAME_OR_INFIX));
    var entire = sourcePosOf(node);
    var decl = new TeleDecl.DataDecl(
      nameOrInfix._1.sourcePos(),
      entire,
      sample == Decl.Personality.NORMAL ? acc : Stmt.Accessibility.Private,
      nameOrInfix._2,
      nameOrInfix._1.data(),
      tele,
      typeOrNull(node.peekChild(TYPE)),
      body,
      bind == null ? BindBlock.EMPTY : bindBlock(bind),
      sample
    );

    return Tuple.of(decl, giveMeOpen(modifier, decl));
  }

  public @NotNull TeleDecl.DataCtor dataBody(@NotNull GenericNode<?> node) {
    var dataCtorClause = node.peekChild(DATA_CTOR_CLAUSE);
    if (dataCtorClause != null) return dataCtorClause(dataCtorClause);
    var dataCtor = node.peekChild(DATA_CTOR);
    if (dataCtor != null) return dataCtor(ImmutableSeq.empty(), dataCtor);
    return unreachable(node);
  }

  public @NotNull TeleDecl.DataCtor dataCtorClause(@NotNull GenericNode<?> node) {
    return dataCtor(patterns(node.child(PATTERNS).child(COMMA_SEP)), node.child(DATA_CTOR));
  }

  public @NotNull Tuple2<TeleDecl.StructDecl, ImmutableSeq<Stmt>> structDecl(@NotNull GenericNode<?> node) {
    var modifier = moduleLikeDeclModifiersOf(node);
    var acc = modifier.accessibility().data();
    var sample = modifier.personality().data();
    var bind = node.peekChild(BIND_BLOCK);
    var fields = node.childrenOfType(STRUCT_FIELD).map(this::structField).toImmutableSeq();
    var tele = telescope(node.childrenOfType(TELE).map(x -> x));
    var nameOrInfix = declNameOrInfix(node.child(DECL_NAME_OR_INFIX));
    var decl = new TeleDecl.StructDecl(
      nameOrInfix._1.sourcePos(),
      sourcePosOf(node),
      sample == Decl.Personality.NORMAL ? acc : Stmt.Accessibility.Private,
      nameOrInfix._2,
      nameOrInfix._1.data(),
      tele,
      typeOrNull(node.peekChild(TYPE)),
      fields,
      bind == null ? BindBlock.EMPTY : bindBlock(bind),
      sample
    );
    return Tuple.of(decl, giveMeOpen(modifier, decl));
  }

  public @NotNull TeleDecl.StructField structField(GenericNode<?> node) {
    var tele = telescope(node.childrenOfType(TELE).map(x -> x));
    var nameOrInfix = declNameOrInfix(node.child(DECL_NAME_OR_INFIX));
    var bind = node.peekChild(BIND_BLOCK);
    return new TeleDecl.StructField(
      nameOrInfix._1.sourcePos(),
      sourcePosOf(node),
      nameOrInfix._2,
      nameOrInfix._1.data(),
      tele,
      typeOrNull(node.peekChild(TYPE)),
      Option.ofNullable(node.peekChild(EXPR)).map(this::expr),
      node.peekChild(KW_COERCE) != null,
      bind == null ? BindBlock.EMPTY : bindBlock(bind)
    );
  }

  public @NotNull TeleDecl.PrimDecl primDecl(@NotNull GenericNode<?> node) {
    var id = primName(node.child(PRIM_NAME));
    return new TeleDecl.PrimDecl(
      id.sourcePos(),
      sourcePosOf(node),
      id.data(),
      telescope(node.childrenOfType(TELE).map(x -> x)),
      typeOrNull(node.peekChild(TYPE))
    );
  }

  public @NotNull TeleDecl.DataCtor dataCtor(@NotNull ImmutableSeq<Arg<Pattern>> patterns, @NotNull GenericNode<?> node) {
    var tele = telescope(node.childrenOfType(TELE).map(x -> x));
    var nameOrInfix = declNameOrInfix(node.child(DECL_NAME_OR_INFIX));
    var bind = node.peekChild(BIND_BLOCK);
    var partial = node.peekChild(PARTIAL_BLOCK);
    var ty = node.peekChild(TYPE);
    var namePos = nameOrInfix._1.sourcePos();
    var ctor = new TeleDecl.DataCtor(
      namePos,
      sourcePosOf(node),
      nameOrInfix._2,
      nameOrInfix._1.data(),
      tele,
      partial(partial, partial != null ? sourcePosOf(partial) : namePos),
      patterns,
      node.peekChild(KW_COERCE) != null,
      bind == null ? BindBlock.EMPTY : bindBlock(bind)
    );
    if (ty != null) ctor.result = type(ty);
    return ctor;
  }

  public @NotNull ImmutableSeq<Expr.Param> telescope(SeqView<GenericNode<?>> telescope) {
    return telescope.flatMap(this::tele).toImmutableSeq();
  }

  public @NotNull ImmutableSeq<Expr.Param> tele(@NotNull GenericNode<?> node) {
    var tele = node.peekChild(LICIT);
    if (tele != null) return licit(tele, TELE_BINDER, this::teleBinder);
    var type = expr(node.child(EXPR));
    var pos = sourcePosOf(node);
    return ImmutableSeq.of(new Expr.Param(pos, Constants.randomlyNamed(pos), type, true));
  }

  public @NotNull ImmutableSeq<Expr.Param> teleBinder(boolean explicit, @NotNull GenericNode<?> node) {
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
    var ids = teleBinderUntyped(node.child(TELE_BINDER_UNTYPED));
    var type = type(node.child(TYPE));
    return ids.map(i -> new Expr.Param(
      i.sourcePos(), LocalVar.from(i), type, explicit));
  }

  private @NotNull ImmutableSeq<WithPos<String>> teleBinderUntyped(@NotNull GenericNode<?> node) {
    return node.childrenOfType(TELE_PARAM_NAME).map(this::teleParamName).toImmutableSeq();
  }

  public @NotNull ImmutableSeq<Expr.Param> lambdaTelescope(SeqView<GenericNode<?>> telescope) {
    return telescope.flatMap(this::lambdaTele).toImmutableSeq();
  }

  public @NotNull ImmutableSeq<Expr.Param> lambdaTele(@NotNull GenericNode<?> node) {
    var teleParamName = node.peekChild(TELE_PARAM_NAME);
    if (teleParamName != null) return lambdaTeleLit(teleParamName, true, sourcePosOf(node));
    return licit(node.child(LICIT), LAMBDA_TELE_BINDER, this::lambdaTeleBinder);
  }

  @FunctionalInterface
  private interface LicitParser<T> extends BooleanObjBiFunction<GenericNode<?>, T> {
  }

  private <T> @NotNull T licit(@NotNull GenericNode<?> node, @NotNull IElementType type, @NotNull LicitParser<T> parser) {
    var child = node.child(type);
    return parser.apply(node.peekChild(LBRACE) == null, child);
  }

  public @NotNull ImmutableSeq<Expr.Param> lambdaTeleBinder(boolean explicit, @NotNull GenericNode<?> node) {
    var pos = sourcePosOf(node);

    // | teleBinderTyped
    var typed = node.peekChild(TELE_BINDER_TYPED);
    if (typed != null) return teleBinderTyped(typed, explicit);

    // | teleBinderUntyped
    var ids = node.child(TELE_BINDER_UNTYPED);
    return teleBinderUntyped(ids).view()
      .map(LocalVar::from)
      .map(bind -> new Expr.Param(pos, bind, typeOrHole(null, pos), explicit)).toImmutableSeq();
  }

  private @NotNull ImmutableSeq<Expr.Param> lambdaTeleLit(GenericNode<?> node, boolean explicit, SourcePos pos) {
    return ImmutableSeq.of(new Expr.Param(pos,
      LocalVar.from(teleParamName(node)), typeOrHole(null, pos), explicit));
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
    if (node.is(REF_EXPR)) {
      var qid = qualifiedId(node.child(QUALIFIED_ID));
      return new Expr.Unresolved(qid.sourcePos(), qid);
    }
    if (node.is(CALM_FACE_EXPR)) return new Expr.Hole(pos, false, null);
    if (node.is(GOAL_EXPR)) {
      var fillingExpr = node.peekChild(EXPR);
      var filling = fillingExpr == null ? null : expr(fillingExpr);
      return new Expr.Hole(pos, true, filling);
    }
    if (node.is(UNIV_EXPR)) {
      if (node.peekChild(KW_TYPE) != null) return new Expr.RawSort(pos, SortKind.Type);
      if (node.peekChild(KW_SET) != null) return new Expr.RawSort(pos, SortKind.Set);
      if (node.peekChild(KW_PROP) != null) return new Expr.RawSort(pos, SortKind.Prop);
      if (node.peekChild(KW_ISET) != null) return new Expr.RawSort(pos, SortKind.ISet);
      return unreachable(node);
    }
    if (node.is(LIT_INT_EXPR)) try {
      return new Expr.LitInt(pos, Integer.parseInt(node.tokenText()));
    } catch (NumberFormatException ignored) {
      reporter.report(new ParseError(pos, "Unsupported integer literal `" + node.tokenText() + "`"));
      throw new ParsingInterruptedException();
    }
    if (node.is(LIT_STRING_EXPR)) {
      var text = StringView.of(node.tokenText());
      var content = text.substring(1, text.length() - 1);
      return new Expr.LitString(pos, StringUtil.escapeStringCharacters(content.toString()));
    }
    if (node.is(ULIFT_ATOM)) {
      var expr = expr(node.child(EXPR));
      var lifts = node.childrenOfType(ULIFT_PREFIX).collect(Collectors.summingInt(kw -> {
        var text = kw.tokenText();
        if ("ulift".equals(text)) return 1;
        else return text.length();
      }));
      return lifts > 0 ? new Expr.Lift(sourcePosOf(node), expr, lifts) : expr;
    }
    if (node.is(TUPLE_ATOM)) {
      var expr = node.child(COMMA_SEP).childrenOfType(EXPR).toImmutableSeq();
      if (expr.size() == 1) return newBinOPScope(expr(expr.get(0)));
      return new Expr.Tuple(sourcePosOf(node), expr.map(this::expr));
    }
    if (node.is(APP_EXPR)) {
      var head = new Expr.NamedArg(true, expr(node.child(EXPR)));
      var tail = node.childrenOfType(ARGUMENT)
        .map(this::argument)
        .collect(MutableSinglyLinkedList.factory());
      tail.push(head);
      return new Expr.BinOpSeq(pos, tail.toImmutableSeq());
    }
    if (node.is(PROJ_EXPR)) return buildProj(pos, expr(node.child(EXPR)), node.child(PROJ_FIX));
    if (node.is(MATCH_EXPR)) {
      var clauses = node.child(CLAUSES);
      var bare = clauses.childrenOfType(BARE_CLAUSE).map(this::bareOrBarredClause);
      var barred = clauses.childrenOfType(BARRED_CLAUSE).map(this::bareOrBarredClause);
      return new Expr.Match(
        sourcePosOf(node),
        node.child(COMMA_SEP).childrenOfType(EXPR).map(this::expr).toImmutableSeq(),
        bare.concat(barred).toImmutableSeq()
      );
    }
    if (node.is(ARROW_EXPR)) {
      var exprs = node.childrenOfType(EXPR);
      var expr0 = exprs.get(0);
      var to = expr(exprs.get(1));
      var paramPos = sourcePosOf(expr0);
      var param = new Expr.Param(paramPos, Constants.randomlyNamed(paramPos), expr(expr0), true);
      return new Expr.Pi(pos, param, to);
    }
    if (node.is(NEW_EXPR)) {
      var struct = expr(node.child(EXPR));
      var newBody = node.peekChild(NEW_BODY);
      return new Expr.New(pos, struct,
        newBody == null
          ? ImmutableSeq.empty()
          : newBody.childrenOfType(NEW_ARG).map(arg -> {
            var id = newArgField(arg.child(NEW_ARG_FIELD));
            var bindings = arg.childrenOfType(TELE_PARAM_NAME).map(this::teleParamName)
              .map(b -> b.map($ -> LocalVar.from(b)))
              .toImmutableSeq();
            var body = expr(arg.child(EXPR));
            return new Expr.Field(id, bindings, body, MutableValue.create());
          }).toImmutableSeq());
    }
    if (node.is(PI_EXPR)) return Expr.buildPi(pos,
      telescope(node.childrenOfType(TELE).map(x -> x)).view(),
      expr(node.child(EXPR)));
    if (node.is(FORALL_EXPR)) return Expr.buildPi(pos,
      lambdaTelescope(node.childrenOfType(LAMBDA_TELE).map(x -> x)).view(),
      expr(node.child(EXPR)));
    if (node.is(SIGMA_EXPR)) {
      var last = expr(node.child(EXPR));
      return new Expr.Sigma(pos,
        telescope(node.childrenOfType(TELE).map(x -> x))
          .appended(new Expr.Param(last.sourcePos(), LocalVar.IGNORED, last, true)));
    }
    if (node.is(LAMBDA_EXPR)) {
      Expr result;
      var bodyExpr = node.peekChild(EXPR);
      if (bodyExpr == null) {
        var impliesToken = node.peekChild(IMPLIES);
        var bodyHolePos = impliesToken == null ? pos : sourcePosOf(impliesToken);
        result = new Expr.Hole(bodyHolePos, false, null);
      } else result = expr(bodyExpr);
      return Expr.buildLam(pos, lambdaTelescope(node.childrenOfType(LAMBDA_TELE).map(x -> x)).view(), result);
    }
    if (node.is(PARTIAL_ATOM)) return partial(node, pos);
    if (node.is(PATH_EXPR)) {
      var params = node.childrenOfType(PATH_TELE).map(t ->
        LocalVar.from(teleParamName(t.child(TELE_PARAM_NAME)))).toImmutableSeq();
      return new Expr.Path(pos, params, expr(node.child(EXPR)), partial(node.peekChild(PARTIAL_BLOCK), pos));
    }
    if (node.is(IDIOM_ATOM)) {
      var block = node.peekChild(IDIOM_BLOCK);
      var names = new Expr.IdiomNames(
        Constants.alternativeEmpty(pos),
        Constants.alternativeOr(pos),
        Constants.applicativeApp(pos),
        Constants.functorPure(pos)
      );
      if (block == null) return new Expr.Idiom(pos, names, ImmutableSeq.empty());
      return new Expr.Idiom(pos, names, block.childrenOfType(BARRED)
        .flatMap(child -> child.childrenOfType(EXPR))
        .map(this::expr)
        .appended(expr(block.child(EXPR)))
        .toImmutableSeq());
    }
    if (node.is(DO_EXPR)) {
      return new Expr.Do(pos, Constants.monadBind(SourcePos.NONE),
        node.child(COMMA_SEP).childrenOfType(DO_BLOCK_CONTENT)
          .map(e -> {
            var bind = e.peekChild(DO_BINDING);
            if (bind != null) {
              return doBinding(bind);
            }
            var expr = e.child(EXPR);
            return new Expr.DoBind(sourcePosOf(expr), LocalVar.IGNORED, expr(expr));
          })
          .toImmutableSeq());
    }
    if (node.is(ARRAY_ATOM)) {
      var arrayBlock = node.peekChild(ARRAY_BLOCK);
      if (arrayBlock == null) return Expr.Array.newList(pos, ImmutableSeq.empty());
      if (arrayBlock.is(ARRAY_COMP_BLOCK)) return arrayCompBlock(arrayBlock, pos);
      if (arrayBlock.is(ARRAY_ELEMENTS_BLOCK)) return arrayElementList(arrayBlock, pos);
    }
    if (node.is(LET_EXPR)) {
      var bindBlock = node.child(LET_BIND_BLOCK);
      var binds = bindBlock.childrenOfType(LET_BIND).map(this::letBind);
      var body = expr(node.child(EXPR));
      return Expr.buildLet(pos, binds, body);
    }
    return unreachable(node);
  }

  public @NotNull Expr.NamedArg argument(@NotNull GenericNode<?> node) {
    if (node.is(ATOM_EX_ARGUMENT)) {
      var fixes = node.childrenOfType(PROJ_FIX);
      var expr = expr(node.child(EXPR));
      var projected = fixes
        .foldLeft(Tuple.of(sourcePosOf(node), expr),
          (acc, proj) -> Tuple.of(acc._2.sourcePos(), buildProj(acc._1, acc._2, proj)))
        ._2;
      return new Expr.NamedArg(true, projected);
    }
    if (node.is(TUPLE_IM_ARGUMENT)) {
      var items = node.child(COMMA_SEP).childrenOfType(EXPR).map(this::expr).toImmutableSeq();
      if (items.sizeEquals(1)) return new Expr.NamedArg(false, newBinOPScope(items.first()));
      var tupExpr = new Expr.Tuple(sourcePosOf(node), items);
      return new Expr.NamedArg(false, tupExpr);
    }
    if (node.is(NAMED_IM_ARGUMENT)) {
      var id = weakId(node.child(WEAK_ID));
      return new Expr.NamedArg(false, id.data(), expr(node.child(EXPR)));
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

  private @NotNull Expr buildProj(
    @NotNull SourcePos sourcePos, @NotNull Expr projectee,
    @NotNull GenericNode<?> fix
  ) {
    var number = fix.peekChild(NUMBER);
    if (number != null) return new Expr.Proj(sourcePos, projectee, Either.left(
      Integer.parseInt(number.tokenText())));
    var qid = qualifiedId(fix.child(PROJ_FIX_ID).child(QUALIFIED_ID));
    var exprs = fix.childrenOfType(EXPR).toImmutableSeq();
    var coeLeft = exprs.getOption(0);
    var restr = exprs.getOption(1);
    return new Expr.RawProj(sourcePos, projectee, qid, null,
      coeLeft.map(this::expr).getOrNull(),
      restr.map(this::expr).getOrNull());
  }

  public @NotNull Arg<Pattern> pattern(@NotNull GenericNode<?> node) {
    var innerPattern = node.child(UNIT_PATTERNS);
    var entirePos = sourcePosOf(node);
    var innerPatternPos = sourcePosOf(innerPattern);

    var unitPats = unitPatterns(innerPattern);
    var as = Option.ofNullable(node.peekChild(WEAK_ID))
      .map(this::weakId)
      .map(LocalVar::from);

    // when no as, entirePos == innerPatternPos

    Arg<Pattern> pattern = unitPats.sizeEquals(1)
      ? unitPats.first()
      : new Arg<>(new Pattern.BinOpSeq(innerPatternPos, unitPats), true);
    return as.isDefined()
      ? Pattern.As.wrap(entirePos, pattern, as.get())
      : pattern;
  }

  private @NotNull ImmutableSeq<Arg<Pattern>> unitPatterns(@NotNull GenericNode<?> node) {
    return node.childrenOfType(UNIT_PATTERN)
      .map(this::unitPattern)
      .toImmutableSeq();
  }

  private Arg<Pattern> unitPattern(@NotNull GenericNode<?> node) {
    var rawPatterns = node.peekChild(LICIT);
    if (rawPatterns != null) return licit(rawPatterns, PATTERNS, (explicit, child) -> {
      child = child.child(COMMA_SEP);
      var patterns = patterns(child);
      var pat = patterns.sizeEquals(1)
        ? newBinOPScope(patterns.first().term(), explicit)
        : new Pattern.Tuple(sourcePosOf(node), patterns);
      return new Arg<>(pat, explicit);
    });
    return new Arg<>(atomPattern(node.childrenView().first()), true);
  }

  private @NotNull Pattern atomPattern(@NotNull GenericNode<?> node) {
    var sourcePos = sourcePosOf(node);
    if (node.is(ATOM_BIND_PATTERN)) {
      var qualifiedId = qualifiedId(node.child(QUALIFIED_ID));
      if (qualifiedId.isUnqualified()) {
        return new Pattern.Bind(sourcePos, LocalVar.from(new WithPos<>(qualifiedId.sourcePos(), qualifiedId.justName())));
      }
      return new Pattern.QualifiedRef(sourcePos, qualifiedId);
    }
    if (node.is(ATOM_LIST_PATTERN)) {
      var patternsNode = node.peekChild(PATTERNS);    // We allowed empty list pattern (nil)
      var patterns = patternsNode != null
        ? patterns(patternsNode.child(COMMA_SEP)).view()
        : SeqView.<Arg<Pattern>>empty();

      return new Pattern.List(sourcePos,
        patterns.map(pat -> {
          if (!pat.explicit()) {    // [ {a} ] is disallowed
            reporter.report(new ParseError(pat.term().sourcePos(), "Implicit elements in a list pattern is disallowed"));
          }
          return pat.term();
        }).toImmutableSeq());
    }
    if (node.peekChild(NUMBER) != null)
      return new Pattern.Number(sourcePos, Integer.parseInt(node.tokenText()));
    if (node.peekChild(LPAREN) != null) return new Pattern.Absurd(sourcePos);
    if (node.peekChild(CALM_FACE) != null) return new Pattern.CalmFace(sourcePos);
    return unreachable(node);
  }

  private @NotNull Expr.Array arrayCompBlock(@NotNull GenericNode<?> node, @NotNull SourcePos entireSourcePos) {
    // arrayCompBlock ::=
    //   expr  BAR listComp
    // [ x * y  |  x <- xs, y <- ys ]

    var generator = expr(node.child(EXPR));
    var bindings = node.child(COMMA_SEP)
      .childrenOfType(DO_BINDING)
      .map(this::doBinding)
      .toImmutableSeq();
    // Recommend: make these more precise: bind to `<-` and pure to `expr` (`x * y` in above)
    var bindName = Constants.monadBind(entireSourcePos);
    var pureName = Constants.functorPure(entireSourcePos);

    return Expr.Array.newGenerator(entireSourcePos, generator, bindings, bindName, pureName);
  }

  private @NotNull Expr.Array arrayElementList(@NotNull GenericNode<?> node, @NotNull SourcePos entireSourcePos) {
    // arrayElementBlock ::=
    //   exprList
    // [ 1, 2, 3 ]

    // Do we have to extract the producing of EXPR_LIST as a new function?
    var exprs = node.child(COMMA_SEP)
      .childrenOfType(EXPR)
      .map(this::expr)
      .toImmutableSeq();

    return Expr.Array.newList(entireSourcePos, exprs);
  }

  public @NotNull Expr.LetBind letBind(@NotNull GenericNode<?> node) {
    var pos = sourcePosOf(node);
    var bind = weakId(node.child(WEAK_ID));
    // make IDEA happy
    var teles = lambdaTelescope(node.childrenOfType(LAMBDA_TELE).map(x -> x));
    var result = typeOrHole(node.peekChild(TYPE), pos);
    var body = expr(node.child(EXPR));

    // The last element is a placeholder, which is meaningless
    return new Expr.LetBind(bind.sourcePos(), LocalVar.from(bind), teles, result, body);
    // ^ see `doBinding()` for why the source pos of `LetBind` should be `bind.sourcePos()`
  }

  public @NotNull ImmutableSeq<Arg<Pattern>> patterns(@NotNull GenericNode<?> node) {
    return node.childrenOfType(PATTERN).map(this::pattern).toImmutableSeq();
  }

  public @NotNull Pattern.Clause clause(@NotNull GenericNode<?> node) {
    return new Pattern.Clause(sourcePosOf(node), patterns(node.child(PATTERNS).child(COMMA_SEP)),
      Option.ofNullable(node.peekChild(EXPR)).map(this::expr));
  }

  public @NotNull Pattern.Clause bareOrBarredClause(@NotNull GenericNode<?> node) {
    return clause(node.child(CLAUSE));
  }

  public @NotNull Expr type(@NotNull GenericNode<?> node) {
    return expr(node.child(EXPR));
  }

  public @Nullable Expr typeOrNull(@Nullable GenericNode<?> node) {
    if (node == null) return null;
    return expr(node.child(EXPR));
  }

  public @NotNull Expr typeOrHole(@Nullable GenericNode<?> node, SourcePos sourcePos) {
    return node == null
      ? new Expr.Hole(sourcePos, false, null)
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

  public @NotNull QualifiedID qualifiedId(@NotNull GenericNode<?> node) {
    return new QualifiedID(sourcePosOf(node),
      node.childrenOfType(WEAK_ID)
        .map(this::weakId)
        .map(WithPos::data).toImmutableSeq());
  }

  public @NotNull Modifier fnModifier(@NotNull GenericNode<?> node) {
    if (node.peekChild(KW_OPAQUE) != null) return Modifier.Opaque;
    if (node.peekChild(KW_INLINE) != null) return Modifier.Inline;
    if (node.peekChild(KW_OVERLAP) != null) return Modifier.Overlap;
    return unreachable(node);
  }

  /**
   * This function assumed that the node is DO_BINDING
   */
  public @NotNull Expr.DoBind doBinding(@NotNull GenericNode<?> node) {
    var wp = weakId(node.child(WEAK_ID));
    return new Expr.DoBind(wp.sourcePos(), LocalVar.from(wp), expr(node.child(EXPR)));
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

  public @NotNull Pattern newBinOPScope(@NotNull Pattern expr, boolean explicit) {
    return new Pattern.BinOpSeq(expr.sourcePos(),
      ImmutableSeq.of(new Arg<>(expr, explicit)));
  }

  private @NotNull SourcePos sourcePosOf(@NotNull GenericNode<?> node) {
    return source.fold(file -> sourcePosOf(node, file), pos -> pos);
  }

  public static @NotNull SourcePos sourcePosOf(@NotNull GenericNode<?> node, @NotNull SourceFile file) {
    return sourcePosOf(node.range(), file, node.isTerminalNode());
  }

  public static @NotNull SourcePos sourcePosOf(@NotNull FlexLexer.Token token, @NotNull SourceFile file) {
    return sourcePosOf(token.range(), file, true);
  }

  public static @NotNull SourcePos sourcePosOf(@NotNull TextRange range, @NotNull SourceFile file, boolean isTerminal) {
    var start = StringUtil.offsetToLineColumn(file.sourceCode(), range.getStartOffset());
    var length = range.getLength();
    var endOffset = range.getEndOffset() - (length == 0 ? 0 : 1);
    var end = isTerminal || length == 0
      ? LineColumn.of(start.line, start.column + length - 1)
      : StringUtil.offsetToLineColumn(file.sourceCode(), endOffset);
    return new SourcePos(file, range.getStartOffset(), endOffset,
      start.line + 1, start.column, end.line + 1, end.column);
  }
}
