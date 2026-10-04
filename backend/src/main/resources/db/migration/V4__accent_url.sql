-- Ссылка на статью-источник ударения: викисловарь под CC BY-SA, и ссылка — условие лицензии.
-- Замысел: docs/implementation/db-schema.md, раздел «Когда ударения нет».
-- @tag:accent @tag:wiktionary

alter table word_form
    add column accent_url text;

comment on column word_form.accent_url is
    'Статья, из которой взято ударение; для WIKTIONARY обязательна (CC BY-SA)';

alter table word_form
    add constraint word_form_wiktionary_url
        check (accent_source is distinct from 'WIKTIONARY' or accent_url is not null);
