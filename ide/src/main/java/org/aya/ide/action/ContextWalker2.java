// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.ide.action;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableLinkedHashMap;
import kala.collection.mutable.MutableMap;
import org.aya.intellij.GenericNode;
import org.aya.parser.AyaPsiParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static org.aya.parser.AyaPsiElementTypes.*;

public class ContextWalker2 {
  public enum Location {
    Modifier,
    Bind,       // fn _a => ...
    Expr,
    Pattern,
    Unknown
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
          .forEach(ContextWalker2.this::collectBinding);
      }

      return prevSiblings;
    }
  }

  public static final @NotNull TokenSet EXPR = AyaPsiParser.EXTENDS_SETS_[4];
  public static final @NotNull TokenSet DECL = TokenSet.create(DATA_DECL, FN_DECL, PRIM_DECL, CLASS_DECL);

  public static final @NotNull TokenSet BIND_INTRODUCER = TokenSet.create(
    TELE,
    LAMBDA_TELE_BINDER,
    TELE_BINDER_UNTYPED,
    LET_BIND_BLOCK,
    PATTERNS,
    UNIT_PATTERN
  );

  private final @NotNull MutableMap<String, Completion.Item.Local> localContext = MutableLinkedHashMap.of();
  private @Nullable Location location = null;

  /// Collect all siblings before [#node]
  public static @NotNull SeqView<GenericNode<?>> backward(@NotNull GenericNode<?> node) {
    if (node instanceof NodeWalker.EmptyNode enode) {
      return backward(enode.host()).appended(enode.host());
    }

    var parent = node.parent();
    if (parent == null) return SeqView.empty();

    return SeqView.narrow(parent.childrenView().takeWhile(n -> !n.equals(node)));
  }

  public @Nullable Location location() {
    return this.location;
  }

  private void setLocation(@NotNull Location location) {
    if (this.location == null) this.location = location;
  }

  private void setLocationExpr() {
    setLocation(Location.Expr);
  }

  private void setLocationBind() {
    setLocation(Location.Bind);
  }

  private void setLocationPattern() {
    setLocation(Location.Pattern);
  }

  private void setLocationUnknown() {
    setLocation(Location.Unknown);
  }

  private final @NotNull CompletionPartition fnDeclPartition = new CompletionPartition(
    ImmutableSeq.of(KW_DEF),
    ImmutableSeq.of(Location.Modifier, Location.Expr),
    TELE
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
    ImmutableSeq.of(Location.Expr, null),
    DO_BINDING
  );

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

      visit(parent);
    }
  }

  private void collectWeakId(@NotNull GenericNode<?> node) {
    var name = node.tokenText().toString();
    // TODo: retrieve type
  }

  private void collectBinding(@NotNull GenericNode<?> node) {
    System.out.println(node);     // debug

    var type = node.elementType();

    if (type == TELE_BINDER_UNTYPED) {
      node.childrenOfType(TELE_PARAM_NAME)
        .map(t -> t.child(WEAK_ID))
        .forEach(this::collectWeakId);
    } else if (type == LET_BIND_BLOCK) {
      node.childrenOfType(LET_BIND)
        .map(t -> t.child(WEAK_ID))
        .forEach(this::collectWeakId);
    } else if (type == DO_BLOCK_CONTENT) {
      // FIXME: not yet tested
      var binding = node.peekChild(DO_BINDING);
      if (binding != null) {
        collectWeakId(binding.child(WEAK_ID));
      }
    }
  }

  /// @param bindIntroducers nodes that is [#BIND_INTRODUCER]
  private void collectBindings(@NotNull ImmutableSeq<GenericNode<?>> bindIntroducers) {
    bindIntroducers.forEach(this::collectBinding);
  }

  /// @param node which [GenericNode#parent()] is [#DECL], can be [org.aya.ide.action.NodeWalker.EmptyNode]
  public void visitDecl(@NotNull GenericNode<?> node) {
    var parent = node.parent();
    assert parent != null;

    var type = parent.elementType();
    if (type == FN_DECL) fnDeclPartition.accept(node);
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
  }

  public void visitMisc(@NotNull GenericNode<?> node) {
    var parent = node.parent();
    assert parent != null;

    var type = parent.elementType();

    // special case, `arrowExpr := expr TO expr`, unlike `type := COLON expr`, both sides of `arrowExpr` are expr
    if (type == ARROW_EXPR) {
      setLocationExpr();
      return;
    }

    if (type == LET_BIND) letBindPartition.accept(node);
    else if (type == CLAUSE) clausePartition.accept(node);
    else if (type == TYPE) typePartition.accept(node);
    else if (type == NEW_EXPR) ;     // TODO
    else if (type == DO_BINDING) doBindPartition.accept(node);
    else if (type == ARRAY_COMP_BLOCK) {
      var prevSiblings = arrayCompBlockPartition.accept(node);
      // special case, as all bindings it introduces are after the usage (generator).
      // the only case is generator,
      // as `arrayCompBlockPartition.accept` can do the job if [node] is (in) do bind,
      // and it do nothing if [node] is (in) generator
      if (!prevSiblings.anyMatch(it -> it.elementType() == BAR)) {
        parent.childrenOfType(DO_BINDING).forEach(this::collectBinding);
      }
    } else if (type == COMMA_SEP) {
      var pparent = parent.parent();
      if (pparent != null) {
        var ptype = pparent.elementType();
        if (ptype == DO_EXPR) {
          setLocationExpr();
          backward(node)
            .filter(it -> it.elementType() == DO_BLOCK_CONTENT)
            .forEach(this::collectBinding);
        }
      }
    }
  }
}
