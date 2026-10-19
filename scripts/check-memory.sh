#!/bin/bash
# scripts/check-memory.sh — Проверка лимитов памяти, согласованности версий и ссылок
set -u

RED='\033[0;31m'
YELLOW='\033[0;33m'
GREEN='\033[0;32m'
NC='\033[0m'
EXIT_CODE=0

echo "🔍 Проверка здоровья Memory Bank Android-проекта"
echo "==============================================="

# 1. Проверка лимитов файлов
check_file() {
  local file="$1"
  local limit_kb="$2"
  [ -f "$file" ] || return
  local size=$(wc -c < "$file")
  local size_kb=$(( (size + 512) / 1024 ))
  local limit_bytes=$(( limit_kb * 1024 ))
  local warn_bytes=$(( limit_bytes * 80 / 100 ))
  if [ "$size" -gt "$limit_bytes" ]; then
    echo -e "${RED}  ❌ $file ($size_kb КБ > $limit_kb КБ) — ПРЕВЫШЕН ЛИМИТ!${NC}"
    EXIT_CODE=1
  elif [ "$size" -gt "$warn_bytes" ]; then
    echo -e "${YELLOW}  ⚠️  $file ($size_kb КБ, лимит $limit_kb КБ) — приближается к лимиту.${NC}"
  else
    echo -e "${GREEN}  ✅ $file ($size_kb КБ / $limit_kb КБ)${NC}"
  fi
}

echo ""
echo "📏 1. Размеры файлов памяти:"
for f in .memory-bank/*.md; do
  [ -f "$f" ] || continue
  name=$(basename "$f")
  if [ "$name" = "common-mistakes.md" ]; then
    check_file "$f" 10
  else
    check_file "$f" 100
  fi
done

for f in .memory-bank/*/*.md; do
  [ -f "$f" ] || continue
  check_file "$f" 100
done

# 2. Проверка версий
echo ""
echo "🔢 2. Согласованность версий:"
GRADLE_VER=$(grep -E 'versionName\s*=\s*"[^"]+"' app-thunderbird/build.gradle.kts 2>/dev/null | head -1 | sed -E 's/.*"([^"]+)".*/\1/')
DOC_VER=$(grep -oE 'version.*[0-9]+\.[0-9]+' CLAUDE.md 2>/dev/null | head -1 | grep -oE '[0-9]+\.[0-9]+')
FEAT_VER=$(grep -oE 'version.*[0-9]+\.[0-9]+' .memory-bank/features.md 2>/dev/null | head -1 | grep -oE '[0-9]+\.[0-9]+')

echo "  app-thunderbird/build.gradle.kts: $GRADLE_VER"
echo "  CLAUDE.md:                        $DOC_VER"
echo "  features.md:                      $FEAT_VER"

if [ -n "$GRADLE_VER" ] && [ "$GRADLE_VER" = "$DOC_VER" ] && [ "$GRADLE_VER" = "$FEAT_VER" ]; then
  echo -e "${GREEN}  ✅ Версии во всех ключевых местах согласованы ($GRADLE_VER)${NC}"
else
  echo -e "${RED}  ❌ Рассинхрон версий! Выровняй версию в app-thunderbird/build.gradle.kts, CLAUDE.md и features.md${NC}"
  EXIT_CODE=1
fi

# 3. Проверка битых ссылок в CLAUDE.md
echo ""
echo "🔗 3. Ссылки на память в CLAUDE.md:"
BROKEN=0
for ref in $(grep -oE '\.memory-bank/[a-zA-Z0-9./_-]+\.md' CLAUDE.md 2>/dev/null | sort -u); do
  if [ ! -f "$ref" ]; then
    echo -e "${RED}  ❌ $ref упомянут в CLAUDE.md, но такого файла нет на диске!${NC}"
    BROKEN=1
    EXIT_CODE=1
  fi
done
[ "$BROKEN" = "0" ] && echo -e "${GREEN}  ✅ Все ссылки ведут на реальные файлы${NC}"

echo "==============================================="
exit $EXIT_CODE

