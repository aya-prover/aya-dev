// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.distill;

import kala.collection.Seq;
import kala.collection.SeqLike;
import kala.collection.SeqView;
import kala.collection.mutable.MutableList;
import org.aya.concrete.stmt.QualifiedID;
import org.aya.concrete.stmt.TeleDecl;
import org.aya.generic.AyaDocile;
import org.aya.generic.ParamLike;
import org.aya.guest0x0.cubical.Formula;
import org.aya.guest0x0.cubical.Partial;
import org.aya.guest0x0.cubical.Restr;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Link;
import org.aya.pretty.doc.Style;
import org.aya.pretty.style.AyaStyleKey;
import org.aya.ref.AnyVar;
import org.aya.ref.DefVar;
import org.aya.ref.LocalVar;
import org.aya.util.Arg;
import org.aya.util.binop.Assoc;
import org.aya.util.binop.BinOpParser;
import org.aya.util.distill.DistillerOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.ToIntBiFunction;

/**
 * @author ice1000
 */
public abstract class BaseDistiller<Term extends AyaDocile> {
  public static <T extends AyaDocile> @NotNull Doc toDoc(@NotNull DistillerOptions options, @NotNull Arg<T> self) {
    return BaseDistiller.arg((outer, d) -> d.toDoc(options), self, Outer.Free);
  }

  @FunctionalInterface
  protected interface Fmt<T extends AyaDocile> extends BiFunction<Outer, T, Doc> {
  }

  public static final @NotNull Style KEYWORD = AyaStyleKey.Keyword.preset();
  public static final @NotNull Style PRIM_CALL = AyaStyleKey.Prim.preset();
  public static final @NotNull Style FN_CALL = AyaStyleKey.Fn.preset();
  public static final @NotNull Style DATA_CALL = AyaStyleKey.Data.preset();
  public static final @NotNull Style STRUCT_CALL = AyaStyleKey.Struct.preset();
  public static final @NotNull Style CON_CALL = AyaStyleKey.Con.preset();
  public static final @NotNull Style FIELD_CALL = AyaStyleKey.Field.preset();
  public static final @NotNull Style GENERALIZED = AyaStyleKey.Generalized.preset();

  public final @NotNull DistillerOptions options;

  protected BaseDistiller(@NotNull DistillerOptions options) {
    this.options = options;
  }

  protected abstract @NotNull Doc term(@NotNull Outer outer, @NotNull Term term);

  public @NotNull Doc visitCalls(
    @Nullable Assoc assoc, @NotNull Doc fn,
    @NotNull SeqView<? extends BinOpParser.@NotNull Elem<Term>> args,
    @NotNull Outer outer, boolean showImplicits
  ) {
    return visitCalls(assoc, fn, this::term, outer, args, showImplicits);
  }

  public @NotNull Doc visitCalls(
    @NotNull DefVar<?, ?> var, @NotNull Style style,
    @NotNull SeqLike<@NotNull Arg<Term>> args,
    @NotNull Outer outer, boolean showImplicits
  ) {
    return visitCalls(var.assoc(), linkRef(var, style), args.view(), outer, showImplicits);
  }

  public @NotNull Doc visitArgsCalls(
    @NotNull DefVar<?, ?> var, @NotNull Style style,
    @NotNull SeqLike<@NotNull Arg<Term>> args, @NotNull Outer outer
  ) {
    return visitCalls(var, style, args, outer, options.map.get(AyaDistillerOptions.Key.ShowImplicitArgs));
  }

  /**
   * Pretty-print an application in a smart way.
   * If an infix operator is applied by two arguments, we use operator syntax.
   *
   * @param assoc Assoc of the applied function (if it is a operator)
   * @param fn    The applied function, pretty-printed.
   * @param fmt   Mostly just {@link #term(Outer, AyaDocile)}, but can be overridden.
   * @param <T>   Mostly <code>Term</code>.
   * @see #prefix(Doc, Fmt, Outer, SeqView)
   */
  <T extends AyaDocile> @NotNull Doc visitCalls(
    @Nullable Assoc assoc, @NotNull Doc fn, @NotNull Fmt<T> fmt, Outer outer,
    @NotNull SeqView<? extends BinOpParser.@NotNull Elem<@NotNull T>> args, boolean showImplicits
  ) {
    var visibleArgs = (showImplicits ? args : args.filter(BinOpParser.Elem::explicit)).toImmutableSeq();
    if (visibleArgs.isEmpty()) return assoc != null ? Doc.parened(fn) : fn;
    if (assoc != null) {
      var firstArg = visibleArgs.first();
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
    return prefix(fn, fmt, outer, visibleArgs.view());
  }

  /**
   * Pretty-print an application in a dumb (but conservative) way, using prefix syntax.
   *
   * @see #visitCalls(Assoc, Doc, Fmt, Outer, SeqView, boolean)
   */
  private <T extends AyaDocile> @NotNull Doc
  prefix(@NotNull Doc fn, @NotNull Fmt<T> fmt, Outer outer, SeqView<? extends BinOpParser.@NotNull Elem<T>> args) {
    var call = Doc.sep(fn, Doc.sep(args.map(arg ->
      arg(fmt, arg, Outer.AppSpine))));
    // If we're in a spine, add parentheses
    return checkParen(outer, call, Outer.AppSpine);
  }

  public static <T extends AyaDocile> Doc arg(@NotNull Fmt<T> fmt, @NotNull BinOpParser.Elem<T> arg, @NotNull Outer outer) {
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
    return !ex
      ? withEx
      : outer != Outer.Free && !noParams
        ? Doc.parened(withEx)
        : withEx;
  }

  /**
   * Pretty-print a telescope in a dumb (but conservative) way.
   *
   * @see #visitTele(Seq, AyaDocile, ToIntBiFunction)
   */
  public @NotNull Doc visitTele(@NotNull Seq<? extends ParamLike<Term>> telescope) {
    return visitTele(telescope, null, (t, v) -> 1);
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
    @Nullable Term body, @NotNull ToIntBiFunction<Term, AnyVar> altF7
  ) {
    if (telescope.isEmpty()) return Doc.empty();
    var last = telescope.first();
    var buf = MutableList.<Doc>create();
    var names = MutableList.of(last.ref());
    for (int i = 1; i < telescope.size(); i++) {
      var param = telescope.get(i);
      if (!Objects.equals(param.type(), last.type())) {
        if (body != null && names.sizeEquals(1)) {
          var ref = names.first();
          var used = telescope.sliceView(i, telescope.size())
            .map(ParamLike::type).appended(body)
            .anyMatch(p -> altF7.applyAsInt(p, ref) > 0);
          if (!used) buf.append(justType(last, Outer.ProjHead));
          else buf.append(mutableListNames(names, last));
        } else buf.append(mutableListNames(names, last));
        names.clear();
        last = param;
      }
      names.append(param.ref());
    }
    if (body != null && names.sizeEquals(1)
      && altF7.applyAsInt(body, names.first()) == 0) {
      buf.append(justType(last, Outer.ProjHead));
    } else buf.append(mutableListNames(names, last));
    return Doc.sep(buf);
  }

  @NotNull Doc justType(@NotNull ParamLike<Term> monika, Outer outer) {
    return monika.explicit() ? term(outer, monika.type())
      : Doc.braced(term(Outer.Free, monika.type()));
  }

  /** @implNote do NOT remove the <code>toImmSeq</code> call!!! */
  private Doc mutableListNames(MutableList<LocalVar> names, ParamLike<?> param) {
    return param.toDoc(Doc.sep(names.view().map(BaseDistiller::linkDef).toImmutableSeq()), options);
  }

  @NotNull Doc lambdaParam(@NotNull ParamLike<?> param) {
    return options.map.get(AyaDistillerOptions.Key.ShowLambdaTypes) ? param.toDoc(options)
      : Doc.bracedUnless(param.nameDoc(), param.explicit());
  }

  public static @NotNull Doc varDoc(@NotNull AnyVar ref) {
    if (ref == LocalVar.IGNORED) return Doc.plain("_");
    else return Doc.linkRef(Doc.plain(ref.name()), linkIdOf(ref));
  }

  static @NotNull Doc coe(boolean coerce) {
    return coerce ? Doc.styled(KEYWORD, "coerce") : Doc.empty();
  }

  static @NotNull Doc primDoc(AnyVar ref) {
    return Doc.sep(Doc.styled(KEYWORD, "prim"), linkDef(ref, PRIM_CALL));
  }

  public static @NotNull Doc linkDef(@NotNull AnyVar ref, @NotNull Style color) {
    return Doc.linkDef(Doc.styled(color, ref.name()), linkIdOf(ref));
  }

  public static @NotNull Doc linkRef(@NotNull AnyVar ref, @NotNull Style color) {
    return Doc.linkRef(Doc.styled(color, ref.name()), linkIdOf(ref));
  }

  public static @NotNull Link linkIdOf(@NotNull AnyVar ref) {
    if (ref instanceof DefVar<?, ?> defVar)
      return Link.loc(QualifiedID.join(defVar.qualifiedName()));
    return Link.loc(ref.hashCode());
  }

  public static @NotNull Doc linkLit(int literal, @NotNull AnyVar ref, @NotNull Style color) {
    return Doc.linkRef(Doc.styled(color, Doc.plain(String.valueOf(literal))), linkIdOf(ref));
  }

  public static @NotNull Doc linkListLit(Doc display, @NotNull AnyVar ref, @NotNull Style color) {
    return Doc.linkDef(Doc.styled(color, display), linkIdOf(ref));
  }

  public static @NotNull Doc linkDef(@NotNull AnyVar ref) {
    return Doc.linkDef(Doc.plain(ref.name()), linkIdOf(ref));
  }

  public static @NotNull Doc defVar(DefVar<?, ?> ref) {
    var style = chooseStyle(ref.concrete);
    return style != null ? linkDef(ref, style) : varDoc(ref);
  }

  public @NotNull Doc formula(@NotNull Outer outer, @NotNull Formula<Term> formula) {
    return switch (formula) {
      case Formula.Conn<Term> cnn -> {
        var here = cnn.isAnd() ? Outer.IMin : Outer.IMax;
        yield checkParen(outer, Doc.sep(
            term(here, cnn.l()),
            cnn.isAnd() ? Doc.symbol("/\\") : Doc.symbol("\\/"),
            term(here, cnn.r())),
          cnn.isAnd() ? Outer.AppHead : Outer.IMin);
      }
      case Formula.Inv<Term> inv -> checkParen(outer,
        Doc.sep(Doc.symbol("~"), term(Outer.AppSpine, inv.i())),
        Outer.AppSpine);
      case Formula.Lit<Term>(var one) -> Doc.plain(one ? "1" : "0");
    };
  }

  public static <T extends Restr.TermLike<T> & AyaDocile> @NotNull Doc
  partial(@NotNull DistillerOptions options, @NotNull Partial<T> partial, boolean showEmpty, String lbr, String rbr) {
    return switch (partial) {
      case Partial.Const<T> sad -> Doc.sep(Doc.symbol(lbr), sad.u().toDoc(options), Doc.symbol(rbr));
      case Partial.Split<T> hap when!showEmpty && hap.clauses().isEmpty() -> Doc.empty();
      case Partial.Split<T> hap -> Doc.sep(Doc.symbol(lbr),
        Doc.join(Doc.spaced(Doc.symbol("|")), hap.clauses().map(s -> side(options, s))),
        Doc.symbol(rbr));
    };
  }

  public static <T extends Restr.TermLike<T> & AyaDocile> @NotNull Doc
  restr(@NotNull DistillerOptions options, @NotNull Restr<T> restr) {
    return switch (restr) {
      case Restr.Const<T>(var one) -> one ? Doc.symbol("top") : Doc.symbol("_|_");
      case Restr.Disj<T> v -> Doc.join(Doc.spaced(Doc.symbol("\\/")),
        v.orz().view().map(or -> or.ands().sizeGreaterThan(1) && v.orz().sizeGreaterThan(1)
          ? Doc.parened(cofib(options, or))
          : cofib(options, or)));
    };
  }

  public static <T extends Restr.TermLike<T> & AyaDocile> @NotNull Doc
  side(@NotNull DistillerOptions options, @NotNull Restr.Side<T> side) {
    return Doc.sep(cofib(options, side.cof()), Doc.symbol(":="), side.u().toDoc(options));
  }

  public static <T extends Restr.TermLike<T> & AyaDocile> @NotNull Doc
  cofib(@NotNull DistillerOptions options, @NotNull Restr.Conj<T> conj) {
    return Doc.join(Doc.spaced(Doc.symbol("/\\")), conj.ands().view().map(and -> Doc.sepNonEmpty(!and.isOne() ? Doc.symbol("~") : Doc.empty(), and.inst().toDoc(options))));
  }

  protected static @Nullable Style chooseStyle(Object concrete) {
    return switch (concrete) {
      case DefVar<?, ?> d -> chooseStyle(d.concrete);
      case TeleDecl.FnDecl d -> FN_CALL;
      case TeleDecl.DataDecl d -> DATA_CALL;
      case TeleDecl.DataCtor d -> CON_CALL;
      case TeleDecl.StructDecl d -> STRUCT_CALL;
      case TeleDecl.StructField d -> FIELD_CALL;
      case TeleDecl.PrimDecl d -> PRIM_CALL;
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
    IMax,
    IMin,
    AppHead,
    AppSpine,
    ProjHead,
    Lifted
  }
}
