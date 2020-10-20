# 术语表

+ 函数 -- 指函数或者 Visitor
+ Term -- 类型正确的表达式，或者说 core language 里的表达式
+ Expr -- 语法正确、类型尚且不知道正不正确的表达式，又叫 concrete syntax
+ Fixpoint -- 输入和返回类型相同的函数
+ Consumer -- 不直接返回值（而是修改自身状态作为输出）的函数
+ TCM -- 类型检查的上下文，包括作用域、错误信息等
+ Tyck -- 类型为 `Expr -> Term -> TCM Term` 的函数
+ Terck -- 停机性检查，包含结构归纳和 productivity 检查
