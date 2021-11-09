// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.distill;

import kala.collection.SeqLike;
import kala.collection.mutable.DynamicSeq;
import kala.control.Option;
import org.aya.api.distill.AyaDocile;
import org.aya.api.distill.DistillerOptions;
import org.aya.api.ref.DefVar;
import org.aya.api.ref.LocalVar;
import org.aya.api.ref.Var;
import org.aya.api.util.Arg;
import org.aya.concrete.stmt.Decl;
import org.aya.concrete.stmt.Sample;
import org.aya.generic.ParamLike;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Style;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.function.BiFunction;

/**
 * @author ice1000
 */
public abstract class BaseDistiller {
  public static final @NotNull Style KEYWORD = Style.preset("aya:Keyword");
  public static final @NotNull Style FN_CALL = Style.preset("aya:FnCall");
  public static final @NotNull Style DATA_CALL = Style.preset("aya:DataCall");
  public static final @NotNull Style STRUCT_CALL = Style.preset("aya:StructCall");
  public static final @NotNull Style CON_CALL = Style.preset("aya:ConCall");
  public static final @NotNull Style FIELD_CALL = Style.preset("aya:FieldCall");
  public static final @NotNull Style GENERALIZED = Style.preset("aya:Generalized");

  public final @NotNull DistillerOptions options;

  protected BaseDistiller(@NotNull DistillerOptions options) {
    this.options = options;
  }

  <T extends AyaDocile> @NotNull Doc visitCalls(
    @NotNull Doc fn, @NotNull SeqLike<@NotNull Arg<@NotNull T>> args,
    @NotNull BiFunction<Outer, T, Doc> formatter, Outer outer
  ) {
    if (args.isEmpty()) return fn;
    var call = Doc.sep(
      fn, Doc.sep(args.view().flatMap(arg -> {
        if (arg.explicit())
          // Explicit argument, it is in a spine, not parenthesized.
          return Option.of(formatter.apply(Outer.AppSpine, arg.term()));
        if (options.showImplicitArgs())
          // Implicit argument, it is already in a pair of braces.
          return Option.of(Doc.braced(formatter.apply(Outer.Free, arg.term())));
        return Option.none();
      }))
    );
    // If we're in a spine, add parentheses
    return outer.ordinal() >= Outer.AppSpine.ordinal() ? Doc.parened(call) : call;
  }

  @NotNull Doc ctorDoc(@NotNull Outer outer, boolean ex, Doc ctorDoc, LocalVar ctorAs, boolean noParams) {
    boolean as = ctorAs != null;
    var withEx = ex ? ctorDoc : Doc.braced(ctorDoc);
    var withAs = !as ? withEx :
      Doc.sep(Doc.parened(withEx), Doc.plain("as"), linkDef(ctorAs));
    return !ex && !as ? withAs : outer != Outer.Free && !noParams ? Doc.parened(withAs) : withAs;
  }

  Doc visitTele(@NotNull SeqLike<? extends ParamLike<?>> telescope) {
    if (telescope.isEmpty()) return Doc.empty();
    var last = telescope.first();
    var buf = DynamicSeq.<Doc>create();
    var names = DynamicSeq.of(last.nameDoc());
    for (var param : telescope.view().drop(1)) {
      if (!Objects.equals(param.type(), last.type())) {
        buf.append(last.toDoc(Doc.sep(names), options));
        names.clear();
        last = param;
      }
      names.append(param.nameDoc());
    }
    buf.append(last.toDoc(Doc.sep(names), options));
    return Doc.sep(buf);
  }

  @NotNull Doc lambdaParam(@NotNull ParamLike<?> param) {
    return options.showLambdaTypes() ? param.toDoc(options)
      : param.explicit() ? param.nameDoc() : Doc.braced(param.nameDoc());
  }

  public static @NotNull Doc varDoc(@NotNull Var ref) {
    return Doc.linkRef(Doc.plain(ref.name()), ref.hashCode());
  }

  static @NotNull Doc coe(boolean coerce) {
    return coerce ? Doc.styled(KEYWORD, "coerce") : Doc.empty();
  }

  static @NotNull Doc primDoc(Var ref) {
    return Doc.sep(Doc.styled(KEYWORD, "prim"), linkDef(ref, FN_CALL));
  }

  public static @NotNull Doc linkDef(@NotNull Var ref, @NotNull Style color) {
    return Doc.linkDef(Doc.styled(color, ref.name()), ref.hashCode());
  }

  static @NotNull Doc linkRef(@NotNull Var ref, @NotNull Style color) {
    return Doc.linkRef(Doc.styled(color, ref.name()), ref.hashCode());
  }

  public static @NotNull Doc linkDef(@NotNull Var ref) {
    return Doc.linkDef(Doc.plain(ref.name()), ref.hashCode());
  }

  static @NotNull Doc visitDefVar(DefVar<?, ?> ref) {
    return visitDefVar(ref, ref.concrete);
  }

  private static @NotNull Doc visitDefVar(DefVar<?, ?> ref, Object concrete) {
    return switch (concrete) {
      case Decl.FnDecl d -> linkRef(ref, FN_CALL);
      case Decl.DataDecl d -> linkRef(ref, DATA_CALL);
      case Decl.DataCtor d -> linkRef(ref, CON_CALL);
      case Decl.StructDecl d -> linkRef(ref, STRUCT_CALL);
      case Decl.StructField d -> linkRef(ref, FIELD_CALL);
      case Decl.PrimDecl d -> linkRef(ref, FN_CALL);
      case Sample sample -> visitDefVar(ref, sample.delegate());
      case null, default -> varDoc(ref);
    };
  }

  /**
   * What's outside?
   *
   * <ul>
   *   <li>Top-level expression may not need parentheses, stone free!</li>
   *   <li>It might be an application! Stay in parentheses!</li>
   *   <li>It might be a binary operator! Applications within are fine.</li>
   * </ul>
   */
  public enum Outer {
    Free,
    BinOp,
    AppHead,
    AppSpine,
    ProjHead,
  }
}
