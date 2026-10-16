# AgentWriter Design Contract

## 1. Purpose and Product Context

AgentWriter is a dark-theme content operations console. The interface should help editors move quickly through topic discovery, article creation, generation monitoring, and publishing without visual distraction.

The create-article experience is a guided workflow:

1. Select one or more trending topics.
2. Enter the article title.
3. Select an optional content template.
4. Select a writing style and start generation.

## 2. Design Principles

- Preserve the compact, dark administrative-console character of the existing product.
- Use progressive disclosure: show only the controls needed for the current step.
- Keep the primary action visually obvious and place it consistently at the lower right.
- Make selection state unmistakable through border, surface, and checkmark changes.
- Always provide explicit loading, empty, error, disabled, and selected states.
- Prefer Naive UI primitives and theme behavior over bespoke controls.

## 3. Color Tokens

| Token | Value | Usage |
| --- | --- | --- |
| `--aw-color-accent` | `#18a058` | Primary actions and selected borders |
| `--aw-color-accent-text` | `#63e2b7` | Accent text on dark surfaces |
| `--aw-color-text-primary` | `rgba(255, 255, 255, 0.85)` | Main copy |
| `--aw-color-text-secondary` | `rgba(255, 255, 255, 0.52)` | Supporting copy |
| `--aw-color-text-muted` | `rgba(255, 255, 255, 0.38)` | Metadata and placeholders |
| `--aw-color-surface` | `rgba(255, 255, 255, 0.03)` | Card surfaces |
| `--aw-color-surface-hover` | `rgba(255, 255, 255, 0.06)` | Hovered selectable cards |
| `--aw-color-surface-selected` | `rgba(24, 160, 88, 0.10)` | Selected cards |
| `--aw-color-border` | `rgba(255, 255, 255, 0.08)` | Default borders |
| `--aw-color-border-strong` | `rgba(255, 255, 255, 0.14)` | Emphasized dividers |
| `--aw-color-warning` | `#f0a020` | Warning and degraded states |
| `--aw-color-info` | `#2080f0` | Informational state |

## 4. Typography

- Use the system sans-serif stack inherited from the application shell.
- Page/card title: 18–20px, semibold.
- Section title and selectable-card title: 14–16px, semibold.
- Body: 14px, regular.
- Metadata and supporting copy: 12–13px, regular.
- Chinese copy should use normal sentence spacing and avoid letter spacing.

## 5. Spacing and Layout

- Base spacing scale: 4, 8, 12, 16, 20, 24, 32, 40px.
- Application content retains the existing 24px desktop padding and 16px mobile padding.
- Wizard content uses 32px between the stepper, content panel, and action row.
- Selectable cards use a 16px gap and collapse from two columns to one below 768px.
- Forms use a readable maximum width of 640px while selection grids may use the full card width.
- Prefer `min-height: 100dvh` for new full-height surfaces. The existing application shell still uses `100vh` and remains accepted debt outside this change.

## 6. Component Contract

### Wizard stepper

- Uses Naive UI `NSteps` and `NStep`.
- Desktop shows all four horizontal steps.
- Mobile may scroll horizontally rather than compress labels into unreadable widths.

### Selectable card

- Entire card is keyboard and pointer selectable.
- Default state uses the standard border and surface.
- Hover state increases surface contrast.
- Selected state uses the accent border, accent-tinted surface, and a visible selected marker.
- Focus state must remain visible.

### Topic selection panel

- Header shows a short instruction and the current selected count.
- Topics display title, source, score, and optional reason.
- Loading state uses Naive UI skeletons or spinner.
- Empty state explains that no topics are available and links the user to the trending-topic page.
- Error state displays the API error and a retry action.

### Form and action row

- Required fields use Naive UI form semantics.
- “Next” is disabled until the current step is valid.
- The final action is disabled until a writing style has been selected.
- “Previous” remains available after the first step and never discards entered data.

## 7. Interaction and Accessibility

- Selection controls expose `role="checkbox"` or equivalent semantics and `aria-checked`.
- All pointer interactions have keyboard equivalents.
- Do not rely on color alone to communicate selection.
- Motion is limited to short 150–200ms hover and selection transitions and respects reduced-motion preferences.
- Text and controls should maintain readable contrast in the dark theme.
- Focus order follows visual order from the stepper content to the action row.

## 8. Responsive and State Behavior

- Desktop (≥1280px): four-step header, two-column selection grids.
- Tablet (768px): four-step header remains usable; content grid may stay two columns when space permits.
- Mobile (375px): stepper scrolls horizontally, grids become one column, and action buttons remain reachable without horizontal overflow.
- Normal, selected, loading, empty, error, disabled, and submitting states are part of the component contract.

## Accepted Design Debt

- Existing views contain raw color values and inline spacing declarations. New create-wizard styles use the tokens above; unrelated views are not refactored in this change.
- The existing application shell uses `100vh`. Migrating the whole shell to dynamic viewport units is intentionally deferred.
