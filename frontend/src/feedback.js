// @tag:feedback
import { tracked } from './settings.js'
import { recentActivity } from './activity.js'

/**
 * Обращение к разработчику: ошибка или пожелание.
 *
 * Словарь не заводит issue сам — он открывает форму GitHub с уже вписанным состоянием.
 * Так человек **видит перед отправкой**, что именно уедет в публичный issue, и может
 * стереть лишнее; серверу при этом не нужен токен с правом писать в репозиторий.
 *
 * Снимок экрана ссылкой передать нельзя — GitHub принимает вложения только вставкой в
 * саму форму. Поэтому снимок кладётся в буфер обмена, а в теле обращения стоит строка,
 * напоминающая вставить его (Ctrl+V).
 */
const ISSUE_FORM = 'https://github.com/mpashka/home-incubator/issues/new'

/**
 * Предел длины готового адреса. Ограничение не наше: длинный URL режут и браузер, и
 * сервер, причём молча. Кириллица в адресе занимает шесть знаков на букву, поэтому
 * запас нужен заметный — при нехватке ужимается журнал, а не состояние.
 */
const URL_LIMIT = 6000

/** Сколько последних действий берём в обращение, если место позволяет. */
const ACTIVITY_LIMIT = 12

/**
 * Готовит обращение: делает снимок экрана (только для ошибки) и собирает адрес формы.
 *
 * @param {'bug'|'feature'} kind
 * @param {object} route текущий маршрут vue-router
 * @returns {Promise<{url: string, screenshot: string}>} `screenshot` — что вышло со
 *   снимком: `copied`, `skipped` (для пожелания он не нужен) либо причина отказа
 */
export async function composeIssue (kind, route) {
  const screenshot = kind === 'bug' ? await copyScreenshot() : 'skipped'
  const title = issueTitle(kind, route)
  const url = withinLimit(count => ISSUE_FORM
    + `?labels=${kind === 'bug' ? 'bug' : 'enhancement'}`
    + `&title=${encodeURIComponent(title)}`
    + `&body=${encodeURIComponent(issueBody(kind, route, screenshot, count))}`)
  return { url, screenshot }
}

function issueTitle (kind, route) {
  const what = kind === 'bug' ? 'ошибка' : 'пожелание'
  if (route?.name === 'word' && route.params?.name) return `[словарь] ${what}: ${route.params.name}`
  const query = typeof route?.query?.q === 'string' ? route.query.q.trim() : ''
  if (query) return `[словарь] ${what} в поиске «${query}»`
  return `[словарь] ${what}`
}

function issueBody (kind, route, screenshot, activityCount) {
  const lines = kind === 'bug'
    ? ['## Что не так', '', '', '## Чего ожидали', '', '']
    : ['## Что хочется', '', '', '## Зачем это нужно — что делаете сейчас вместо этого', '', '']

  if (screenshot === 'copied') {
    lines.push('📎 Снимок экрана лежит в буфере обмена — вставьте его сюда (Ctrl+V).', '')
  } else if (screenshot !== 'skipped') {
    lines.push(`Снимок экрана приложить не удалось: ${screenshot}`, '')
  }

  lines.push('<details><summary>Состояние словаря — собрано страницей</summary>', '')
  lines.push(`- Страница: \`${route?.fullPath ?? '/'}\``)
  lines.push(`- Настройки: ${settingsSummary()}`)
  lines.push(`- Окружение: ${environmentSummary()}`)
  lines.push(`- Сборка: ${buildSummary()}`)

  const journal = recentActivity().slice(-activityCount)
  if (journal.length) {
    lines.push('- Последние действия:')
    for (const event of journal) lines.push(`  - ${clock(event.at)} ${event.text}`)
  }

  lines.push('', '</details>')
  return lines.join('\n')
}

function settingsSummary () {
  return Object.values(tracked).map(({ title, value }) => `${title}: ${value.value}`).join('; ')
}

function environmentSummary () {
  const size = `окно ${window.innerWidth}×${window.innerHeight}`
  const dark = window.matchMedia?.('(prefers-color-scheme: dark)').matches ? 'тёмная' : 'светлая'
  return `${size}, система ${dark}, язык ${navigator.language}, ${navigator.userAgent}`
}

function buildSummary () {
  return `${__BUILD_REVISION__} от ${__BUILD_TIME__}`
}

function clock (date) {
  return date.toTimeString().slice(0, 8)
}

/**
 * Собирает адрес, ужимая журнал, пока не влезет. Журнал урезается первым и до нуля:
 * состояние страницы и настройки нужны всегда, а действия — тем полезнее, чем свежее.
 */
function withinLimit (build) {
  for (let count = ACTIVITY_LIMIT; count > 0; count--) {
    const url = build(count)
    if (url.length <= URL_LIMIT) return url
  }
  return build(0)
}

/**
 * Заранее подтягивает библиотеку снимка — вызывается при наведении на кнопку.
 *
 * Не украшение: браузер разрешает и запись в буфер, и открытие вкладки только вскоре
 * после нажатия. Если библиотека начнёт грузиться по клику, на медленной сети окно
 * успеет стать «непрошеным» и его заблокируют.
 */
export function warmScreenshot () {
  import('html2canvas').catch(() => {})
}

/**
 * Снимает страницу и кладёт PNG в буфер обмена.
 *
 * html2canvas грузится только здесь и только по нажатию: библиотека весит больше всей
 * остальной оболочки, а нужна раз в сто посещений.
 *
 * @returns {Promise<string>} `copied` либо причина, по которой снимка не будет
 */
async function copyScreenshot () {
  if (!navigator.clipboard?.write || typeof ClipboardItem === 'undefined') {
    return 'браузер не умеет класть картинки в буфер обмена'
  }
  try {
    const { default: html2canvas } = await import('html2canvas')
    const canvas = await html2canvas(document.body, {
      logging: false,
      backgroundColor: getComputedStyle(document.body).backgroundColor,
      scale: Math.min(window.devicePixelRatio || 1, 2)
    })
    const blob = await new Promise(resolve => canvas.toBlob(resolve, 'image/png'))
    if (!blob) return 'снимок не удалось перевести в PNG'
    await navigator.clipboard.write([new ClipboardItem({ 'image/png': blob })])
    return 'copied'
  } catch (error) {
    return error?.message || String(error)
  }
}
