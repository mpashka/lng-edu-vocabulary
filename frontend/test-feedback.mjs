// Проверка сборки обращения: что уезжает в issue и что адрес остаётся в пределах,
// после которых браузер и сервер молча его обрежут.
//
// Браузерного окружения в node нет — подделываем ровно то, что читает код оболочки.
globalThis.localStorage = { getItem: () => null, setItem: () => {} }
globalThis.window = { innerWidth: 1280, innerHeight: 800, matchMedia: () => ({ matches: false }) }
// `navigator` в node только для чтения — подменяем свойствами объекта, а не целиком.
Object.defineProperty(globalThis.navigator, 'language', { value: 'ru-RU', configurable: true })
Object.defineProperty(globalThis.navigator, 'userAgent', { value: 'node-test', configurable: true })
globalThis.__BUILD_REVISION__ = 'abc1234'
globalThis.__BUILD_TIME__ = '2026-09-07 12:00'

const { note } = await import('./src/activity.js')
const { composeIssue } = await import('./src/feedback.js')

let failures = 0
const check = (what, condition) => {
  if (!condition) {
    console.error(`  ✗ ${what}`)
    failures++
  }
}

const wordRoute = { name: 'word', params: { name: 'кућа' }, query: { id: '13304' }, fullPath: '/word/кућа?id=13304' }
const searchRoute = { name: 'search', params: {}, query: { q: 'kuca' }, fullPath: '/?q=kuca' }

note('поиск «kuca» → показано 6 из 42')
note('открыта статья «кућа» (id 13304): 1 шт., нашлось по headword')

const feature = await composeIssue('feature', searchRoute)
const body = decodeURIComponent(new URL(feature.url).searchParams.get('body'))
const title = decodeURIComponent(new URL(feature.url).searchParams.get('title'))

check('пожелание не делает снимок', feature.screenshot === 'skipped')
check('метка пожелания — enhancement', feature.url.includes('labels=enhancement'))
check('заголовок называет запрос', title === '[словарь] пожелание в поиске «kuca»')
check('в теле есть страница', body.includes('- Страница: `/?q=kuca`'))
check('в теле есть настройки', body.includes('сербский текст: cyrillic'))
check('в теле есть журнал', body.includes('поиск «kuca» → показано 6 из 42'))
check('в теле есть версия сборки', body.includes('abc1234 от 2026-09-07 12:00'))
check('о снимке в пожелании не говорится', !body.includes('Ctrl+V'))

// У ошибки без буфера обмена снимка не выйдет — отказ обязан быть назван, а не проглочен.
const bug = await composeIssue('bug', wordRoute)
const bugBody = decodeURIComponent(new URL(bug.url).searchParams.get('body'))
check('метка ошибки — bug', bug.url.includes('labels=bug'))
check('заголовок ошибки называет слово',
  decodeURIComponent(new URL(bug.url).searchParams.get('title')) === '[словарь] ошибка: кућа')
check('причина отсутствия снимка названа', bugBody.includes('Снимок экрана приложить не удалось'))

// Длинный журнал не должен раздувать адрес: кириллица занимает шесть знаков на букву.
for (let i = 0; i < 40; i++) note(`поиск «многобуквенное сербское слово номер ${i}» → показано 20 из 400`)
const long = await composeIssue('feature', wordRoute)
check('адрес остаётся в пределах 6000 знаков', long.url.length <= 6000)
const longBody = decodeURIComponent(new URL(long.url).searchParams.get('body'))
check('состояние сохранено даже при урезанном журнале', longBody.includes('- Настройки: '))

if (failures) {
  console.error(`feedback: ${failures} проверок не прошло`)
  process.exit(1)
}
console.log('feedback: все проверки прошли')
