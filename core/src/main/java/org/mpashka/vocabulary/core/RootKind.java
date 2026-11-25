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

    PROTO_SLAVIC("праславянский"),
    PROTO_INDO_EUROPEAN("праиндоевропейский"),
    RUSSIAN("русский"),
    SERBIAN("сербский"),
    LATIN("латынь"),
    ENGLISH("английский"),
    ROMANCE("романский"),
    OTHER("другой");

    private final String title;

    RootKind(String title) {
        this.title = title;
    }

    /** Название по-русски. */
    public String title() {
        return title;
    }

    public static Optional<RootKind> find(String name) {
        for (RootKind kind : values()) {
            if (kind.name().equals(name)) {
                return Optional.of(kind);
            }
        }
        return Optional.empty();
    }
}
