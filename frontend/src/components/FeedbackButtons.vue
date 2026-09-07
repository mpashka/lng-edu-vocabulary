<script setup>
// @tag:feedback
import { ref, onBeforeUnmount } from 'vue'
import { useRoute } from 'vue-router'
import { composeIssue, warmScreenshot } from '../feedback.js'

const route = useRoute()

const busy = ref(false)
/** Сообщение под панелью: ожидание, судьба снимка или отказ открыть вкладку. */
const status = ref('')
/** Адрес обращения, когда браузер не дал открыть его сам. */
const blocked = ref('')

let timer = null

async function open (kind) {
  if (busy.value) return
  busy.value = true
  blocked.value = ''
  status.value = ''
  // Снимок обычно готов быстрее, чем человек замечает паузу; сообщение о работе
  // показываем, только если не уложились.
  timer = setTimeout(() => { status.value = 'Готовим снимок экрана…' }, 300)

  try {
    const { url, screenshot } = await composeIssue(kind, route)
    clearTimeout(timer)
    const tab = window.open(url, '_blank', 'noopener')
    if (!tab) {
      blocked.value = url
      status.value = 'Браузер не дал открыть вкладку.'
    } else if (screenshot === 'copied') {
      status.value = 'Снимок экрана в буфере обмена — вставьте его в обращение (Ctrl+V).'
    } else if (screenshot !== 'skipped') {
      status.value = `Снимок экрана не приложен: ${screenshot}. Остальное уже вписано в обращение.`
    }
  } finally {
    clearTimeout(timer)
    busy.value = false
  }
}

onBeforeUnmount(() => clearTimeout(timer))
</script>

<template>
  <div class="feedback">
    <div class="feedback-buttons">
      <button
        type="button"
        class="feedback-bug"
        :class="{ busy }"
        title="Открыть обращение на GitHub: состояние словаря будет вписано, снимок экрана — в буфере обмена"
        @mouseenter="warmScreenshot()"
        @focus="warmScreenshot()"
        @click="open('bug')"
      >Сообщить об ошибке</button>
      <button
        type="button"
        :class="{ busy }"
        title="Открыть обращение на GitHub с описанием того, чего словарю не хватает"
        @click="open('feature')"
      >Предложить возможность</button>
    </div>

    <p v-if="status" class="feedback-status">
      {{ status }}
      <a v-if="blocked" :href="blocked" target="_blank" rel="noopener">Открыть обращение</a>
      <button type="button" class="feedback-hide" @click="status = ''; blocked = ''">Скрыть</button>
    </p>
  </div>
</template>
