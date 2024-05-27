// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.prettier;

import kala.collection.Seq;
import kala.collection.SeqLike;
import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import org.aya.generic.AyaDocile;
import org.aya.generic.term.ParamLike;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Link;
import org.aya.pretty.doc.Style;
import org.aya.pretty.style.AyaStyleKey;
import org.aya.syntax.concrete.stmt.QualifiedID;
import org.aya.syntax.concrete.stmt.decl.*;
import org.aya.syntax.core.def.*;
import org.aya.syntax.core.term.Param;
import org.aya.syntax.ref.*;
import org.aya.util.Arg;
import org.aya.util.BinOpElem;
import org.aya.util.binop.Assoc;
import org.aya.util.prettier.PrettierOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.ToIntBiFunction;

import static org.aya.prettier.Tokens.KW_PRIM;

public abstract class BasePrettier<Term extends AyaDocile> {
  public static @NotNull Doc argDoc(@NotNull PrettierOptions options, @NotNull Arg<? extends AyaDocile> self) {
    return BasePrettier.arg((_, d) -> d.toDoc(options), self, Outer.Free);
  }

  public static @NotNull Doc argsDoc(@NotNull PrettierOptions options, @NotNull SeqView<Arg<? extends AyaDocile>> self) {
    return Doc.commaList(self.map(t -> argDoc(options, t)));
  }

  public static @NotNull Doc coreArgsDoc(@NotNull PrettierOptions options, @NotNull SeqView<? extends AyaDocile> self) {
    return Doc.commaList(self.map(t -> t.toDoc(options)));
  }

  @FunctionalInterface
  protected interface Fmt<T extends AyaDocile> extends BiFunction<Outer, T, Doc> {
  }

  public static final @NotNull Style KEYWORD = AyaStyleKey.Keyword.preset();
  public static final @NotNull Style ERROR = AyaStyleKey.Error.preset();
  public static final @NotNull Style GOAL = AyaStyleKey.Goal.preset();
  public static final @NotNull Style WARNING = AyaStyleKey.Warning.preset();
  // Annotate the "whole call expr" (not the call head!!) with this.
  public static final @NotNull Style CALL = AyaStyleKey.CallTerm.preset();
  // Annotate the "call head" with styles below.
  public static final @NotNull Style PRIM = AyaStyleKey.Prim.preset();
  public static final @NotNull Style FN = AyaStyleKey.Fn.preset();
  public static final @NotNull Style DATA = AyaStyleKey.Data.preset();
  public static final @NotNull Style CLAZZ = AyaStyleKey.Clazz.preset();
  public static final @NotNull Style CON = AyaStyleKey.Con.preset();
  public static final @NotNull Style MEMBER = AyaStyleKey.Member.preset();
  public static final @NotNull Style GENERALIZED = AyaStyleKey.Generalized.preset();
  public static final @NotNull Style COMMENT = AyaStyleKey.Comment.preset();
  public static final @NotNull Style LOCAL_VAR = AyaStyleKey.LocalVar.preset();

  public final @NotNull PrettierOptions options;

  protected BasePrettier(@NotNull PrettierOptions options) {
    this.options = options;
  }

  protected abstract @NotNull Doc term(@NotNull Outer outer, @NotNull Term term);

  public @NotNull Doc visitCoreApp(
    @Nullable Assoc assoc, @NotNull Doc fn,
    @NotNull SeqView<Term> args,
    @NotNull Outer outer, boolean showImplicits
  ) {
    return visitCalls(assoc, fn, this::term, outer, args.map(Arg::ofExplicitly), showImplicits);
  }

  public @NotNull Doc visitCalls(
    @Nullable Assoc assoc, @NotNull Doc fn,
    @NotNull SeqView<? extends @NotNull BinOpElem<Term>> args,
    @NotNull Outer outer, boolean showImplicits
  ) {
    return visitCalls(assoc, fn, this::term, outer, args, showImplicits);
  }

  public @NotNull Doc visitCoreCalls(
    @NotNull AnyDef var, @NotNull SeqLike<Term> args,
    @NotNull Outer outer, boolean showImplicits
  ) {
    var preArgs = args.toImmutableSeq();

    ImmutableSeq<Param> licit = ImmutableSeq.empty();
    // Because the signature of DataCon is selfTele, so we only need to deal with core con
    if (var instanceof TyckAnyDef<?> inner) {
      if (inner.core() instanceof SubLevelDef sub) licit = sub.selfTele;
      else if (inner.core() instanceof TyckDef tyck) licit = Objects.requireNonNull(tyck.ref().signature).rawParams();
    }
    // TODO: handle serialized code, where you can use telescopeLicit

    // licited args, note that this may not include all [var] args since [preArgs.size()] may less than [licit.size()]
    // this is safe since the core call is always fully applied, that is, no missing implicit arguments.
    var licitArgs = preArgs.zip(licit, (t, p) -> new Arg<>(t, p.explicit()));
    // explicit arguments, these are not [var] arguments, for example, a function [f {Nat} Nat : Nat -> Nat]
    // [explicitArgs.isEmpty] if licitArgs doesn't include all [var] args.
    var explicitArgs = preArgs.view().drop(licitArgs.size()).map(Arg::ofExplicitly);

    return visitCalls(var.assoc(), refVar(var), licitArgs.view().appendedAll(explicitArgs), outer, showImplicits);
  }

  /**
   * Pretty-print an application in a smart way.
   * If an infix operator is applied by two arguments, we use operator syntax.
   *
   * @param assoc Assoc of the applied function (if it is an operator)
   * @param fn    The applied function, pretty-printed.
   * @param fmt   Mostly just {@link #term(Outer, AyaDocile)}, but can be overridden.
   * @param <T>   Mostly <code>Term</code>.
   * @see #prefix(Doc, Fmt, Outer, SeqView)
   */
  <T extends AyaDocile> @NotNull Doc visitCalls(
    @Nullable Assoc assoc, @NotNull Doc fn, @NotNull Fmt<T> fmt, Outer outer,
    @NotNull SeqView<? extends @NotNull BinOpElem<@NotNull T>> args, boolean showImplicits
  ) {
    var visibleArgs = (showImplicits ? args : args.filter(BinOpElem::explicit)).toImmutableSeq();
    if (visibleArgs.isEmpty()) return assoc != null ? Doc.parened(fn) : fn;
    if (assoc != null) {
      var firstArg = visibleArgs.getFirst();
      if (!firstArg.explicit()) return prefix(Doc.parened(fn), fmt, outer, visibleArgs.view());
      var first = fmt.apply(Outer.BinOp, firstArg.term());
      if (assoc.isBinary()) {
        // If we're in a binApp/head/spine/etc., add parentheses
        if (visibleArgs.sizeEquals(1)) return checkParen(outer, Doc.sep(first, fn), Outer.BinOp);
        var triple = Doc.sep(first, fn, arg(fmt, visibleArgs.get(1), Outer.BinOp));
        if (visibleArgs.sizeEquals(2)) return checkParen(outer, triple, Outer.BinOp);
        return prefix(Doc.parened(triple), fmt, outer, visibleArgs.view().drop(2));
      }
      if (assoc.isUnary() && visibleArgs.sizeEquals(1)) {
        return checkParen(outer, Doc.sep(fn, first), Outer.BinOp);
      }
    }
    return Doc.styled(CALL, prefix(fn, fmt, outer, visibleArgs.view()));
  }

  /**
   * Pretty-print an application in a dumb (but conservative) way, using prefix syntax.
   *
   * @see #visitCalls(Assoc, Doc, Fmt, Outer, SeqView, boolean)
   */
  private <T extends AyaDocile> @NotNull Doc
  prefix(@NotNull Doc fn, @NotNull Fmt<T> fmt, Outer outer, SeqView<? extends @NotNull BinOpElem<T>> args) {
    var call = Doc.sep(fn, Doc.sep(args.map(arg ->
      arg(fmt, arg, Outer.AppSpine))));
    // If we're in a spine, add parentheses
    return checkParen(outer, call, Outer.AppSpine);
  }

  protected static <T extends AyaDocile> Doc arg(@NotNull Fmt<T> fmt, @NotNull BinOpElem<T> arg, @NotNull Outer outer) {
    if (arg.explicit()) return fmt.apply(outer, arg.term());
    return Doc.braced(fmt.apply(Outer.Free, arg.term()));
  }

  public static @NotNull Doc checkParen(@NotNull Outer outer, @NotNull Doc binApp, @NotNull Outer binOp) {
    return outer.ordinal() >= binOp.ordinal() ? Doc.parened(binApp) : binApp;
  }

  /**
   * This function does the following if necessary:
   * <ul>
   *   <li>Wrap the constructor with parentheses or braces</li>
   * </ul>
   *
   * @param ctorDoc  The constructor pretty-printed doc, without the 'as' or parentheses.
   * @param noParams Whether the constructor has no parameters or not.
   */
  @NotNull Doc ctorDoc(@NotNull Outer outer, boolean ex, Doc ctorDoc, boolean noParams) {
    var withEx = Doc.bracedUnless(ctorDoc, ex);
    return !ex ? withEx
      : outer != Outer.Free && !noParams
        ? Doc.parened(withEx)
        : withEx;
  }

  /**
   * Pretty-print a telescope in a dumb (but conservative) way.
   *
   * @see #visitTele(Seq, AyaDocile, Usage)
   */
  public @NotNull Doc visitTele(@NotNull Seq<? extends ParamLike<Term>> telescope) {
    return visitTele(telescope, null, (_, _) -> 1);
  }

  /**
   * Pretty-print a telescope in a smart way.
   * The bindings that are not used in the telescope/body are omitted.
   * Bindings of the same type (by 'same' I mean <code>Objects.equals</code> returns true)
   * are merged together.
   *
   * @param body  the body of the telescope (like the return type in a pi type),
   *              only used for finding usages (of the variables in the telescope).
   * @param altF7 a function for finding usages.
   * @see #visitTele(Seq)
   */
  public @NotNull Doc visitTele(
    @NotNull Seq<? extends ParamLike<Term>> telescope,
    @Nullable Term body, @NotNull Usage<Term, LocalVar> altF7
  ) {
    if (telescope.isEmpty()) return Doc.empty();
    var last = telescope.getFirst();
    var buf = MutableList.<Doc>create();

    // consecutive parameters of same type.
    var names = MutableList.of(last);
    for (int i = 1; i < telescope.size(); i++) {
      var param = telescope.get(i);
      if (!Objects.equals(param.type(), last.type())) {
        if (body != null && names.sizeEquals(1)) {
          var ref = names.getFirst();
          var used = telescope.sliceView(i, telescope.size())
            .map(ParamLike::type).appended(body)
            .anyMatch(p -> altF7.applyAsInt(p, ref.ref()) > 0);
          // We omit the name if there is no usage.
          if (!used) buf.append(justType(last, Outer.ProjHead));
          else buf.append(mutableListNames(names, last));
        } else buf.append(mutableListNames(names, last));
        names.clear();
        last = param;
      }
      names.append(param);
    }
    if (body != null && names.sizeEquals(1)
      && altF7.applyAsInt(body, names.getFirst().ref()) == 0) {
      buf.append(justType(last, Outer.ProjHead));
    } else buf.append(mutableListNames(names, last));
    return Doc.sep(buf);
  }

  @NotNull Doc justType(@NotNull Arg<Term> monika, Outer outer) {
    return monika.explicit() ? term(outer, monika.term())
      : Doc.braced(term(Outer.Free, monika.term()));
  }

  @NotNull Doc justType(@NotNull ParamLike<Term> sayori, @NotNull Outer outer) {
    return justType(new Arg<>(sayori.type(), sayori.explicit()), outer);
  }

  private Doc mutableListNames(
    MutableList<? extends ParamLike<?>> names,
    ParamLike<?> param
  ) {
    // We HAVE TO collect the results, since {names} is mutable, therefore {names.view()} becomes mutable.
    var namesDocs = names.view().map(ParamLike::nameDoc)
      .toImmutableSeq();
    return param.toDoc(Doc.sep(namesDocs), options);
  }

  @NotNull Doc lambdaParam(@NotNull ParamLike<?> param) {
    return options.map.get(AyaPrettierOptions.Key.ShowLambdaTypes) ? param.toDoc(options)
      : Doc.bracedUnless(param.nameDoc(), param.explicit());
  }

  protected boolean optionImplicit() {
    return options.map.get(AyaPrettierOptions.Key.ShowImplicitArgs);
  }

  /**
   * @param ref if it has type {@link DefVar}, use refVar or defVar instead.
   */
  public static @NotNull Doc varDoc(@NotNull AnyVar ref) {
    if (ref == LocalVar.IGNORED) return Doc.plain("_");
    return switch (ref) {
      case LocalVar _ -> linkRef(ref, LOCAL_VAR);
      case GeneralizedVar _ -> linkRef(ref, GENERALIZED);
      default -> Doc.linkRef(Doc.plain(ref.name()), linkIdOf(ref));
    };
  }

  static @NotNull Doc coe(boolean coerce) {
    return coerce ? Doc.styled(KEYWORD, "coerce") : Doc.empty();
  }

  static @NotNull Doc primDoc(DefVar<?, ?> ref) {
    return Doc.sep(KW_PRIM, refVar(ref));
  }

  private static @NotNull Doc linkDef(@NotNull AnyVar ref, @NotNull Style color) {
    return Doc.linkDef(Doc.styled(color, ref.name()), linkIdOf(ref));
  }

  private static @NotNull Doc linkRef(@NotNull AnyVar ref, @NotNull Style color) {
    return Doc.linkRef(Doc.styled(color, ref.name()), linkIdOf(ref));
  }

  private static @NotNull Doc linkRef(@NotNull AnyDef ref, @NotNull Style color) {
    return Doc.linkRef(Doc.styled(color, ref.name()), linkIdOf(ref));
  }

  public static @NotNull Link linkIdOf(@NotNull AnyVar ref) {
    return linkIdOf(null, ref);
  }

  public static @NotNull Link linkIdOf(@NotNull AnyDef ref) {
    return linkIdOf(null, ref);
  }

  // TODO: can we generalize the following two functions?
  public static @NotNull Link linkIdOf(@Nullable ModulePath currentFileModule, @NotNull AnyVar ref) {
    if (ref instanceof DefVar<?, ?> defVar) {
      var location = Link.loc(QualifiedID.join(new QName(defVar).asStringSeq()));
      // referring to the `ref` in its own module
      if (currentFileModule == null || defVar.module == null) return location;
      var fileModule = defVar.module.fileModule();
      if (fileModule.sameElements(currentFileModule))
        return location;
      // referring to the `ref` in another module
      return Link.cross(fileModule.module(), location);
    }
    return Link.loc(ref.hashCode());
  }

  public static @NotNull Link linkIdOf(@Nullable ModulePath currentFileModule, @NotNull AnyDef ref) {
    var location = Link.loc(QualifiedID.join(ref.qualifiedName().asStringSeq()));
    // referring to the `ref` in its own module
    if (currentFileModule == null || ref.fileModule().sameElements(currentFileModule))
      return location;
    // referring to the `ref` in another module
    return Link.cross(ref.fileModule().module(), location);
  }

  public static @NotNull Doc linkLit(int literal, @NotNull AnyDef ref, @NotNull Style color) {
    return Doc.linkRef(Doc.styled(color, Doc.plain(String.valueOf(literal))), linkIdOf(ref));
  }

  public static @NotNull Doc linkListLit(Doc display, @NotNull AnyDef ref, @NotNull Style color) {
    return Doc.linkDef(Doc.styled(color, display), linkIdOf(ref));
  }

  public static @NotNull Doc linkDef(@NotNull AnyVar ref) {
    return Doc.linkDef(Doc.plain(ref.name()), linkIdOf(ref));
  }

  public static @NotNull Doc refVar(DefVar<?, ?> ref) {
    var style = chooseStyle(ref);
    return style != null ? linkRef(ref, style) : varDoc(ref);
  }

  public static @NotNull Doc refVar(AnyDef ref) {
    var style = chooseStyle(ref);
    assert style != null;
    return linkRef(ref, style);
  }

  public static @NotNull Doc defVar(DefVar<?, ?> ref) {
    var style = chooseStyle(ref.concrete);
    return style != null ? linkDef(ref, style) : varDoc(ref);
  }

  protected static @Nullable Style chooseStyle(Object obj) {
    return switch (obj) {
      case DefVar<?, ?> d -> chooseStyle(d.concrete);
      case FnDecl _ -> FN;
      case DataDecl _ -> DATA;
      case PrimDecl _ -> PRIM;
      case FnDefLike _ -> FN;
      case DataDefLike _ -> DATA;
      case ConDefLike _ -> CON;
      case PrimDef _ -> PRIM;
      /*
      case ClassDecl d -> CLAZZ;
      case TeleDecl.ClassMember d -> MEMBER;
      */
      case null, default -> null;
    };
  }

  /**
   * Expression: where am I?
   *
   * <ul>
   *   <li>Top-level expression may not need parentheses, stone free!</li>
   *   <li>An argument of an application! Stay in parentheses!</li>
   *   <li>An operand of a binary application! Applications within are safe,
   *     but other binary applications are in danger!</li>
   *   <li>Codomain of a telescope</li>
   * </ul>
   */
  public enum Outer {
    Free,
    Codomain,
    BinOp,
    Domain,
    AppHead,
    AppSpine,
    ProjHead,
    Lifted
  }

  @FunctionalInterface
  public interface Usage<T, R> extends ToIntBiFunction<T, R> {
    sealed interface Ref {
      record Free(@NotNull LocalVar var) implements Ref { }
      record Meta(@NotNull MetaVar var) implements Ref { }
      enum AnyFree implements Ref { INSTANCE }
    }
  }
}
