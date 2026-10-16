<script setup lang="ts">
import { NCard, NTag } from 'naive-ui'

const props = defineProps<{
  readonly selected: boolean
  readonly selectionType: 'checkbox' | 'radio'
  readonly name: string
  readonly value: number
}>()

const emit = defineEmits<{
  select: []
}>()

function handleArrow(event: KeyboardEvent, direction: -1 | 1) {
  if (props.selectionType !== 'radio') return

  event.preventDefault()
  const radios = Array.from(document.querySelectorAll<HTMLInputElement>('input[type="radio"]'))
    .filter(control => control.name === props.name && !control.disabled)
  const currentIndex = radios.indexOf(event.currentTarget as HTMLInputElement)
  if (currentIndex < 0 || radios.length === 0) return

  const nextIndex = (currentIndex + direction + radios.length) % radios.length
  const nextRadio = radios[nextIndex]
  nextRadio?.focus()
  nextRadio?.click()
}
</script>

<template>
  <label class="selectable-card-shell">
    <input
      class="selection-control"
      :type="selectionType"
      :name="name"
      :value="value"
      :checked="selected"
      @change="emit('select')"
      @keydown.left="handleArrow($event, -1)"
      @keydown.up="handleArrow($event, -1)"
      @keydown.right="handleArrow($event, 1)"
      @keydown.down="handleArrow($event, 1)"
    >
    <NCard
      class="selectable-card"
      :class="{ 'selectable-card--selected': selected }"
      size="small"
      hoverable
    >
      <template #header>
        <div class="card-title-row">
          <span><slot name="title" /></span>
          <NTag v-if="selected" type="success" size="small" round>✓ 已选择</NTag>
        </div>
      </template>
      <slot />
    </NCard>
  </label>
</template>

<style scoped>
.selectable-card-shell {
  position: relative;
  display: block;
  height: 100%;
  cursor: pointer;
}

.selection-control {
  position: absolute;
  width: 1px;
  height: 1px;
  opacity: 0;
}

.selectable-card {
  height: 100%;
  border-color: var(--aw-color-border);
  cursor: pointer;
  transition:
    border-color var(--aw-motion-fast) ease,
    background-color var(--aw-motion-fast) ease,
    box-shadow var(--aw-motion-fast) ease;
}

.selectable-card-shell:hover .selectable-card {
  background: var(--aw-color-surface-hover);
}

.selection-control:focus-visible + .selectable-card {
  outline: 2px solid var(--aw-color-accent);
  outline-offset: 2px;
}

.selectable-card--selected {
  border-color: var(--aw-color-accent) !important;
  background: var(--aw-color-surface-selected);
  box-shadow: 0 0 0 2px var(--aw-color-accent-ring);
}

.card-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  font-weight: 600;
}

@media (prefers-reduced-motion: reduce) {
  .selectable-card {
    transition: none;
  }
}
</style>
