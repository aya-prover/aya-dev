// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.ide.action.completion;

import kala.collection.immutable.ImmutableMap;
import kala.collection.immutable.ImmutableSeq;
import kala.value.LazyValue;
import org.aya.generic.BindingInfo;
import org.aya.ide.action.Completion;
import org.aya.intellij.GenericNode;
import org.aya.syntax.concrete.Expr;
import org.aya.syntax.concrete.stmt.StmtVisitor;
import org.aya.util.Arg;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

import static org.aya.parser.AyaPsiElementTypes.*;

public record BindingCollector(@NotNull ImmutableMap<GenericNode<?>, BindingInfo> bindingInfos) {
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

    // TODO: maybe we can return ImmutableSeq<Param> and make Param stores Completion.Item.Local
    var licit = node.child(LICIT);
    var explicit = licit.firstChild().is(LPAREN);
    return new Arg<>(collectBinding(licit.child(LAMBDA_TELE_BINDER)), explicit);
  }

  // TODO: maybe SeqView
  public @NotNull ImmutableSeq<Completion.Item.Local> collectBinding(@NotNull GenericNode<?> node) {
    System.out.println(node);     // debug

    var type = node.elementType();

    // region tele

    if (type == TELE) {
      var ty = typeOf(bindingInfos.getOrNull(node));
      if (ty == null) {
        var binder = node.child(LICIT).child(TELE_BINDER);
        var typed = binder.peekChild(TELE_BINDER_TYPED);
        if (typed != null) {
          return collectBinding(typed);
        }
        var anonymous = binder.child(TELE_BINDER_ANONYMOUS);
        ty = typeOf(bindingInfos.getOrNull(anonymous));
        if (ty != null) return ImmutableSeq.of(ty);
        else return ImmutableSeq.empty();
      } else {
        return ImmutableSeq.of(ty);
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
        .mapNotNull(letBind -> {
          var tele = letBind.childrenOfType(LAMBDA_TELE)
            .flatMap(t -> {
              var lt = lambdaTele(t);
              var explicit = lt.explicit();
              var params = lt.term();
              return params.view()
                .map(param -> new Completion.Param(param.name(), param.type().headless(), explicit));
            }).toSeq();

          var result = typeOf(bindingInfos.get(letBind));
          assert result != null;

          if (tele.anyMatch(Objects::isNull)) tele = ImmutableSeq.empty();
          return new Completion.Item.Local(result.var(), new Completion.Telescope(tele, result.type().headless()));
        })
        .toSeq();
    }

    if (type == DO_BLOCK_CONTENT) {
      // FIXME: not yet tested
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
