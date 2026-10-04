-- Hrvatski jezični portal (hjp.znanje.hr) — источник ударения заглавного слова, со ссылкой.
-- Замысел: docs/implementation/sources.md, «hjp.znanje.hr».
-- @tag:accent @tag:hjp

alter type data_source add value 'HJP';

comment on column word_form.accent_source is
    'Пусто — ударения нет (почему — word.unstressed_source и accent_searched_at); '
        'SOURCE_DICTIONARY/WIKTIONARY/HJP — достоверно; LLM — требует проверки';

comment on column word_form.accent_url is
    'Статья, из которой взято ударение; для WIKTIONARY (CC BY-SA) и HJP обязательна';

alter table word_form
    drop constraint word_form_wiktionary_url;

-- Новое значение перечисления в той же транзакции использовать нельзя — сравнение идёт текстом.
alter table word_form
    add constraint word_form_accent_url
        check (accent_source::text not in ('WIKTIONARY', 'HJP') or accent_url is not null);
