// @tag:feedback
import { watch } from 'vue'
import { tracked } from './settings.js'

/**
 * Журнал последних действий: что человек делал перед тем, как нажать «Сообщить об ошибке».
 *
 * Живёт только в памяти вкладки и никуда не отправляется сам: содержимое уходит наружу
 * ровно один раз — когда человек сам открывает сообщение об ошибке и видит текст перед
 * отправкой. Ни в localStorage, ни на сервер журнал не попадает.
 */
const LIMIT = 20

const events = []

/**
 * Записывает действие. Текст — одной строкой и уже пригодный для чтения человеком:
 * журнал попадает в публичный issue как есть.
 */
export function note (text) {
  events.push({ at: new Date(), text })
  if (events.length > LIMIT) events.shift()
}

/** Журнал от старого к новому — копия, чтобы читатель не менял его случайно. */
export function recentActivity () {
  return events.slice()
}

// Смена настройки — тоже действие: половина «у меня всё не так» объясняется
// переключателем, о котором человек забыл.
for (const { title, value } of Object.values(tracked)) {
  watch(value, next => note(`настройка «${title}» → ${next}`))
}
