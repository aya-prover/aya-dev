// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.stmt;

import kala.collection.immutable.ImmutableSeq;
import org.aya.concrete.Expr;
import org.aya.core.def.Def;
import org.aya.ref.DefVar;
import org.aya.resolve.context.Context;
import org.aya.tyck.order.TyckUnit;
import org.aya.util.binop.OpDecl;
import org.aya.util.error.SourceNode;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Generic concrete definitions, corresponding to {@link org.aya.core.def.GenericDef}.
 * Concrete definitions can be varied in the following ways:
 * <ul>
 *   <li>Whether it has a telescope, see {@link Telescopic}</li>
 *   <li>Whether it can be defined at top-level, see {@link TopLevel}</li>
 *   <li>Whether it has a result type, see {@link Resulted}</li>
 * </ul>
 * We say these are properties of a concrete definition and should be implemented selectively.
 *
 * <p>
 * There are some commonalities between concrete definitions: they all have
 * source positions, names, operator info and statement accessibility. These common
 * parts are extracted into {@link CommonDecl} for all concrete definitions,
 * {@link TeleDecl} for all top-level telescopic concrete definitions and
 * {@link ClassDecl} for all top-level class-able concrete definitions.
 *
 * <p>
 * For visitors that may need to visit a certain property, it is recommended to
 * provide an extra proof object that implements the property. For example, visiting
 * a {@link Telescopic} concrete definition requires a proof object of type {@link Telescopic}
 * in addition to the original definition object of type {@link Decl}.
 * Checkout {@link org.aya.concrete.visitor.StmtOps#visitTelescopic(Decl, Telescopic, Object)}
 * for detailed code example.
 *
 * @author kiva, zaoqi
 * @see CommonDecl
 * @see TeleDecl
 * @see ClassDecl
 */
public sealed interface Decl extends OpDecl, SourceNode, TyckUnit, Stmt permits CommonDecl {
  /** @see org.aya.generic.Modifier */
  enum Personality {
    /** Denotes that the definition is a normal definition (default behavior) */
    NORMAL,
    /** Denotes that the definition is an example (same as normal, but in separated context) */
    EXAMPLE,
    /** Denotes that the definition is a counterexample (errors expected, in separated context) */
    COUNTEREXAMPLE,
  }

  @Override @NotNull Accessibility accessibility();
  @Contract(pure = true) @NotNull DefVar<?, ?> ref();
  @NotNull BindBlock bindBlock();
  @Override @Nullable OpInfo opInfo();
  @NotNull SourcePos entireSourcePos();

  @Override default boolean needTyck(@NotNull ImmutableSeq<String> currentMod) {
    return ref().isInModule(currentMod) && ref().core == null;
  }

  /**
   * Denotes that the definition is telescopic
   *
   * @author kiva
   */
  sealed interface Telescopic permits TeleDecl, TeleDecl.DataCtor, ClassDecl.StructDecl.StructField {
    @NotNull ImmutableSeq<Expr.Param> telescope();
    void setTelescope(@NotNull ImmutableSeq<Expr.Param> telescope);
    @Nullable Def.Signature signature();
    void setSignature(@Nullable Def.Signature signature);
  }

  /**
   * Denotes that the definition can be defined at top-level
   *
   * @author kiva
   */
  sealed interface TopLevel permits ClassDecl, TeleDecl {
    @NotNull Personality personality();
    @Nullable Context getCtx();
    void setCtx(@NotNull Context ctx);
  }

  /**
   * Denotes that the definition has a result type
   *
   * @author kiva
   */
  sealed interface Resulted permits ClassDecl, TeleDecl, ClassDecl.StructDecl.StructField {
    @NotNull Expr result();
    void setResult(@NotNull Expr result);
  }
}
