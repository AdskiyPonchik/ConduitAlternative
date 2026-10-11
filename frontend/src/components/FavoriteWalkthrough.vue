<template>
  <div class="walkthrough">
    <fieldset class="scenario-picker">
      <legend>Choose a scenario</legend>
      <label v-for="option in scenarios" :key="option.id" :class="{ selected: scenario === option.id }">
        <input v-model="scenario" type="radio" name="favorite-scenario" :value="option.id" @change="stepIndex = 0">
        {{ option.label }}
      </label>
    </fieldset>

    <div class="request-layout">
      <ol class="request-steps" aria-label="Request steps">
        <li v-for="(item, index) in steps" :key="item.title">
          <button type="button" :aria-current="index === stepIndex ? 'step' : undefined" @click="stepIndex = index">
            <span class="step-number">{{ index + 1 }}</span>
            {{ item.title }}
          </button>
        </li>
      </ol>

      <div class="step-detail" aria-live="polite" aria-atomic="true">
        <p class="step-count">Step {{ stepIndex + 1 }} of {{ steps.length }}</p>
        <h3>{{ currentStep.title }}</h3>
        <p>{{ currentStep.explanation }}</p>
        <pre><code>{{ currentStep.code }}</code></pre>
        <a :href="sourceUrl(currentStep.source)">View this step in the source ↗</a>
      </div>
    </div>

    <div class="walkthrough-controls">
      <button type="button" :disabled="stepIndex === 0" @click="stepIndex--">← Previous</button>
      <p aria-live="polite">{{ outcome }}</p>
      <button type="button" :disabled="stepIndex === steps.length - 1" @click="stepIndex++">Next →</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { favoriteSteps, scenarios, sourceUrl, type FavoriteScenario } from 'src/content/favorite-walkthrough'

const scenario = ref<FavoriteScenario>('first')
const stepIndex = ref(0)
const steps = computed(() => favoriteSteps(scenario.value))
const currentStep = computed(() => steps.value[stepIndex.value])
const outcome = computed(() => scenarios.find(item => item.id === scenario.value)!.outcome)
</script>

<style scoped>
.walkthrough { border: 1px solid #dce4dd; border-radius: 12px; overflow: hidden; background: white; }
.scenario-picker { display: flex; flex-wrap: wrap; gap: 10px; padding: 24px; border: 0; border-bottom: 1px solid #dce4dd; }
.scenario-picker legend { float: left; width: 100%; margin-bottom: 12px; font-weight: 600; }
.scenario-picker label { display: inline-flex; align-items: center; gap: 8px; padding: 8px 12px; border: 1px solid #dce4dd; border-radius: 6px; cursor: pointer; color: #334439; }
.scenario-picker label.selected { background: #edf5ed; border-color: #387541; }
.scenario-picker input { accent-color: #287039; }
.request-layout { display: grid; grid-template-columns: minmax(190px, 1fr) minmax(0, 2fr); }
.request-steps { padding: 20px 12px; background: #f7f9f6; list-style: none; margin: 0; }
.request-steps button { display: flex; align-items: center; gap: 10px; width: 100%; border: 0; border-radius: 6px; padding: 12px; background: transparent; text-align: left; cursor: pointer; color: #4b5c50; line-height: 1.4; }
.request-steps button[aria-current] { background: #e3eddf; color: #203d28; font-weight: 600; }
.step-number { flex-shrink: 0; font-size: 12px; display: grid; place-items: center; width: 24px; height: 24px; border: 1px solid #bdcbbb; border-radius: 50%; }
.step-detail { padding: 28px; min-width: 0; }
.step-detail h3 { font-size: 1.3rem; line-height: 1.35; margin-bottom: 14px; }
.step-detail p { line-height: 1.7; }
.step-count { color: #617165; font-size: 0.8rem; text-transform: uppercase; letter-spacing: .06em; }
.step-detail pre { overflow-x: auto; background: #18291f; color: #e0eedf; border-radius: 6px; padding: 20px; margin: 22px 0; line-height: 1.7; font-size: .8rem; white-space: pre; }
.step-detail a { color: #256232; text-decoration: underline; text-underline-offset: 3px; }
.walkthrough-controls { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 16px 24px; border-top: 1px solid #dce4dd; }
.walkthrough-controls p { margin: 0; font-size: .85rem; color: #49614f; text-align: center; }
.walkthrough-controls button { flex-shrink: 0; background: white; border: 1px solid #ccd8cb; color: #294d33; border-radius: 5px; padding: 7px 11px; cursor: pointer; }
.walkthrough-controls button:disabled { opacity: .4; cursor: default; }
button:focus-visible, label:focus-within { outline: 2px solid #287039; outline-offset: 3px; }
@media (max-width: 650px) {
  .request-layout { grid-template-columns: minmax(0, 1fr); }
  .request-steps { padding: 12px; }
  .request-steps button { padding: 8px; }
  .step-detail { padding: 22px 18px; }
  .scenario-picker { padding: 18px; }
  .walkthrough-controls { flex-wrap: wrap; padding: 16px 18px; }
  .walkthrough-controls p { order: 3; width: 100%; }
}
</style>
