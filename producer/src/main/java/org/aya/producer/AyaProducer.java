// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.producer;

import com.intellij.lexer.FlexLexer;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.LineColumn;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.collection.mutable.MutableSinglyLinkedList;
import kala.control.Either;
import kala.control.Option;
import kala.function.BooleanObjBiFunction;
import kala.value.MutableValue;
import org.aya.generic.Constants;
import org.aya.generic.Modifier;
import org.aya.generic.term.SortKind;
import org.aya.intellij.GenericNode;
import org.aya.parser.AyaPsiElementTypes;
import org.aya.parser.AyaPsiParser;
import org.aya.parser.AyaPsiTokenType;
import org.aya.producer.error.BadModifierWarn;
import org.aya.producer.error.ModifierProblem;
import org.aya.producer.error.ParseError;
import org.aya.syntax.concrete.Expr;
import org.aya.syntax.concrete.Pattern;
import org.aya.syntax.concrete.stmt.*;
import org.aya.syntax.concrete.stmt.decl.*;
import org.aya.syntax.ref.GeneralizedVar;
import org.aya.syntax.ref.LocalVar;
import org.aya.syntax.ref.ModulePath;
import org.aya.util.Arg;
import org.aya.util.binop.Assoc;
import org.aya.util.binop.OpDecl;
import org.aya.util.error.Panic;
import org.aya.util.error.SourceFile;
import org.aya.util.error.SourcePos;
import org.aya.util.error.WithPos;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
 *       the node itself from its parent. Available {@link TokenSet}s are {@link AyaProducer#EXPR}, {@link AyaProducer#STMT},
 *       {@link AyaProducer#ARGUMENT} and something alike.</li>
 *       <li>Use {@link GenericNode#is(IElementType)} to pattern-matching on the node.</li>
 *       <li>Note that extends nodes are flattened so producing concrete tree from parse tree is different from
 *       other nodes, compare {@link AyaProducer#expr(GenericNode)} and its bnf rule for more details.</li>
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
public record AyaProducer(
  @NotNull Either<SourceFile, SourcePos> source,
  @NotNull Reporter reporter
) {
  // NOTE: change here is you modified `extends` in `AyaPsiParser.bnf`
  public static final @NotNull TokenSet ARRAY_BLOCK = AyaPsiParser.EXTENDS_SETS_[0];
  public static final @NotNull TokenSet ARGUMENT = AyaPsiParser.EXTENDS_SETS_[2];
  public static final @NotNull TokenSet STMT = AyaPsiParser.EXTENDS_SETS_[3];
  public static final @NotNull TokenSet EXPR = AyaPsiParser.EXTENDS_SETS_[4];
  public static final @NotNull TokenSet DECL = TokenSet.create(DATA_DECL, FN_DECL, PRIM_DECL, CLASS_DECL);

  public @NotNull Either<ImmutableSeq<Stmt>, WithPos<Expr>> program(@NotNull GenericNode<?> node) {
    var repl = node.peekChild(EXPR);
    if (repl != null) return Either.right(expr(repl));
    return Either.left(node.childrenOfType(STMT).flatMap(this::stmt).toImmutableSeq());
  }

  public @NotNull ImmutableSeq<Stmt> stmt(@NotNull GenericNode<?> node) {
    if (node.is(IMPORT_CMD)) return ImmutableSeq.of(importCmd(node));
    if (node.is(MODULE)) return ImmutableSeq.of(module(node));
    if (node.is(OPEN_CMD)) return openCmd(node);
    if (node.is(DECL)) {
      var stmts = MutableList.<Stmt>create();
      var result = decl(node, stmts);
      if (result != null) stmts.prepend(result);
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
    var acc = node.peekChild(KW_PUBLIC);
    var asId = node.peekChild(WEAK_ID);
    var importMod = node.child(QUALIFIED_ID);
    return new Command.Import(
      sourcePosOf(importMod),
      modulePath(importMod),
      asId == null ? null : weakId(asId).data(),
      acc == null ? Stmt.Accessibility.Private : Stmt.Accessibility.Public
    );
  }

  public @NotNull ImmutableSeq<Stmt> openCmd(@NotNull GenericNode<?> node) {
    var accessibility = node.peekChild(KW_PUBLIC) == null
      ? Stmt.Accessibility.Private
      : Stmt.Accessibility.Public;
    var useHide = node.peekChild(USE_HIDE);
    var modNameNode = node.child(QUALIFIED_ID);
    var namePos = sourcePosOf(modNameNode);
    var modName = modulePath(modNameNode);
    var openImport = node.peekChild(KW_IMPORT) != null;
    var open = new Command.Open(
      namePos,
      accessibility,
      modName.asName(),
      useHide != null ? useHide(useHide) : UseHide.EMPTY,
      false,
      openImport
    );
    return openImport
      ? ImmutableSeq.of(new Command.Import(namePos, modName, null, accessibility), open)
      : ImmutableSeq.of(open);
  }

  public UseHide hideList(SeqView<? extends GenericNode<?>> hideLists, UseHide.Strategy strategy) {
    return new UseHide(hideLists
      .mapNotNull(h -> h.peekChild(COMMA_SEP))
      .flatMap(node -> node.childrenOfType(QUALIFIED_ID).map(this::qualifiedId))
      .map(UseHide.Name::new)
      .toImmutableSeq(),
      strategy);
  }

  public UseHide useList(SeqView<? extends GenericNode<?>> useLists, UseHide.Strategy strategy) {
    return new UseHide(useLists
      .mapNotNull(u -> u.peekChild(COMMA_SEP))
      .flatMap(this::useIdsComma)
      .toImmutableSeq(),
      strategy);
  }

  public SeqView<UseHide.Name> useIdsComma(@NotNull GenericNode<?> node) {
    return node.childrenOfType(USE_ID).map(id -> {
      var wholePos = sourcePosOf(id);
      var name = qualifiedId(id.child(QUALIFIED_ID));
      var useAs = id.peekChild(USE_AS);
      if (useAs == null) return new UseHide.Name(name);
      var asId = weakId(useAs.child(WEAK_ID)).data();
      var asAssoc = useAs.peekChild(ASSOC);
      var asBind = useAs.peekChild(BIND_BLOCK);
      return new UseHide.Name(wholePos, name, Option.some(asId),
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
    return new BindBlock(sourcePosOf(node),
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
      node.childrenOfType(HIDE_LIST),
      UseHide.Strategy.Hiding);
    if (node.peekChild(KW_USING) != null) return useList(
      node.childrenOfType(USE_LIST),
      UseHide.Strategy.Using);
    return unreachable(node);
  }

  public @NotNull Command.Module module(@NotNull GenericNode<?> node) {
    var modName = weakId(node.child(WEAK_ID));
    return new Command.Module(
      modName.sourcePos(), sourcePosOf(node), modName.data(),
      node.childrenOfType(STMT).flatMap(this::stmt).toImmutableSeq());
  }

  public @Nullable Decl decl(@NotNull GenericNode<?> node, @NotNull MutableList<Stmt> additional) {
    if (node.is(FN_DECL)) return fnDecl(node);
    if (node.is(PRIM_DECL)) return primDecl(node);
    if (node.is(DATA_DECL)) return dataDecl(node, additional);
    // if (node.is(CLASS_DECL)) return classDecl(node, additional);
    return unreachable(node);
  }

  /**
   * @return public if accessiblity is unspecified
   * @see ModifierParser#parse(ImmutableSeq, ModifierParser.Filter)
   */
  public @NotNull ModifierParser.Modifiers declModifiersOf(
    @NotNull GenericNode<?> node,
    @NotNull ModifierParser.Filter filter
  ) {
    var modifiers = node.childrenOfType(DECL_MODIFIERS).map(x -> {
      var pos = sourcePosOf(x);
      ModifierParser.CModifier modifier = null;
      for (var mod : ModifierParser.CModifier.values())
        if (x.peekChild(mod.type) != null) modifier = mod;
      if (modifier == null) unreachable(x);

      return new WithPos<>(pos, modifier);
    });

    return new ModifierParser(reporter()).parse(modifiers.toImmutableSeq(), filter);
  }

  private record DeclParseData(
    @NotNull GenericNode<?> node,
    @NotNull DeclInfo info,
    @Nullable String name
    , @NotNull ModifierParser.Modifiers modifier
  ) {
    public @Nullable String checkName(@NotNull AyaProducer self) {
      if (name != null) return name;
      return self.error(node.childrenView().getFirst(), "Expect a name");
    }
  }

  private @NotNull DeclParseData declInfo(
    @NotNull GenericNode<?> node, @NotNull ModifierParser.Filter filter
  ) {
    var modifier = declModifiersOf(node, filter);
    var bind = node.peekChild(BIND_BLOCK);
    var nameOrInfix = Option.ofNullable(declNameOrInfix(node.peekChild(DECL_NAME_OR_INFIX)));
    var wholePos = sourcePosOf(node);
    var info = new DeclInfo(
      modifier.accessibility().data(),
      nameOrInfix.map(x -> x.name.sourcePos()).getOrDefault(SourcePos.NONE),
      wholePos,
      nameOrInfix.map(DeclNameOrInfix::infix).getOrNull(),
      bind == null ? BindBlock.EMPTY : bindBlock(bind)
    );
    return new DeclParseData(node, info, nameOrInfix.map(x -> x.name.data()).getOrNull(), modifier);
  }

  public @Nullable FnDecl fnDecl(@NotNull GenericNode<?> node) {
    var info = declInfo(node, ModifierParser.FN_FILTER);
    var name = info.checkName(this);
    if (name == null) return null;

    var fnBodyNode = node.peekChild(FN_BODY);
    if (fnBodyNode == null) return error(node.childrenView().getFirst(), "Expect a function body");

    var fnMods = info.modifier().toFnModifiers();
    var tele = telescope(node.childrenOfType(TELE));
    var dynamite = fnBody(tele.map(Expr.Param::ref), fnBodyNode);
    if (dynamite == null) return null;
    var inline = info.modifier.misc(ModifierParser.CModifier.Inline);
    var overlap = info.modifier.misc(ModifierParser.CModifier.Overlap);
    if (dynamite instanceof FnBody.BlockBody && inline != null) {
      reporter.report(new BadModifierWarn(inline, Modifier.Inline));
    }
    if (dynamite instanceof FnBody.ExprBody && overlap != null) {
      reporter.report(new ModifierProblem(overlap, ModifierParser.CModifier.Overlap, ModifierProblem.Reason.Duplicative));
    }

    var ty = typeOrNull(node.peekChild(TYPE));
    var fnDecl = new FnDecl(info.info, fnMods, name, tele, ty, dynamite);
    if (info.modifier.isExample()) fnDecl.isExample = true;
    return fnDecl;
  }

  public @Nullable FnBody fnBody(@NotNull ImmutableSeq<LocalVar> vars, @NotNull GenericNode<?> node) {
    var expr = node.peekChild(EXPR);
    var implies = node.peekChild(IMPLIES);
    if (expr == null && implies != null) return error(implies, "Expect function body");
    if (expr != null) return new FnBody.ExprBody(expr(expr));
    var body = node.childrenOfType(BARRED_CLAUSE)
      .map(this::bareOrBarredClause).toImmutableSeq();
    var elims = node.childrenOfType(WEAK_ID)
      .map(this::weakId)
      .map(id -> new WithPos<>(id.sourcePos(),
        vars.find(v -> v.name().equals(id.data())).getOrDefault(LocalVar.IGNORED)))
      .toImmutableSeq();
    return new FnBody.BlockBody(body, elims);
  }

  private void giveMeOpen(@NotNull ModifierParser.Modifiers modiSet, @NotNull Decl decl, @NotNull MutableList<Stmt> additional) {
    var keyword = modiSet.misc(ModifierParser.CModifier.Open);
    if (keyword == null) return;

    additional.append(new Command.Open(
      keyword,
      modiSet.accessibility().data(),
      new ModuleName.Qualified(decl.ref().name()),
      UseHide.EMPTY,
      modiSet.isExample(),
      true
    ));
  }

  public @Nullable DataDecl dataDecl(GenericNode<?> node, @NotNull MutableList<Stmt> additional) {
    var body = node.childrenOfType(DATA_BODY).mapNotNull(this::dataBody).toImmutableSeq();
    var tele = telescope(node.childrenOfType(TELE));
    var info = declInfo(node, ModifierParser.DECL_FILTER);
    var name = info.checkName(this);
    if (name == null) return null;
    var ty = typeOrNull(node.peekChild(TYPE));
    var decl = new DataDecl(info.info, name, tele, ty, body);
    if (info.modifier.isExample()) decl.isExample = true;
    giveMeOpen(info.modifier, decl, additional);
    return decl;
  }

  public @Nullable DataCon dataBody(@NotNull GenericNode<?> node) {
    var dataConClause = node.peekChild(DATA_CON_CLAUSE);
    if (dataConClause != null) return dataCtor(
      patterns(dataConClause.child(PATTERNS).child(COMMA_SEP)),
      dataConClause.child(DATA_CON));
    var dataCon = node.peekChild(DATA_CON);
    if (dataCon != null) return dataCtor(ImmutableSeq.empty(), dataCon);
    return error(node.childrenView().getFirst(), "Expect a data constructor");
  }

  // public @Nullable ClassDecl classDecl(@NotNull GenericNode<?> node, @NotNull MutableList<Stmt> additional) {
  //   var info = declInfo(node, ModifierParser.DECL_FILTER);
  //   var name = info.checkName(this, true);
  //   if (name == null) return null;
  //   var members = node.childrenOfType(CLASS_MEMBER).map(this::classMember).toImmutableSeq();
  //   var decl = new ClassDecl(info.info, name, members);
  //   giveMeOpen(info.modifier, decl, additional);
  //   return decl;
  // }

  // public @NotNull TeleDecl.ClassMember classMember(GenericNode<?> node) {
  //   var tele = telescope(node.childrenOfType(TELE).map(x -> x));
  //   var info = declInfo(node, ModifierParser.SUBDECL_FILTER);
  //   var name = info.checkName(this, true);
  //   if (name == null) return unreachable(node);
  //   return new TeleDecl.ClassMember(
  //     info.info, name, tele,
  //     typeOrHole(node.peekChild(TYPE), info.info.sourcePos()),
  //     Option.ofNullable(node.peekChild(EXPR)).map(this::expr),
  //     node.peekChild(KW_COERCE) != null
  //   );
  // }

  private <T> @Nullable T error(@NotNull GenericNode<?> node, @NotNull String message) {
    reporter.report(new ParseError(sourcePosOf(node), message));
    return null;
  }

  public @Nullable PrimDecl primDecl(@NotNull GenericNode<?> node) {
    var nameEl = node.peekChild(PRIM_NAME);
    if (nameEl == null) return error(node.childrenView().getFirst(), "Expect a primitive's name");
    var id = weakId(nameEl.child(WEAK_ID));
    return new PrimDecl(
      id.sourcePos(),
      sourcePosOf(node),
      id.data(),
      telescope(node.childrenOfType(TELE)),
      typeOrNull(node.peekChild(TYPE))
    );
  }

  public @Nullable DataCon dataCtor(@NotNull ImmutableSeq<Arg<WithPos<Pattern>>> patterns, @NotNull GenericNode<?> node) {
    var info = declInfo(node, ModifierParser.SUBDECL_FILTER);
    var name = info.checkName(this);
    if (name == null) return null;
    var tele = telescope(node.childrenOfType(TELE));
    var ty = node.peekChild(TYPE);
    var coe = node.peekChild(KW_COERCE) != null;
    return new DataCon(info.info, name, patterns, tele, coe, ty == null ? null : type(ty));
  }

  public @NotNull ImmutableSeq<Expr.Param> telescope(SeqView<? extends GenericNode<?>> telescope) {
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
    return ids.map(i -> new Expr.Param(i.sourcePos(), LocalVar.from(i), type, explicit));
  }

  private @NotNull ImmutableSeq<WithPos<String>> teleBinderUntyped(@NotNull GenericNode<?> node) {
    return node.childrenOfType(TELE_PARAM_NAME).map(this::teleParamName).toImmutableSeq();
  }

  public @NotNull ImmutableSeq<Expr.Param> lambdaTelescope(SeqView<? extends GenericNode<?>> telescope) {
    return telescope.flatMap(this::lambdaTele).toImmutableSeq();
  }

  public @NotNull ImmutableSeq<Expr.Param> lambdaTele(@NotNull GenericNode<?> node) {
    var teleParamName = node.peekChild(TELE_PARAM_NAME);
    if (teleParamName != null) return lambdaTeleLit(teleParamName, sourcePosOf(node));
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
    // | teleBinderTyped
    var typed = node.peekChild(TELE_BINDER_TYPED);
    if (typed != null) return teleBinderTyped(typed, explicit);

    // | teleBinderUntyped
    var pos = sourcePosOf(node);
    var ids = node.child(TELE_BINDER_UNTYPED);
    return teleBinderUntyped(ids).view()
      .map(LocalVar::from)
      .map(bind -> new Expr.Param(bind.definition(), bind, typeOrHole(null, pos), explicit))
      .toImmutableSeq();
  }

  private @NotNull ImmutableSeq<Expr.Param> lambdaTeleLit(GenericNode<?> node, SourcePos pos) {
    return ImmutableSeq.of(new Expr.Param(pos,
      LocalVar.from(teleParamName(node)), typeOrHole(null, pos), true));
  }

  private record DeclNameOrInfix(@NotNull WithPos<String> name, @Nullable OpDecl.OpInfo infix) {
  }

  private @Nullable DeclNameOrInfix declNameOrInfix(@Nullable GenericNode<?> node) {
    if (node == null) return null;
    var assoc = node.peekChild(ASSOC);
    var id = weakId(node.child(WEAK_ID));
    if (assoc == null) return new DeclNameOrInfix(id, null);
    var infix = new OpDecl.OpInfo(id.data(), assoc(assoc));
    return new DeclNameOrInfix(new WithPos<>(id.sourcePos(), infix.name()), infix);
  }

  public @NotNull WithPos<Expr> expr(@NotNull GenericNode<?> node) {
    var pos = sourcePosOf(node);
    if (node.is(REF_EXPR)) {
      var qid = qualifiedId(node.child(QUALIFIED_ID));
      return new WithPos<>(pos, new Expr.Unresolved(qid));
    }
    if (node.is(CALM_FACE_EXPR)) return new WithPos<>(pos, new Expr.Hole(false, null));
    if (node.is(GOAL_EXPR)) {
      var fillingExpr = node.peekChild(EXPR);
      var filling = fillingExpr == null ? null : expr(fillingExpr);
      return new WithPos<>(pos, new Expr.Hole(true, filling));
    }
    if (node.is(UNIV_EXPR)) {
      if (node.peekChild(KW_TYPE) != null) return new WithPos<>(pos, new Expr.RawSort(SortKind.Type));
      if (node.peekChild(KW_SET) != null) return new WithPos<>(pos, new Expr.RawSort(SortKind.Set));
      if (node.peekChild(KW_ISET) != null) return new WithPos<>(pos, new Expr.RawSort(SortKind.ISet));
      return unreachable(node);
    }
    if (node.is(LIT_INT_EXPR)) try {
      return new WithPos<>(pos, new Expr.LitInt(node.tokenText().toInt()));
    } catch (NumberFormatException ignored) {
      reporter.report(new ParseError(pos, "Unsupported integer literal `" + node.tokenText() + "`"));
      throw new ParsingInterruptedException();
    }
    if (node.is(LIT_STRING_EXPR)) {
      var text = node.tokenText();
      var content = text.substring(1, text.length() - 1);
      return new WithPos<>(pos, new Expr.LitString(StringUtil.escapeStringCharacters(content.toString())));
    }
    if (node.is(ULIFT_ATOM)) {
      var expr = expr(node.child(EXPR));
      var lifts = node.childrenOfType(ULIFT_PREFIX).collect(Collectors.summingInt(kw -> {
        var text = kw.tokenText();
        if ("ulift".contentEquals(text)) return 1;
        else return text.length();
      }));
      return lifts > 0 ? new WithPos<>(pos, new Expr.Lift(expr, lifts)) : expr;
    }
    if (node.is(TUPLE_ATOM)) {
      var expr = node.child(COMMA_SEP).childrenOfType(EXPR).toImmutableSeq();
      if (expr.size() == 1) return newBinOPScope(expr(expr.get(0)));
      return new WithPos<>(pos, new Expr.Tuple(expr.map(this::expr)));
    }
    if (node.is(APP_EXPR)) {
      var head = new Expr.NamedArg(true, expr(node.child(EXPR)));
      var tail = node.childrenOfType(ARGUMENT)
        .map(this::argument)
        .collect(MutableSinglyLinkedList.factory());
      tail.push(head);
      return new WithPos<>(pos, new Expr.BinOpSeq(tail.toImmutableSeq()));
    }
    if (node.is(PROJ_EXPR)) return new WithPos<>(pos, buildProj(expr(node.child(EXPR)), node.child(PROJ_FIX)));
    // if (node.is(MATCH_EXPR)) {
    //   var clauses = node.child(CLAUSES);
    //   var bare = clauses.childrenOfType(BARE_CLAUSE).map(this::bareOrBarredClause);
    //   var barred = clauses.childrenOfType(BARRED_CLAUSE).map(this::bareOrBarredClause);
    //   return new Expr.Match(
    //     sourcePosOf(node),
    //     node.child(COMMA_SEP).childrenOfType(EXPR).map(this::expr).toImmutableSeq(),
    //     bare.concat(barred).toImmutableSeq()
    //   );
    // }
    if (node.is(ARROW_EXPR)) {
      var exprs = node.childrenOfType(EXPR);
      var expr0 = exprs.get(0);
      var to = expr(exprs.get(1));
      var paramPos = sourcePosOf(expr0);
      var param = new Expr.Param(paramPos, Constants.randomlyNamed(paramPos), expr(expr0), true);
      return new WithPos<>(pos, new Expr.Pi(param, to));
    }
    // if (node.is(NEW_EXPR)) {
    //   var struct = expr(node.child(EXPR));
    //   var newBody = node.peekChild(NEW_BODY);
    //   return new WithPos<>(pos, new Expr.New(pos, struct,
    //     newBody == null
    //       ? ImmutableSeq.empty()
    //       : newBody.childrenOfType(NEW_ARG).map(arg -> {
    //         var id = newArgField(arg.child(NEW_ARG_FIELD));
    //         var bindings = arg.childrenOfType(TELE_PARAM_NAME).map(this::teleParamName)
    //           .map(b -> b.map(_ -> LocalVar.from(b)))
    //           .toImmutableSeq();
    //         var body = expr(arg.child(EXPR));
    //         return new WithPos<>(pos, new Expr.Field<>(sourcePosOf(arg), id, bindings, body, MutableValue.create()));
    //       }).toImmutableSeq()));
    // }
    if (node.is(PI_EXPR)) return Expr.buildPi(pos,
      telescope(node.childrenOfType(TELE)).view(),
      expr(node.child(EXPR)));
    if (node.is(FORALL_EXPR)) return Expr.buildPi(pos,
      lambdaTelescope(node.childrenOfType(LAMBDA_TELE)).view(),
      expr(node.child(EXPR)));
    if (node.is(SIGMA_EXPR)) {
      var last = expr(node.child(EXPR));
      return new WithPos<>(pos, new Expr.Sigma(telescope(node.childrenOfType(TELE))
        .appended(new Expr.Param(last.sourcePos(), LocalVar.IGNORED, last, true))));
    }
    if (node.is(LAMBDA_EXPR)) {
      WithPos<Expr> result;
      var bodyExpr = node.peekChild(EXPR);
      if (bodyExpr == null) {
        var impliesToken = node.peekChild(IMPLIES);
        var bodyHolePos = impliesToken == null ? pos : sourcePosOf(impliesToken);
        result = new WithPos<>(bodyHolePos, new Expr.Hole(false, null));
      } else result = expr(bodyExpr);
      var tele = teleBinderUntyped(node.child(TELE_BINDER_UNTYPED)).view()
        .map(LocalVar::from)
        .map(v -> new Expr.Param(v.definition(), v, true));
      return Expr.buildLam(pos, tele, result);
    }
    if (node.is(IDIOM_ATOM)) {
      var block = node.peekChild(IDIOM_BLOCK);
      var names = new Expr.IdiomNames(
        Constants.alternativeEmpty,
        Constants.alternativeOr,
        Constants.applicativeApp,
        Constants.functorPure
      );
      if (block == null) return new WithPos<>(pos, new Expr.Idiom(names, ImmutableSeq.empty()));
      return new WithPos<>(pos, new Expr.Idiom(names, block.childrenOfType(BARRED)
        .flatMap(child -> child.childrenOfType(EXPR))
        .map(this::expr)
        .appended(expr(block.child(EXPR)))
        .toImmutableSeq()));
    }
    if (node.is(DO_EXPR)) {
      return new WithPos<>(pos, new Expr.Do(Constants.monadBind,
        node.child(COMMA_SEP).childrenOfType(DO_BLOCK_CONTENT)
          .map(e -> {
            var bind = e.peekChild(DO_BINDING);
            if (bind != null) {
              return doBinding(bind);
            }
            var expr = e.child(EXPR);
            return new Expr.DoBind(sourcePosOf(expr), LocalVar.IGNORED, expr(expr));
          })
          .toImmutableSeq()));
    }
    if (node.is(ARRAY_ATOM)) {
      var arrayBlock = node.peekChild(ARRAY_BLOCK);
      if (arrayBlock == null) return new WithPos<>(pos, Expr.Array.newList(ImmutableSeq.empty()));
      if (arrayBlock.is(ARRAY_COMP_BLOCK)) return new WithPos<>(pos, arrayCompBlock(arrayBlock));
      if (arrayBlock.is(ARRAY_ELEMENTS_BLOCK)) return new WithPos<>(pos, arrayElementList(arrayBlock));
    }
    if (node.is(LET_EXPR)) {
      var bindBlockMaybe = node.peekChild(LET_BIND_BLOCK);
      var body = expr(node.child(EXPR));
      if (bindBlockMaybe != null) {
        var binds = bindBlockMaybe.childrenOfType(LET_BIND).map(this::letBind);
        return Expr.buildLet(pos, binds, body);
      } else {
        // let open
        var component = qualifiedId(node.child(QUALIFIED_ID));
        var useHide = node.peekChild(USE_HIDE);

        return new WithPos<>(pos, new Expr.LetOpen(pos,
          component.component().resolve(component.name()),
          useHide == null ? UseHide.EMPTY : useHide(useHide),
          body));
      }
    }
    return unreachable(node);
  }

  public @NotNull Expr.NamedArg argument(@NotNull GenericNode<?> node) {
    var pos = sourcePosOf(node);
    if (node.is(ATOM_EX_ARGUMENT)) {
      var fixes = node.childrenOfType(PROJ_FIX);
      var expr = expr(node.child(EXPR));
      var projected = fixes.foldLeft(expr, (acc, proj) ->
        new WithPos<>(acc.sourcePos(), buildProj(acc, proj)));
      return new Expr.NamedArg(true, null, projected);
    }
    if (node.is(TUPLE_IM_ARGUMENT)) {
      var items = node.child(COMMA_SEP).childrenOfType(EXPR).map(this::expr).toImmutableSeq();
      if (items.sizeEquals(1)) return new Expr.NamedArg(false, newBinOPScope(items.getFirst()));
      var tupExpr = new WithPos<Expr>(pos, new Expr.Tuple(items));
      return new Expr.NamedArg(false, tupExpr);
    }
    if (node.is(NAMED_IM_ARGUMENT)) {
      var id = weakId(node.child(WEAK_ID));
      return new Expr.NamedArg(false, id.data(), expr(node.child(EXPR)));
    }
    return unreachable(node);
  }

  private @NotNull Expr buildProj(
    @NotNull WithPos<Expr> projectee,
    @NotNull GenericNode<?> fix
  ) {
    var number = fix.peekChild(NUMBER);
    if (number != null) return new Expr.Proj(projectee, Either.left(
      number.tokenText().toInt()));
    var qid = qualifiedId(fix.child(PROJ_FIX_ID).child(QUALIFIED_ID));
    return new Expr.Proj(projectee, Either.right(qid));
  }

  public @NotNull Arg<WithPos<Pattern>> pattern(@NotNull GenericNode<?> node) {
    var innerPattern = node.child(UNIT_PATTERNS);
    var entirePos = sourcePosOf(node);
    var innerPatternPos = sourcePosOf(innerPattern);

    var unitPats = unitPatterns(innerPattern);
    var as = Option.ofNullable(node.peekChild(WEAK_ID))
      .map(this::weakId)
      .map(LocalVar::from);

    // when no as, entirePos == innerPatternPos

    Arg<WithPos<Pattern>> pattern = unitPats.sizeEquals(1)
      ? unitPats.getFirst()
      : new Arg<>(new WithPos<>(innerPatternPos, new Pattern.BinOpSeq(unitPats)), true);
    return as.isDefined()
      ? Pattern.As.wrap(pattern, as.get()).map(x -> new WithPos<>(entirePos, x))
      : pattern;
  }

  private @NotNull ImmutableSeq<Arg<WithPos<Pattern>>> unitPatterns(@NotNull GenericNode<?> node) {
    return node.childrenOfType(UNIT_PATTERN)
      .map(this::unitPattern)
      .toImmutableSeq();
  }

  private Arg<WithPos<Pattern>> unitPattern(@NotNull GenericNode<?> node) {
    var rawPatterns = node.peekChild(LICIT);
    if (rawPatterns != null) return licit(rawPatterns, PATTERNS, (explicit, child) -> {
      child = child.child(COMMA_SEP);
      var patterns = patterns(child);
      Pattern pat;
      if (patterns.sizeEquals(1)) {
        pat = newBinOPScope(patterns.getFirst().term(), explicit);
      } else {
        // avoiding ({a}, b, {c})
        var implicitTupElem = patterns.filterNot(Arg::explicit);

        // This won't report error if implicitTupTerm.isEmpty
        implicitTupElem.forEach(p -> {
          var pos = p.term().sourcePos();
          reporter.report(new ParseError(pos, "Implicit pattern is not allowed here."));
        });

        pat = new Pattern.Tuple(patterns.map(Arg::term));
      }

      return new Arg<>(new WithPos<>(sourcePosOf(node), pat), explicit);
    });

    return new Arg<>(atomPattern(node.childrenView().getFirst()), true);
  }

  private @NotNull WithPos<Pattern> atomPattern(@NotNull GenericNode<?> node) {
    return new WithPos<>(sourcePosOf(node), doAtomPattern(node));
  }

  private @NotNull Pattern doAtomPattern(@NotNull GenericNode<?> node) {
    if (node.is(ATOM_BIND_PATTERN)) {
      var qualifiedId = qualifiedId(node.child(QUALIFIED_ID));
      if (qualifiedId.isUnqualified()) {
        return new Pattern.Bind(LocalVar.from(new WithPos<>(qualifiedId.sourcePos(), qualifiedId.name())));
      }
      return new Pattern.QualifiedRef(qualifiedId);
    }
    if (node.is(ATOM_LIST_PATTERN)) {
      var patternsNode = node.peekChild(PATTERNS);    // We allowed empty list pattern (nil)
      var patterns = patternsNode != null
        ? patterns(patternsNode.child(COMMA_SEP)).view()
        : SeqView.<Arg<WithPos<Pattern>>>empty();

      return new Pattern.List(
        patterns.map(pat -> {
          if (!pat.explicit()) {    // [ {a} ] is disallowed
            reporter.report(new ParseError(pat.term().sourcePos(), "Implicit elements in a list pattern is disallowed"));
          }
          return pat.term();
        }).toImmutableSeq());
    }
    if (node.peekChild(NUMBER) != null)
      return new Pattern.Number(node.tokenText().toInt());
    if (node.peekChild(LPAREN) != null) return Pattern.Absurd.INSTANCE;
    if (node.peekChild(CALM_FACE) != null) return Pattern.CalmFace.INSTANCE;
    return unreachable(node);
  }

  private @NotNull Expr.Array arrayCompBlock(@NotNull GenericNode<?> node) {
    // arrayCompBlock ::=
    //   expr  BAR listComp
    // [ x * y  |  x <- xs, y <- ys ]

    var generator = expr(node.child(EXPR));
    var bindings = node.child(COMMA_SEP)
      .childrenOfType(DO_BINDING)
      .map(this::doBinding)
      .toImmutableSeq();
    // Recommend: make these more precise: bind to `<-` and pure to `expr` (`x * y` in above)
    var names = new Expr.Array.ListCompNames(
      Constants.monadBind,
      Constants.functorPure
    );
    return Expr.Array.newGenerator(generator, bindings, names);
  }

  private @NotNull Expr.Array arrayElementList(@NotNull GenericNode<?> node) {
    // arrayElementBlock ::=
    //   exprList
    // [ 1, 2, 3 ]

    // Do we have to extract the producing of EXPR_LIST as a new function?
    var exprs = node.child(COMMA_SEP)
      .childrenOfType(EXPR)
      .map(this::expr)
      .toImmutableSeq();

    return Expr.Array.newList(exprs);
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

  public @NotNull ImmutableSeq<Arg<WithPos<Pattern>>> patterns(@NotNull GenericNode<?> node) {
    return node.childrenOfType(PATTERN).map(this::pattern).toImmutableSeq();
  }

  public @NotNull Pattern.Clause clause(@NotNull GenericNode<?> node) {
    return new Pattern.Clause(sourcePosOf(node), patterns(node.child(PATTERNS).child(COMMA_SEP)),
      Option.ofNullable(node.peekChild(EXPR)).map(this::expr));
  }

  public @NotNull Pattern.Clause bareOrBarredClause(@NotNull GenericNode<?> node) {
    return clause(node.child(CLAUSE));
  }

  public @NotNull WithPos<Expr> type(@NotNull GenericNode<?> node) {
    return expr(node.child(EXPR));
  }

  public @Nullable WithPos<Expr> typeOrNull(@Nullable GenericNode<?> node) {
    if (node == null) return null;
    var child = node.peekChild(EXPR);
    if (child == null) {
      reporter.report(new ParseError(sourcePosOf(node), "Expect the return type expression"));
      return null;
    }
    return expr(child);
  }

  public @NotNull WithPos<Expr> typeOrHole(@Nullable GenericNode<?> node, @NotNull SourcePos pos) {
    return node == null
      ? new WithPos<>(pos, new Expr.Hole(false, null))
      : type(node);
  }

  public @NotNull WithPos<String> weakId(@NotNull GenericNode<?> node) {
    return new WithPos<>(sourcePosOf(node), node.tokenText().toString());
  }

  public @NotNull WithPos<String> generalizeParamName(@NotNull GenericNode<?> node) {
    return teleParamName(node.child(TELE_PARAM_NAME));
  }

  public @NotNull WithPos<String> teleParamName(@NotNull GenericNode<?> node) {
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

  public @NotNull ModulePath modulePath(@NotNull GenericNode<?> node) {
    return new ModulePath(node.childrenOfType(WEAK_ID)
      .map(this::weakId)
      .map(WithPos::data).toImmutableSeq());
  }

  /**
   * This function assumed that the node is DO_BINDING
   */
  public @NotNull Expr.DoBind doBinding(@NotNull GenericNode<?> node) {
    var wp = weakId(node.child(WEAK_ID));
    return new Expr.DoBind(wp.sourcePos(), LocalVar.from(wp), expr(node.child(EXPR)));
  }

  private <T> T unreachable(GenericNode<?> node) {
    throw new Panic(node.elementType() + ": " + node.tokenText());
  }

  /**
   * [kiva]: make `(expr)` into a new BinOP parser scope
   * so the `f (+)` becomes passing `+` as an argument to function `f`.
   */
  public @NotNull WithPos<Expr> newBinOPScope(@NotNull WithPos<Expr> expr) {
    return new WithPos<>(
      expr.sourcePos(),
      new Expr.BinOpSeq(ImmutableSeq.of(new Expr.NamedArg(true, expr)))
    );
  }

  public @NotNull Pattern newBinOPScope(@NotNull WithPos<Pattern> expr, boolean explicit) {
    return new Pattern.BinOpSeq(ImmutableSeq.of(new Arg<>(expr, explicit)));
  }

  private @NotNull SourcePos sourcePosOf(@NotNull GenericNode<?> node) {
    return source.fold(file -> sourcePosOf(node, file), pos -> pos);
  }

  public static @NotNull SourcePos sourcePosOf(@NotNull GenericNode<?> node, @NotNull SourceFile file) {
    return sourcePosOf(node.range(), file, isTerminalNode(node));
  }

  public static boolean isTerminalNode(@NotNull GenericNode<?> node) {
    return node.elementType() instanceof AyaPsiTokenType;
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
