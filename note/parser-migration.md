# Notes on how to edit Aya grammar

### Prerequisites

- Install [Grammar-Kit](https://github.com/JetBrains/Grammar-Kit) from _File -> Settings -> Plugin -> Marketplace_ in Intellij IDEA. Note: this is a must.
- [Why we switched to GrammarKit?](https://docs.google.com/document/d/1-xjqbSZcliCa-eVez-kIS6OZtlD9jo1igAMuNPvauTA/edit#)

### Edit grammar sources
- For lexer rules, go to [AyaPsiLexer.jflex](../parser/src/main/grammar/AyaPsiLexer.flex).
- For parser rules, go to [AyaPsiParser.bnf](../parser/src/main/grammar/AyaPsiParser.bnf).
- If you changed keywords in `.bnf` file, remember to update them in the `.jflex` file.

### Update generated parser and producer

- Select `.bnf` or `.jflex` files and press `Ctrl + Shift + G`.
- Check the diff of generated sources carefully:
  - Remove everything under [`src/main/gen/org.aya.intellij`](../parser/src/main/gen/org/aya/intellij).
  - Remove the `Factory` class in `AyaPsiElementTypes`.
- Add generated sources to version control. (This is a workaround since GrammarKit needs the whole IntelliJ platform to run, and we don't want to add it as a dev-dependency)
- Update the Producer accordingly. See JavaDoc on it for useful tips.

### Useful resources
- [GK tutorial](https://github.com/JetBrains/Grammar-Kit/blob/master/TUTORIAL.md)
- [GK advanced usages](https://github.com/JetBrains/Grammar-Kit/blob/master/HOWTO.md)
