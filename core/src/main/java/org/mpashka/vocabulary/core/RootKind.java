package org.mpashka.vocabulary.core;

import java.util.Optional;

/**
 * Чем слову родня: вид связи в таблице {@code word_root}.
 *
 * <p>Повторяет перечисление {@code root_kind} базы (миграции {@code V1}, {@code V6}).
 * Язык, которого в перечислении нет, кладётся в {@link #OTHER}, а сам язык называет
 * {@code word_root.note}.
 */
// @tag:word-roots
public enum RootKind {

    PROTO_SLAVIC,
    PROTO_INDO_EUROPEAN,
    RUSSIAN,
    SERBIAN,
    LATIN,
    ENGLISH,
    ROMANCE,
    OTHER;

    public static Optional<RootKind> find(String name) {
        for (RootKind kind : values()) {
            if (kind.name().equals(name)) {
                return Optional.of(kind);
            }
        }
        return Optional.empty();
    }
}
