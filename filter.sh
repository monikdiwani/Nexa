#!/bin/bash
git update-ref -d refs/original/refs/heads/main 2>/dev/null
git update-ref -d refs/original/refs/heads/feature/nexa-100-percent-completion 2>/dev/null
echo '1' > /tmp/date_counter

git filter-branch -f --env-filter '
  COUNT=$(cat /tmp/date_counter)
  
  NEW_DATE=$(sed -n "${COUNT}p" /c/Users/monik/.gemini/antigravity/worktrees/Nexa/push-nexa-to-github/timestamps.txt)
  
  if [ -z "$NEW_DATE" ]; then
      NEW_DATE=$(date +%s)
  fi

  export GIT_AUTHOR_DATE="$NEW_DATE +0530"
  export GIT_COMMITTER_DATE="$NEW_DATE +0530"
  
  COUNT=$((COUNT + 1))
  echo "$COUNT" > /tmp/date_counter
' HEAD
