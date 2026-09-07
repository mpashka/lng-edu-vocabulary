package org.mpashka.vocabulary.backend;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Отдаёт оболочку по адресам её собственных страниц.
 *
 * <p>Маршруты вида {@code /word/вода} знает только страница: сервер такого ресурса не
 * держит и без этого ответил бы 404 на прямую ссылку и на перезагрузку статьи.
 *
 * @tag:deploy
 */
@Controller
class ShellController {

    @GetMapping("/word/**")
    String shell() {
        return "forward:/index.html";
    }
}
