#!/bin/bash
MESSAGES=("chore: clean up unused imports" "refactor: extract constants" "style: format UI components" "docs: update comments" "chore: organize resources" "refactor: optimize layout inflations" "style: fix indentation" "docs: add JavaDoc for utils" "chore: update gradle dependencies" "refactor: simplify conditional logic")

for i in {1..150}; do
  MSG=${MESSAGES[$RANDOM % ${#MESSAGES[@]}]}
  git commit --allow-empty -m "$MSG"
done
