// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.ide.action;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableLinkedHashMap;
import kala.collection.mutable.MutableMap;
import kala.value.LazyValue;
import org.aya.generic.BindingInfo;
import org.aya.intellij.GenericNode;
import org.aya.parser.AyaPsiParser;
import org.aya.syntax.concrete.stmt.StmtVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static org.aya.parser.AyaPsiElementTypes.*;

public class ContextWalker2 {
  public enum Location {
    Modifier,   // only modifiers
    Bind,       // no completion
    Expr,       // almost everything
    Pattern,    // only constructors
    Elim,       // only binds
    Unknown     // no completion
  }

  /// Collect all siblings before [#node]
  public static @NotNull SeqView<GenericNode<?>> backward(@NotNull GenericNode<?> node) {
    if (node instanceof NodeWalker.EmptyNode enode) {
      return backward(enode.host()).appended(enode.host());
    }

    var parent = node.parent();
    if (parent == null) return SeqView.empty();

    return SeqView.narrow(parent.childrenView().takeWhile(n -> !n.equals(node)));
  }

  /// Return the parent node of [#node],
  /// this function will return [org.aya.ide.action.NodeWalker.EmptyNode] if [#node] is [org.aya.ide.action.NodeWalker.EmptyNode]
  /// and the host of [#node] is the last node of its parent (in other word, [#node] is also at the end of its parent).
  public static @Nullable GenericNode<?> refocusParent(@NotNull GenericNode<?> node) {
    var parent = node.parent();
    if (parent == null) return null;
    if (!(node instanceof NodeWalker.EmptyNode enode)) return parent;
    if (parent.lastChild().equals(enode.host())
      && !RIGHT_OPEN_BINDING_INTRODUCER.contains(parent.elementType()))
      return new NodeWalker.EmptyNode(parent);
    return parent;
  }

  private static @Nullable Completion.Item.Local typeOf(@Nullable BindingInfo info) {
    if (info == null) return null;
    var type = new StmtVisitor.Type(info.typeExpr(), LazyValue.of(info.theCore()));
    return new Completion.Item.Local(info.var(), type);
  }

  private @NotNull ImmutableSeq<Completion.Item.Local> collectBinding(@NotNull GenericNode<?> node) {
    System.out.println(node);     // debug

    var type = node.elementType();

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
        .mapNotNull(ContextWalker2::typeOf).toSeq();
    }

    if (type == LET_BIND_BLOCK) {
      node.childrenOfType(LET_BIND)
        .forEach(letBind -> {
          var tele = letBind.childrenOfType(LAMBDA_TELE);
          var result = letBind.peekChild(TYPE);
        });
    }

    if (type == DO_BLOCK_CONTENT) {
      // FIXME: not yet tested
      var binding = node.peekChild(DO_BINDING);
      if (binding != null) {
      }
    }

    return ImmutableSeq.empty();
  }

  public static final @NotNull TokenSet EXPR = AyaPsiParser.EXTENDS_SETS_[4];
  public static final @NotNull TokenSet DECL = TokenSet.create(DATA_DECL, FN_DECL, PRIM_DECL, CLASS_DECL);
  public static final @NotNull TokenSet RIGHT_OPEN_BINDING_INTRODUCER = TokenSet.create(
    DO_BINDING,
    LET_BIND
  );

  private final @NotNull CompletionPartition fnDeclPartition = new CompletionPartition(
    ImmutableSeq.of(KW_DEF),
    ImmutableSeq.of(Location.Modifier, Location.Expr),
    TELE
  );

  private final @NotNull CompletionPartition dataDeclPartition = new CompletionPartition(
    ImmutableSeq.of(KW_DATA),
    ImmutableSeq.of(Location.Modifier, Location.Expr),
    TELE
  );

  private final @NotNull CompletionPartition dataConPartition = new CompletionPartition(
    ImmutableSeq.of(DECL_NAME_OR_INFIX),      // unlike others, we use a node as pin b
    ImmutableSeq.of(Location.Unknown, Location.Expr),
    TELE
  );

  private final @NotNull CompletionPartition elimPartition = new CompletionPartition(
    ImmutableSeq.of(KW_ELIM),
    ImmutableSeq.of(null, Location.Elim),
    null
  );

  private final @NotNull CompletionPartition lambda0Partition = new CompletionPartition(
    ImmutableSeq.of(IMPLIES),
    ImmutableSeq.of(Location.Bind, Location.Expr),
    TELE_BINDER_UNTYPED
  );

  private final @NotNull CompletionPartition lambda1Partition = new CompletionPartition(
    ImmutableSeq.of(IMPLIES),
    ImmutableSeq.of(Location.Pattern, Location.Expr),
    PATTERNS
  );

  private final @NotNull CompletionPartition lambda2Partition = new CompletionPartition(
    ImmutableSeq.of(IMPLIES),
    ImmutableSeq.of(Location.Pattern, Location.Expr),
    UNIT_PATTERN
  );

  private final @NotNull CompletionPartition piPartition = new CompletionPartition(
    ImmutableSeq.of(KW_PI, TO),
    ImmutableSeq.of(null, Location.Expr, Location.Expr),
    TELE
  );

  private final @NotNull CompletionPartition forallPartition = new CompletionPartition(
    ImmutableSeq.of(KW_FORALL, TO),
    ImmutableSeq.of(null, Location.Bind, Location.Expr),
    LAMBDA_TELE
  );

  private final @NotNull CompletionPartition letPartition = new CompletionPartition(
    ImmutableSeq.of(KW_LET, KW_IN),
    ImmutableSeq.of(null, Location.Unknown, Location.Expr),
    LET_BIND_BLOCK
  );

  private final @NotNull CompletionPartition letBindPartition = new CompletionPartition(
    ImmutableSeq.of(DEFINE_AS),
    ImmutableSeq.of(Location.Bind, Location.Expr),
    LAMBDA_TELE
  );

  private final @NotNull CompletionPartition clausePartition = new CompletionPartition(
    ImmutableSeq.of(IMPLIES),
    ImmutableSeq.of(Location.Pattern, Location.Expr),
    PATTERNS
  );

  private final @NotNull CompletionPartition typePartition = new CompletionPartition(
    ImmutableSeq.of(COLON),
    ImmutableSeq.of(null, Location.Expr),
    null
  );

  private final @NotNull CompletionPartition doBindPartition = new CompletionPartition(
    ImmutableSeq.of(LARROW),
    ImmutableSeq.of(Location.Bind, Location.Expr),
    null
  );

  private final @NotNull CompletionPartition arrayCompBlockPartition = new CompletionPartition(
    ImmutableSeq.of(BAR),
    ImmutableSeq.of(Location.Expr, Location.Unknown),
    DO_BINDING    // FIXME: doesn't work, use comma sep
  );

  private final @NotNull MutableMap<String, Completion.Item.Local> localContext = MutableLinkedHashMap.of();
  private final @NotNull MutableMap<GenericNode<?>, BindingInfo> bindingInfos;
  private @Nullable Location location = null;

  public ContextWalker2(@NotNull MutableMap<GenericNode<?>, BindingInfo> bindingInfos) {
    this.bindingInfos = bindingInfos;
  }

  public @Nullable Location location() {
    return this.location;
  }

  private void setLocation(@NotNull Location location) {
    if (this.location == null) this.location = location;
  }

  public void visit(@Nullable GenericNode<?> node) {
    if (node == null) return;

    var parent = node.parent();
    if (parent != null) {
      var type = parent.elementType();
      if (EXPR.contains(type)) {
        visitExpr(node);
      } else if (DECL.contains(type)) {
        visitDecl(node);
      } else {
        visitMisc(node);
      }

      // in case [node] is EmptyNode, and the its host is still the last child of [parent]
      visit(refocusParent(node));
    }
  }

  private void collectAndPutBinding(@NotNull GenericNode<?> node) {
    collectBinding(node).forEach(l -> localContext.putIfAbsent(l.name(), l));
  }

  /// @param node which [GenericNode#parent()] is [#DECL], can be [org.aya.ide.action.NodeWalker.EmptyNode]
  public void visitDecl(@NotNull GenericNode<?> node) {
    var parent = node.parent();
    assert parent != null;

    var type = parent.elementType();
    if (type == FN_DECL) fnDeclPartition.accept(node);
    else if (type == DATA_DECL) dataDeclPartition.accept(node);
  }

  /// @param node which [GenericNode#parent()] is [#EXPR], can be [org.aya.ide.action.NodeWalker.EmptyNode]
  public void visitExpr(@NotNull GenericNode<?> node) {
    var parent = node.parent();
    assert parent != null;

    var type = parent.elementType();
    if (type == LAMBDA_0_EXPR) lambda0Partition.accept(node);
    else if (type == LAMBDA_1_EXPR) lambda1Partition.accept(node);
    else if (type == LAMBDA_2_EXPR) lambda2Partition.accept(node);
    else if (type == FORALL_EXPR) forallPartition.accept(node);
    else if (type == PI_EXPR) piPartition.accept(node);
    else if (type == LET_EXPR) letPartition.accept(node);
    else if (type == NEW_EXPR) ;    // TODO
  }

  public void visitMisc(@NotNull GenericNode<?> node) {
    var parent = node.parent();
    assert parent != null;

    var type = parent.elementType();

    // special case, `arrowExpr := expr TO expr`, unlike `type := COLON expr`, both sides of `arrowExpr` are expr
    if (type == ARROW_EXPR) {
      setLocation(Location.Expr);
      return;
    }

    if (type == LET_BIND) letBindPartition.accept(node);
    else if (type == CLAUSE) clausePartition.accept(node);
    else if (type == TYPE) typePartition.accept(node);
    else if (type == DO_BINDING) doBindPartition.accept(node);
    else if (type == ARRAY_COMP_BLOCK) {
      var prevSiblings = arrayCompBlockPartition.accept(node);
      // special case, as all bindings it introduces are after the usage (generator).
      // the only case is generator,
      // as `arrayCompBlockPartition.accept` can do the job if [node] is (in) do bind,
      // and it do nothing if [node] is (in) generator
      if (!prevSiblings.anyMatch(it -> it.elementType() == BAR)) {
        parent.childrenOfType(DO_BINDING).forEach(this::collectAndPutBinding);
      }
    } else if (type == ELIMS) {
      elimPartition.accept(node);
    } else if (type == COMMA_SEP) {
      var pparent = parent.parent();
      if (pparent != null) {
        var ptype = pparent.elementType();
        if (ptype == DO_EXPR) {
          setLocation(Location.Expr);
          backward(node)
            .filter(it -> it.elementType() == DO_BLOCK_CONTENT)
            .forEach(this::collectAndPutBinding);
        }
      }
    } else if (type == DATA_CON) {
      dataConPartition.accept(node);
    }
  }

  public class CompletionPartition {
    public final @NotNull ImmutableSeq<IElementType> pins;
    public final @NotNull ImmutableSeq<ContextWalker2.@Nullable Location> locations;
    public final @Nullable IElementType paramType;

    public CompletionPartition(
      @NotNull ImmutableSeq<IElementType> pins,
      @NotNull ImmutableSeq<Location> locations,
      @Nullable IElementType paramType
    ) {
      this.pins = pins;
      this.locations = locations;
      this.paramType = paramType;

      assert pins.isNotEmpty();
      assert locations.size() == pins.size() + 1;
    }

    public @NotNull ImmutableSeq<GenericNode<?>> accept(@NotNull GenericNode<?> node) {
      var prevSiblings = backward(node)
        .toSeq();

      // part locating
      int part = 0;   // also the index of the next delimiter
      for (var sibling : prevSiblings) {
        if (part == pins.size()) break;
        if (sibling.elementType() == pins.get(part)) {
          part++;
        }
      }

      // set location
      var maybeLocation = locations.get(part);
      if (maybeLocation != null) setLocation(maybeLocation);

      // collect bindings
      if (paramType != null) {
        prevSiblings.filter(it -> it.elementType() == paramType)
          .forEach(ContextWalker2.this::collectAndPutBinding);
      }

      return prevSiblings;
    }
  }
}
