
# 术语表

### 编写指南

+ 这些术语均为元语言中的术语，即在这些 Java 代码中出现的术语。
+ 当这个名词：
  + 有来源时，尽可能注明这些来源。
  + 在其他场合有不同的叫法时，尽可能注明这些场合和叫法。
  + 有类似的词时，尽可能注明这些区别。
  + 是编译器实现代码中的命名时，使用`代码块`标记他们。

## 表达式相关

+ 函数 -- 指函数或者（大部分情况下都是后者）Visitor
+ `Term` -- 类型正确的表达式，或者说 core language 里的表达式，可以解释执行
  + Agda 中叫做 `Internal`
  + Arend 中叫做 Core
+ `Def` -- 类型正确的全局定义，或者说包含 `Term` 的全局定义
  + Agda 中叫做 `Defn`
+ `Expr` -- 语法正确、类型尚且不知道正不正确的表达式，又叫 concrete syntax
  + Agda 中叫做 `Abstract`
  + Arend 中叫做 `Concrete`
+ `Decl` -- 语法正确、类型尚且不知道正不正确的全局定义，或者说包含 `Expr` 的全局定义
+ `Subst` -- binding 到表达式的映射，又叫替换
  + Arend 中叫 `ExprSubstitution`
  + Agda 中叫 `Substitution`
+ `Tele` -- 函数式风格的列表存储的一组 binding，后面的 binding 的类型可以引用前面的 binding 的值。
  在实例化时，需要按顺序从左到右依次实例化，每次实例化需要对后面的 binding 跑 substitution。
  + Arend 中叫 `DependentLink`
+ `Univ` -- 类型的类型
  + Arend 里面叫 `Universe`
  + Agda 里面叫 `Sort`
+ `Sort` -- `Univ` 的属性，包括 universe level 和 homotopy truncation level
  + 命名来自 Arend
  + Agda 里面就叫 `Level` 但是他们没有 homotopy truncation level
+ `Distill` -- pretty print 或格式化代码（format/reformat code）（参见：https://github.com/jonsterling/dreamtt/blob/main/frontend/Distiller.ml ）
+ `Stmt` -- 语句（statement），文件由一组 `Stmt` 组成

### 各种 Visitor

+ Fixpoint -- 输入和返回类型相同的函数，包含 `Substituter`, `Stripper`, `Normalizer` 等
+ Consumer -- 不直接返回值（而是修改自身状态作为输出）的函数，比如 `UsageCounter`

## 类型检查时用到的状态

+ `Reporter` -- 用来收集错误信息的接口

## 类型检查的步骤

+ Tyck -- 类型为 `Expr -> Term -> TCM Term` 的函数
+ Resolve -- 解析 reference 的步骤
+ Terck -- 停机性检查，包含结构归纳和 productivity 检查
  + 一般叫 termination check 和 productivity check,
    也有奇葩喜欢叫 structural induction 和 guardedness
+ Normalize -- 表达式化简求值
  + Agda, Arend 里面都是这么叫的
+ Unfold -- 展开一个函数调用表达式
  + Agda, Coq, Arend 里面都是这么叫的
+ DefEq -- 其实就是 conversion check 或者 unification
  + 很多地方叫 definitional equality 或者 judgmental equality

## 其他
+ LSP -- Language Server Protocol
