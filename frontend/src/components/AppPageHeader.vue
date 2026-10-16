<script setup lang="ts">
defineProps<{
  readonly title: string
  readonly description?: string
  readonly eyebrow?: string
}>()
</script>

<template>
  <header class="page-header">
    <div class="page-header__copy">
      <p v-if="eyebrow" class="page-header__eyebrow">{{ eyebrow }}</p>
      <h1>{{ title }}</h1>
      <p v-if="description" class="page-header__description">{{ description }}</p>
    </div>
    <div v-if="$slots.actions" class="page-header__actions">
      <slot name="actions" />
    </div>
  </header>
</template>

<style scoped>
.page-header {
  display: flex;
  min-width: 0;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--aw-spacing-4);
}

.page-header__copy {
  min-width: 0;
}

.page-header__eyebrow {
  margin: 0 0 6px;
  color: var(--aw-accent);
  font-size: 12px;
  font-weight: 600;
}

h1 {
  margin: 0;
  color: var(--aw-text-primary);
  font-size: 22px;
  font-weight: 650;
  line-height: 1.25;
}

.page-header__description {
  max-width: 680px;
  margin: 7px 0 0;
  color: var(--aw-text-secondary);
  font-size: 13px;
  line-height: 1.55;
}

.page-header__actions {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  gap: var(--aw-spacing-2);
}

@media (max-width: 640px) {
  .page-header {
    align-items: stretch;
    flex-direction: column;
  }

  .page-header__actions {
    flex-wrap: wrap;
  }
}
</style>
