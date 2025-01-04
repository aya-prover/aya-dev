# 模式匹配类型检查

## Pusheen

在进行 `PatternTycker` 之前，函数签名会进行 pusheen 操作，即：
将位于 result 的 **PiTerm** 的参数移动到 telescope 中。这可以视为是某种 normal form。

## PatternTycker

`PatternTycker` 负责找到用户给定的 pattern 所对应的 parameter，并且在遇到问题时报错，
如过多隐式 pattern 或过少的 pattern。

尽管我们一开始就对函数签名进行 pusheen，但对于 PatternTycker 来说，它需要知道哪些 parameter 是必须有 pattern 而哪些不必须。因此，在 pattern 

## After Pattern Typecheck (checkLhs)

如果 pattern tycker 成功，此时
