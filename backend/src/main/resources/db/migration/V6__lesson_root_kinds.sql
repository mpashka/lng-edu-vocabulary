-- Виды корней, которых требуют связи, найденные уроками: латынь, английский, романские.
-- Замысел: docs/implementation/sources.md, «Находки уроков».
-- @tag:word-roots @tag:lesson-findings

alter type root_kind add value 'LATIN';
alter type root_kind add value 'ENGLISH';
alter type root_kind add value 'ROMANCE';

comment on column word_root.kind is
    'Чем слову родня: PROTO_SLAVIC, PROTO_INDO_EUROPEAN, RUSSIAN, SERBIAN, '
        'LATIN, ENGLISH, ROMANCE; остальные языки-источники — OTHER, сам язык в note';

comment on column word_root.note is
    'Пояснение связи для человека. У вида OTHER называет язык: '
        '«османско-турецкий gübre», «новогреческий gomári»';
