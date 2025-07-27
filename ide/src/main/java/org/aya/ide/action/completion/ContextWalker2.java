// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.ide.action.completion;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import kala.collection.immutable.ImmutableMap;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableLinkedHashMap;
import kala.collection.mutable.MutableMap;
import org.aya.ide.action.Completion;
import org.aya.intellij.GenericNode;
import org.aya.parser.AyaPsiParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static org.aya.parser.AyaPsiElementTypes.*;

public class ContextWalker2 {

  public static final @NotNull TokenSet EXPR = AyaPsiParser.EXTENDS_SETS_[4];
  public static final @NotNull TokenSet DECL = TokenSet.create(DATA_DECL, FN_DECL, PRIM_DECL, CLASS_DECL);

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
    ImmutableSeq.of(DECL_NAME_OR_INFIX),      // unlike others, we use a node as pin
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

  private final @NotNull CompletionPartition letListPartition = new CompletionPartition(
    ImmutableSeq.empty(),
    ImmutableSeq.of((Location) null),
    LET_BIND
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

  public final @NotNull MutableMap<String, Completion.Item.Local> localContext = MutableLinkedHashMap.of();
  private final @NotNull BindingCollector bindingCollector;
  private @Nullable Location location = null;

  public ContextWalker2(@NotNull ImmutableMap<GenericNode<?>, BindingInfo> bindingInfos) {
    this.bindingCollector = new BindingCollector(bindingInfos);
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
      visit(NodeWalkUtil.refocusParent(node));
    }
  }

  private void collectAndPutBinding(@NotNull GenericNode<?> node) {
    bindingCollector.collectBinding(node)
      .forEach(l -> localContext.putIfAbsent(l.name(), l));
  }

  /// @param node which [GenericNode#parent()] is [#DECL], can be [NodeWalker.EmptyNode]
  public void visitDecl(@NotNull GenericNode<?> node) {
    var parent = node.parent();
    assert parent != null;

    var type = parent.elementType();
    if (type == FN_DECL) fnDeclPartition.accept(node);
    else if (type == DATA_DECL) dataDeclPartition.accept(node);
  }

  /// @param node which [GenericNode#parent()] is [#EXPR], can be [NodeWalker.EmptyNode]
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
    else if (type == LET_BIND_BLOCK) letListPartition.accept(node);
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
          NodeWalkUtil.backward(node)
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
    public final @NotNull ImmutableSeq<@Nullable Location> locations;
    public final @Nullable IElementType paramType;

    public CompletionPartition(
      @NotNull ImmutableSeq<IElementType> pins,
      @NotNull ImmutableSeq<Location> locations,
      @Nullable IElementType paramType
    ) {
      this.pins = pins;
      this.locations = locations;
      this.paramType = paramType;

      assert locations.size() == pins.size() + 1;
    }

    public @NotNull ImmutableSeq<GenericNode<?>> accept(@NotNull GenericNode<?> node) {
      var prevSiblings = NodeWalkUtil.backward(node)
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
