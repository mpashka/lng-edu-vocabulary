#!/usr/bin/env bash
# Перенос содержимого словаря на сервер: слепок локальной базы -> база в контейнере.
# Устройство выкладки — docs/implementation/deploy.md.
set -euo pipefail

server="${VOCABULARY_SERVER:-yc-user@app-server.pasha-home.ru}"
container="${VOCABULARY_DB_CONTAINER:-vocabulary-db-1}"
app_container="${VOCABULARY_APP_CONTAINER:-vocabulary-app-1}"
repo="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
secrets="$repo/secrets.properties"

if [[ ! -f "$secrets" ]]; then
    echo "Нет $secrets — из него берётся подключение к локальной базе." >&2
    echo "Заполни его по secrets.properties.template (см. README.md) и повтори." >&2
    exit 1
fi

property() { sed -n "s/^$1=//p" "$secrets" | tail -1; }

url="$(property vocabulary.db.url)"
user="$(property vocabulary.db.username)"
password="$(property vocabulary.db.password)"
host="$(sed -E 's|jdbc:postgresql://([^:/]+).*|\1|' <<<"$url")"
port="$(sed -E 's|jdbc:postgresql://[^:/]+:?([0-9]*)/.*|\1|' <<<"$url")"
database="$(sed -E 's|.*/([^/?]+)(\?.*)?$|\1|' <<<"$url")"

dump="$(mktemp -t vocabulary-XXXXXX.sql.gz)"
trap 'rm -f "$dump"' EXIT

echo "Слепок $database с $host:${port:-5432}…"
PGPASSWORD="$password" pg_dump --no-owner --no-privileges --clean --if-exists \
    -h "$host" -p "${port:-5432}" -U "$user" "$database" | gzip -6 > "$dump"
echo "  готово: $(du -h "$dump" | cut -f1)"

echo "Отправка на $server…"
scp -q "$dump" "$server:/tmp/vocabulary.sql.gz"

# Приложение останавливается на время заливки: слепок сносит и создаёт заново те же таблицы,
# из которых оно в этот момент читает. База остаётся поднятой — заливать надо в неё.
echo "Заливка в контейнер $container (приложение на это время остановлено)…"
ssh "$server" bash -euo pipefail -s "$container" "$app_container" <<'REMOTE'
db_container="$1"
app_container="$2"
docker stop "$app_container" >/dev/null
gunzip -c /tmp/vocabulary.sql.gz \
    | docker exec -i "$db_container" psql -v ON_ERROR_STOP=1 -q -o /dev/null -U vocabulary -d vocabulary
docker start "$app_container" >/dev/null
rm -f /tmp/vocabulary.sql.gz
REMOTE

echo "Приложение поднимается…"
for _ in $(seq 1 30); do
    words="$(curl -fsS "https://recnik.srpski.pasha-home.ru/api/words?q=voda" 2>/dev/null \
             | sed -n 's/.*"total":\([0-9]*\).*/\1/p' || true)"
    [[ -n "${words:-}" ]] && break
    sleep 3
done

if [[ -z "${words:-}" ]]; then
    echo "Словарь не ответил за полторы минуты после заливки." >&2
    echo "Смотреть: ssh $server 'docker logs --tail 50 $app_container'" >&2
    exit 1
fi
echo "Готово: по запросу «voda» словарь находит $words статей."
