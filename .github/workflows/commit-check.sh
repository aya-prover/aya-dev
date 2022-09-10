#!/usr/bin/env bash

#
# Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
# Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
#

set -eu -o pipefail

git log --oneline --decorate --pretty=format:'%h:%s' origin/main.. | xargs -0 echo | while read line; do
  IFS=':' read -r hash tag msg <<< "$line"
  if [[ "$(echo $tag | tr -d [A-za-z0-9\#-])"x != ""x ]]; then
    echo "In commit $hash, \`$tag\` is not a good commit tag"
    exit 1
  fi
  if [[ ! "$msg" =~ ( ).+ ]]; then
    echo "In commit $hash, commit message should have a space after \`:\` and not empty"
    exit 2
  fi
  echo "Commit message of $hash is good"
done

exit 0

