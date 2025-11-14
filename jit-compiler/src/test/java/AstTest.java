// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.

import org.aya.compiler.morphism.JavaUtil;
import org.aya.compiler.morphism.ir.IrDecl;
import org.aya.compiler.morphism.ir.IrExpr;
import org.aya.compiler.morphism.ir.IrStmt;
import org.aya.compiler.morphism.ir.BlockSimplifier;
import org.aya.compiler.serializers.FnSerializer;
import org.aya.compiler.serializers.ModuleSerializer;
import org.aya.syntax.core.def.FnDef;
import org.aya.syntax.core.term.LetTerm;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class AstTest {
  @Test public void letElim() {
    var result = CompileTest.tyck("""
      open inductive Nat | zero | suc Nat
      def infix + (a b : Nat) : Nat
      | 0, b => b
      | suc a, b => suc (a + b)
      
      def test (n : Nat) : Nat =>
        let m := n + n
        in m + (m + 0)
      """);
    var ser = new ModuleSerializer(result.info().shapeFactory());
    var anfBuilder = ser.serializeToANF(CompileTest.computeModuleResult(result));
    var anf = anfBuilder.build();
    var test = anf.members().view().filterIsInstance(IrDecl.Clazz.class)
      .find(cl -> cl.className().displayName().endsWith("test"))
      .get();
    var invokes = test.members().view()
      .filterIsInstance(IrDecl.Method.class)
      .filter(it -> it.signature().name().equals("invoke"))
      .toSeq();
    var cdLet = JavaUtil.fromClass(LetTerm.class);
    // sanity check for the equality test
    assertEquals(cdLet, JavaUtil.fromClass(LetTerm.class));
    for (var method : invokes) {
      for (var stmt : method.body()) {
        if (stmt instanceof IrStmt.SetVariable(_, IrExpr.New(var con, _))) {
          assertNotEquals(con.owner(), cdLet);
        }
      }
    }
  }

  @Test public void prettyPrint() {
    var result = CompileTest.tyck("""
      open inductive Nat | zero | suc Nat
      def infix + (a b : Nat) : Nat
      | 0, b => b
      | suc a, b => suc (a + b)
      """);
    var compiler = new FnSerializer(result.info().shapeFactory(), new ModuleSerializer.MatchyRecorder());
    var pretty = compiler.buildInvokeForPrettyPrint(result.defs().filterIsInstance(FnDef.class)
      .find(it -> "+".equals(it.ref().name())).get());
    pretty = (IrDecl.Method) BlockSimplifier.optimize(pretty);
    System.out.println(pretty.toDoc().commonRender());
  }
}
