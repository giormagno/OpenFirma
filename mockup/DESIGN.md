# Design System Specification: Architectural Futurist

## 1. Overview & Creative North Star
**Creative North Star: "The Precision Blueprint"**
This design system moves away from the "soft and friendly" web toward a high-fidelity, industrial aesthetic. It is inspired by architectural schematics, aerospace interfaces, and clinical precision. We are not building a generic website; we are crafting a digital instrument.

To break the "template" look, this system utilizes **rigorous alignment, intentional asymmetry, and hyper-compact spacing**. While standard UI breathes with large gaps, this system feels "engineered"—data-dense but perfectly legible. We replace decorative elements with functional markers, using the cyan accent as a "laser-guided" focus point for the user.

---

## 2. Colors & Surface Logic

### The "No-Line" Rule & Tonal Hierarchy
Traditional UI uses borders to separate sections. We do not. To create a premium industrial feel, boundaries are defined by **Surface Nesting**. 
- Use `surface` (#f5f7f7) for the main canvas.
- Use `surface_container_low` (#eff1f1) for large sectioning.
- Use `surface_container_lowest` (#ffffff) for the highest priority interactive zones.
Contrast is achieved through these subtle shifts in light, mimicking the way light hits different planes of a physical structure.

### The "Glass & Gradient" Rule
To add "soul" to the industrial coldness, primary actions use a **Signature Cyan Gradient**.
- **Primary CTA:** Linear gradient from `primary_container` (#00f1fe) to `primary` (#00666c) at a 45-degree angle.
- **Glassmorphism:** For floating overlays (modals/tooltips), use `surface_container_lowest` at 80% opacity with a `20px` backdrop-blur. This suggests a "heads-up display" (HUD) layered over the data.

### Key Token Usage
- **Primary Accent (`#00f1fe`):** Reserved strictly for active states, key CTAs, and critical status indicators.
- **Neutral Base:** `background` (#f5f7f7) provides a warm, off-white foundation that prevents eye strain, unlike pure white.

---

## 3. Typography: Space Grotesk
We utilize **Space Grotesk** for its monospaced-adjacent personality and geometric rigor. It evokes a "tech-heavy" feel while remaining highly readable.

- **Display (Large/Medium):** Set to `700` weight with `-0.02em` letter spacing. Use these sparingly for dramatic, editorial-style section headers.
- **Headlines/Titles:** Set to `500` weight. These should be treated as "labels" for the sections below them.
- **Body:** Set to `400` weight. Use `body-md` (0.875rem) as the standard to maintain the "compact" industrial aesthetic.
- **Labels:** Always uppercase with `+0.05em` letter spacing. This mimics engineering stamps or technical documentation.

---

## 4. Elevation & Depth

### The Layering Principle
Depth is achieved through "stacking" without the use of traditional shadows. 
- **Tier 1 (Base):** `surface`
- **Tier 2 (Inlay):** `surface_container` (used for grouping content)
- **Tier 3 (Floating):** `surface_container_lowest` (used for cards/elements that need to pop)

### Ambient Shadows & The Ghost Border
- **Shadows:** Standard shadows are forbidden. If an element must float, use a "Technical Shadow": `color: on_surface (4% opacity)`, `blur: 40px`, `offset-y: 10px`. 
- **The Ghost Border:** Per the industrial brief, "crisp borders" are used *only* as accents. Use `outline_variant` (#abadae) at 20% opacity. This creates a hair-line detail that looks like a precision-cut edge rather than a box.

---

## 5. Components

### Buttons & Inputs
- **Primary Button:** Rectangular (`0px` radius). Background is the Signature Cyan Gradient. Text is `on_primary_fixed` (#004145).
- **Secondary Button:** `0px` radius. Background: `surface_container_highest`. Border: 1px "Ghost Border".
- **Input Fields:** No background. 1px bottom-border only (`outline_variant`). On focus, the bottom border transitions to `primary` (#00f1fe) with a 2px height.

### Cards & Lists
- **No Dividers:** Lists are separated by `spacing-2` (0.4rem) of vertical white space or a subtle background toggle between `surface` and `surface_container_low`.
- **The "Data Cell":** Use `surface_container_high` for small data-heavy chips or tags to make them feel like "inset" buttons on a control panel.

### Specialized Components
- **The "Status Glyph":** A 4x4px square of `primary` cyan placed next to active line items.
- **The "Architectural Grid":** In hero sections, use a 1px `outline_variant` (10% opacity) grid overlay to reinforce the "Blueprint" aesthetic.

---

## 6. Do’s and Don’ts

### Do:
- **Keep it Sharp:** Use the `0px` roundedness scale religiously. Every corner must be a perfect 90-degree angle.
- **Embrace Density:** Use the compact spacing scale (`spacing-1` to `spacing-4`) to group related data tightly.
- **Use Monospace Logic:** Align text and numbers in columns to mimic a technical ledger.

### Don’t:
- **No Softness:** Never use border-radii or soft "blob" shapes.
- **No Heavy Shadows:** Do not use default Material Design shadows; they destroy the "Light Industrial" aesthetic.
- **No Standard Borders:** Avoid 100% opaque borders between sections; rely on the `surface` color shifts defined in Section 2.
- **No Pure Black:** Use `on_surface` (#2c2f30) for text to maintain a sophisticated, high-end tonal range.