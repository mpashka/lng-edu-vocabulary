import { ref, watch } from 'vue'

const saved = (key, fallback) => localStorage.getItem(key) ?? fallback
const persistent = (key, fallback) => {
  const value = ref(saved(key, fallback))
  watch(value, next => localStorage.setItem(key, next))
  return value
}

/** Как показывать сербский текст: кириллицей или латиницей. */
export const displayAlphabet = persistent('vocabulary.displayAlphabet', 'cyrillic')
/** Правило выбора сербского или русского поиска по алфавиту ввода. */
export const searchAlphabet = persistent('vocabulary.searchAlphabet', 'current')
/**
 * Искать ли по словоформам. Выключенный поиск ищет только по заглавному слову
 * и переводу: «вода» тогда не приводит к «во̏д», у которого это родительный падеж.
 */
export const searchForms = persistent('vocabulary.searchForms', 'true')
export const showForms = persistent('vocabulary.showForms', 'true')
export const showExamples = persistent('vocabulary.showExamples', 'true')
export const showIdioms = persistent('vocabulary.showIdioms', 'true')
export const showRoots = persistent('vocabulary.showRoots', 'true')
/**
 * Как подписывать падежи и прочие пометы форм — в карточке и в справке по правилу:
 * `both` (по-сербски и по-русски), `russian`, `serbian`, `code`.
 *
 * Прежде выбора «по-русски» не было вовсе, и подпись стояла только сербская. Значение
 * `serbian`, оставшееся у тех, кто настройку не трогал, — не выбор, а старая
 * умолчательная величина, поэтому один раз поднимается до `both`. Осознанно выбранные
 * `russian` и `code` не трогаются.
 */
export const formLabels = persistent('vocabulary.formLabels', 'both')
if (localStorage.getItem('vocabulary.formLabels') === 'serbian'
    && !localStorage.getItem('vocabulary.formLabelsChosen')) {
  formLabels.value = 'both'
}
watch(formLabels, () => localStorage.setItem('vocabulary.formLabelsChosen', 'true'))
export const theme = persistent('vocabulary.theme', 'system')

// Разделы справки по правилу — своя панель, как у карточки.
export const ruleParadigm = persistent('vocabulary.ruleParadigm', 'true')
export const ruleExamples = persistent('vocabulary.ruleExamples', 'true')
export const ruleExceptions = persistent('vocabulary.ruleExceptions', 'true')
/** Разбор именно того слова, из карточки которого открыли правило. */
export const ruleWordBreakdown = persistent('vocabulary.ruleWordBreakdown', 'true')

/**
 * Настройки под наблюдением: их значения уезжают в сообщение об ошибке, а смена
 * попадает в журнал действий. Список здесь один на обоих читателей —
 * [activity.js](activity.js) и [feedback.js](feedback.js), — чтобы диагностика не
 * начала врать при добавлении настройки.
 *
 * @tag:feedback
 */
export const tracked = {
  displayAlphabet: { title: 'сербский текст', value: displayAlphabet },
  searchAlphabet: { title: 'алфавит поиска', value: searchAlphabet },
  searchForms: { title: 'поиск по словоформам', value: searchForms },
  formLabels: { title: 'названия падежей', value: formLabels },
  showForms: { title: 'показ словоформ', value: showForms },
  showExamples: { title: 'показ примеров', value: showExamples },
  showIdioms: { title: 'показ оборотов', value: showIdioms },
  showRoots: { title: 'показ связей с корнями', value: showRoots },
  theme: { title: 'тема', value: theme },
  ruleParadigm: { title: 'правило: парадигма', value: ruleParadigm },
  ruleExamples: { title: 'правило: примеры', value: ruleExamples },
  ruleExceptions: { title: 'правило: исключения', value: ruleExceptions },
  ruleWordBreakdown: { title: 'правило: разбор слова', value: ruleWordBreakdown }
}
