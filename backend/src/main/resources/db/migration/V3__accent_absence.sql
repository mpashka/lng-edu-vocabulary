-- Отсутствие ударения бывает трёх видов, и их надо различать: слово безударное по природе,
-- ударение искали и не нашли, ударение ещё не искали.
-- Замысел: docs/implementation/db-schema.md, раздел «Когда ударения нет».
-- @tag:accent @tag:word-forms

alter table word
    add column unstressed_source data_source;

comment on column word.unstressed_source is
    'Слово безударное по природе (за, из, и, да, га) — кто это установил; пусто — обычное слово';

alter table word_form
    add column accent_searched_at timestamptz;

comment on column word_form.accent_searched_at is
    'Когда ударение искали во внешних источниках. При пустом accent_source — искали и не нашли; '
        'пусто — ещё не искали';

comment on column word_form.accent_source is
    'Пусто — ударения нет (почему — word.unstressed_source и accent_searched_at); '
        'SOURCE_DICTIONARY/WIKTIONARY — достоверно; LLM — требует проверки';

-- Перенос ставил источник ударения заглавной форме и тогда, когда тона в ней нет.
update word_form
   set accent_source = null
 where accent_source is not null
   and form !~ '[̀́̏̑]';

-- Очередь этапа 10: формы, по которым внешние источники проверены и пусты.
create index word_form_accent_not_found_idx on word_form (word_id)
    where accent_source is null and accent_searched_at is not null;
