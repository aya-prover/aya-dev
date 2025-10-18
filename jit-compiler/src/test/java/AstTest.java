// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.

import org.aya.compiler.morphism.JavaUtil;
import org.aya.compiler.morphism.ast.AstDecl;
import org.aya.compiler.morphism.ast.AstExpr;
import org.aya.compiler.morphism.ast.AstStmt;
import org.aya.compiler.serializers.ModuleSerializer;
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
    var test = anf.members().view().filterIsInstance(AstDecl.Clazz.class)
      .find(cl -> cl.className().displayName().endsWith("test"))
      .get();
    var invokes = test.members().view()
      .filterIsInstance(AstDecl.Method.class)
      .filter(it -> it.signature().name().equals("invoke"))
      .toSeq();
    var cdLet = JavaUtil.fromClass(LetTerm.class);
    // sanity check for the equality test
    assertEquals(cdLet, JavaUtil.fromClass(LetTerm.class));
    for (var method : invokes) {
      for (var stmt : method.body()) {
        if (stmt instanceof AstStmt.SetVariable(_, AstExpr.New(var con, _))) {
          assertNotEquals(con.owner(), cdLet);
        }
      }
    }
  }
}
