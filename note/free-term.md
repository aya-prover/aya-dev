# 关于局部无名表示的术语和开发要点

由于这个术语无法简短说明，因此使用一个单独的文件解释。

局部无名表示法希望来自语境中的变量使用实体变量名，而只有绑定变量使用 de Bruijn 编号，比如 `a, b ⊢ fn. ?` 的问号位置，
可以使用 `^0`（表示 fn 绑定的变量）也可以使用变量名 `a` 或 `b`。在此处使用 `^1` 的话就是一个「悬挂」的 de Bruijn 编号。
变量名称为自由变量 free var，编号一般则对应地叫绑定变量 bound var，虽然实际上这些更应该叫 index。
这样和传统 lambda calculus 术语就保持一致了。

悬挂的编号在代码实现中是不可避免的，比如在检查表层语法 `⊢ fn a => a` 时，会先进入 `a ⊢ a`，然后再生成 `⊢ fn. ^0`。
在把右半边的 `a` 变成 `^0` 之后、把这个 term 装进 `fn.` 之前，`^0` 就是悬挂的。对于 lambda 表达式而言，
这个悬挂的编号转瞬即逝，但在很多更复杂的 term 构造时（比如一次操作一整个 telescope 的时候），
悬挂的编号会存在更久，因此我们需要一个术语来描述这种 term。

## De Bruijn-open Term

_De Bruijn-open term_ 是指含有悬挂编号的 term，例如函数 `const`: `fn a b => a` 的内部表示 `fn. fn. ^1` 中，把最深处的 `^1`
单独拿出来，就是一个悬挂的绑定变量，那么就认为它是悬挂的。而 `^1` 和 `fn. ^1` 就是 _De Bruijn-open term_。

在源码中，可能为 De Bruijn-open 的 term 会使用 `@Bound` 注解标记。为了简短也可以直接叫 bound term，
但这个名字和传统的 lambda calculus 术语冲突，因此仅在 Aya 开发内部讨论使用。

_Bound term_ 是通过 `bind`/`bindAt` 及其衍生 API 产生的，它通常不会被直接使用，需要放在一个 `Closure` 内部，
如 `LamTerm` 或 `DepTypeTerm`。

## De Bruijn-closed Term

_De Bruijn-closed term_ 是指它不含有任何悬挂的绑定变量，例如函数 `const`: `fn a b => a` 的内部表示 `fn. fn. ^1` 中，
`fn. fn. ^1` 整体就不含有任何悬挂的绑定变量。

在源码中，使用 `@Closed` 注解标记这类 term。为了简短，我们是直接叫 free term 的，
但这个明显也和传统的 lambda calculus 术语冲突，因此仅在 Aya 开发内部讨论使用。如果你不参与 Aya 开发，
应该尽量避免使用这个术语。

_Free term_ 是绝大多数情况下使用的 Term，通常来说，你应该尽可能要求传入的 Term 是 _free_ 的。
只有少量特定操作可以处理 _bound term_。

# 开发要点

在遍历 term 的时候，不可避免会遇到悬挂的编号，如果处处维护局部无名的 invariant，需要在每次进入 closure 时都 fresh 一个名字，
代入进去，再遍历。这很明显效率低下。因此我们仅在有正确性考量时再这样做。一些简单的遍历操作，比如 pretty print、计算 term 深度、
查找某个 free var 的使用等，都可以直接在 de Bruijn-open 的 term 上进行。

凡是涉及潜在的 term 结构变化，比如 zonk、freezeHoles、normalize 等等，由于潜在的触发 β-reduction 的可能性，
都需要标注 `@Closed`。

如果安装 kala-inspection 插件，在源码中会对这两个标记进行静态检查。
