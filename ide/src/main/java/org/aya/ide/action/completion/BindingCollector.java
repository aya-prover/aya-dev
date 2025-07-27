// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.ide.action.completion;

import kala.collection.immutable.ImmutableMap;
import kala.collection.immutable.ImmutableSeq;
import kala.value.LazyValue;
import org.aya.ide.action.Completion;
import org.aya.intellij.GenericNode;
import org.aya.syntax.concrete.Expr;
import org.aya.syntax.concrete.stmt.StmtVisitor;
import org.aya.syntax.core.term.ErrorTerm;
import org.aya.syntax.ref.LocalVar;
import org.aya.util.Arg;
import org.aya.util.position.SourcePos;
import org.aya.util.position.WithPos;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static org.aya.parser.AyaPsiElementTypes.*;

public record BindingCollector(@NotNull ImmutableMap<GenericNode<?>, BindingInfo> bindingInfos) {
  private static final @NotNull Expr HOLE = new Expr.Hole(false, null);

  public static boolean isAvailable(@NotNull Completion.Item.Local local) {
    return !(local.var() instanceof LocalVar lvar)   // anything beside LocalVar, or
      || (!lvar.isGenerated()                        // LocalVar, but not generated, and
      && local.type().result().lazyType().get() != ErrorTerm.TYPE_OF_CON_PATTERN);    // not a constructor
  }

  @Contract("!null -> !null")
  private static @Nullable Completion.Item.Local typeOf(@Nullable BindingInfo info) {
    if (info == null) return null;
    var userType = info.typeExpr();
    if (userType instanceof Expr.Hole) userType = null;
    var type = new StmtVisitor.Type(userType, LazyValue.of(info.theCore()));
    return new Completion.Item.Local(info.var(), type);
  }

  private @NotNull Arg<ImmutableSeq<Completion.Item.Local>> lambdaTele(@NotNull GenericNode<?> node) {
    var untyped = node.peekChild(TELE_PARAM_NAME);
    if (untyped != null) {
      return Arg.ofExplicitly(ImmutableSeq.of(typeOf(bindingInfos.get(untyped))));
    }

    var licit = node.child(LICIT);
    var explicit = licit.firstChild().is(LPAREN);
    return new Arg<>(collectBinding(licit.child(LAMBDA_TELE_BINDER)), explicit);
  }

  private @NotNull Completion.Item.Local letBind(@NotNull GenericNode<?> node) {
    var tele = node.childrenOfType(LAMBDA_TELE)
      .flatMap(t -> {
        var lt = lambdaTele(t);
        var explicit = lt.explicit();
        var params = lt.term();
        return params.view()
          .map(param -> {
            var ref = param.var() instanceof LocalVar lvar ? lvar : new LocalVar(param.name());
            var type = param.type().headless().userType();
            if (type == null) type = HOLE;
            return new Expr.Param(SourcePos.NONE, ref, new WithPos<>(SourcePos.NONE, type), explicit);
          });
      }).toSeq();

    var info = bindingInfos.get(node);
    var typeExpr = info.typeExpr();
    var result = typeOf(bindingInfos.get(node));
    // OMG, this is so stupid
    var piExpr = Expr.buildPi(SourcePos.NONE, tele.view(), new WithPos<>(SourcePos.NONE, typeExpr != null
      ? typeExpr
      : new Expr.Hole(false, null)));

    return new Completion.Item.Local(result.var(), new StmtVisitor.Type(piExpr.data(), result.result().lazyType()));
  }

  // TODO: maybe SeqView
  /// Collect all bindings and their information that [#node] introduce,
  /// we consider these nodes can introduce bindings:
  /// * tele
  /// * lambdaTele
  /// * lambdaTeleBinder
  /// * teleBinderTyped
  /// * teleBinderUntyped
  /// * letBindBlock/letBind
  /// * doBlockContent/doBind
  /// * teleParamName
  public @NotNull ImmutableSeq<Completion.Item.Local> collectBinding(@NotNull GenericNode<?> node) {
    var type = node.elementType();

    // region tele

    if (type == TELE) {
      var licit = node.peekChild(LICIT);
      if (licit != null) {
        var binder = licit.child(TELE_BINDER);
        var typed = binder.peekChild(TELE_BINDER_TYPED);
        if (typed != null) {
          return collectBinding(typed);
        }
        var anonymous = binder.child(TELE_BINDER_ANONYMOUS);
        var ty = typeOf(bindingInfos.getOrNull(anonymous));
        if (ty != null) return ImmutableSeq.of(ty);
        else return ImmutableSeq.empty();
      } else {
        return ImmutableSeq.empty();
      }
    }

    if (type == TELE_BINDER_TYPED) {
      return collectBinding(node.child(TELE_BINDER_UNTYPED));
    }

    if (type == TELE_BINDER_UNTYPED) {
      return node.childrenOfType(TELE_PARAM_NAME)
        .map(bindingInfos::getOrNull)
        .mapNotNull(BindingCollector::typeOf).toSeq();
    }

    if (type == LAMBDA_TELE) {
      return lambdaTele(node).term();
    }

    if (type == LAMBDA_TELE_BINDER) {
      var child = node.peekChild(TELE_BINDER_TYPED);
      if (child == null) child = node.child(TELE_BINDER_UNTYPED);
      return collectBinding(child);
    }

    // endregion tele

    // region let/do bind

    if (type == LET_BIND_BLOCK) {
      return node.childrenOfType(LET_BIND)
        .mapNotNull(this::letBind)
        .toSeq();
    }

    if (type == LET_BIND) {
      return ImmutableSeq.of(letBind(node));
    }

    if (type == DO_BLOCK_CONTENT) {
      // TODO
      var binding = node.peekChild(DO_BINDING);
      if (binding != null) {
      }
    }

    // endregion let/do bind

    // region pattern
    if (type == PATTERNS) {
      return node.child(COMMA_SEP)
        .childrenOfType(PATTERN)
        .flatMap(this::collectBinding)
        .toSeq();
    }

    if (type == PATTERN) {
      var asBind = typeOf(bindingInfos.getOrNull(node));    // as binding
      var bindings = node.child(UNIT_PATTERNS)
        .childrenOfType(UNIT_PATTERN)
        .flatMap(this::collectBinding);

      if (asBind != null) bindings = bindings.appended(asBind);
      return bindings.toSeq();
    }

    if (type == UNIT_PATTERN) {
      var licit = node.peekChild(LICIT);
      if (licit != null) {
        return collectBinding(licit.child(PATTERNS));
      }

      // atom patterns
      var atom = node.firstChild();

      if (atom.is(ATOM_LIST_PATTERN)) {
        return atom.childrenOfType(PATTERNS)
          .flatMap(this::collectBinding)
          .toSeq();
      }

      if (atom.is(ATOM_BIND_PATTERN)) {
        var myType = typeOf(bindingInfos.getOrNull(atom));
        if (myType == null) return ImmutableSeq.empty();
        return ImmutableSeq.of(myType);
      }
    }

    // endregion pattern

    return ImmutableSeq.empty();
  }
}
