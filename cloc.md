```bash
cloc  --not-match-d='src/test/java'  --exclude-dir='cypress' --strip-comments=nc --vcs=git
```
```
github.com/AlDanial/cloc v 1.96  T=0.33 s (1033.9 files/s, 134599.5 lines/s)
-------------------------------------------------------------------------------
Language                     files          blank        comment           code
-------------------------------------------------------------------------------
Java                           200           2586           7936          12317
JavaScript                       6            606            232           1693
```