---
tags:
  - architecture
  - ui-ux
  - layout
created: 2026-06-02
status: Draft
---

# UI/UX Layout & Wave Gesture Mechanics

This document provides detailed design and implementation specs for the **Slim Launcher's** visual elements and interactive physics, focusing heavily on matching the premium, fluid feel of Niagara Launcher.

Back to **[[index|Main Hub]]** | Previous: **[[system_requirements|System Requirements]]**

---

## 📱 Home Screen Layout Structure

The layout is unified into a single vertical scroll view (`RecyclerView`) with structural sections:

```
+------------------------------------+
|                                    |
|   [ Status / Clock / Calendar ]    |  <-- Top Context Block
|                                    |
|   +----------------------------+   |
|   |  Host Widget Area (Inline) |   |  <-- Expandable/Collapsible Widget
|   +----------------------------+   |
|                                    |
|   ⭐ FAVORITES                     |
|   - Chrome        [💬 2 Notifications] <-- Inline notification preview
|   - Messages      [💬 Smart Reply...]
|   - Phone                          |
|   - Settings                       |
|                                    |
|   ------------------------------   |
|   🔍 Search Input Area             |  <-- Sticky/Float Search triggers
|                                    |
|   🗂️ APP LIST                      |
|   A - Acrobat Reader               |
|     - Amazon                       |
|   B - Backdrops                    |
|     - Bitwarden                    |  <-- Alphabetic listing
|   ...                              |
|                                    |
+------------------------------------+
```

### Layout Elements:
1. **Context Block**: Displays local weather, time, and active calendar events dynamically.
2. **Inline Widget**: Host container for exactly one primary system widget, which can be collapsed/expanded via vertical swipe.
3. **Favorites List**: A curated set of apps (maximum 8-10 items). Tapping an app expands its notification stack.
4. **App List**: The complete alphabetical listing of apps, which is scrolled via standard swipe or jumping through the sidebar Wave Gesture.

---

## 🌊 Wave Alphabet Gesture Mechanics

The core user experience is the side-scrolling index (the Wave Slider). Instead of a standard straight scrollbar, dragging down the side curves the letters outward in a "wave" and zooms into the current letter.

```
Straight Index          Wave Gesture (Dragging)
      A                       A
      B                       B
      C                     ( C )    <-- Outward curve displacement
    * D *                 ((  D  ))  <-- Selected / Magnified letter
      E                     ( E )
      F                       F
      G                       G
```

### 🧮 Mathematical Model (Displacement & Haptics)

1. **Touch Interception**:
   - The scrollbar is a custom view `WaveGestureView` occupying the right or left edge of the screen (typically `width = 30dp`).
   - Action down starts drag tracking. Action move tracks `y` position relative to the screen.

2. **Letter Displacement Formula**:
   - For every letter `i` in the alphabet list, calculate its distance `dy` from the touch point `y_touch`.
   - Calculate horizontal displacement `x_offset` and scale factor `scale` using a Gaussian curve:
     $$x_{offset}(i) = A \cdot e^{-\frac{dy^2}{2\sigma^2}}$$
     $$scale(i) = 1.0 + B \cdot e^{-\frac{dy^2}{2\sigma^2}}$$
     - Where $A$ is the maximum wave extension (e.g., `40dp`), $B$ is the scale expansion (e.g., `0.5` zoom), and $\sigma$ represents the width of the wave spread.

3. **Drawing Path**:
   - In `onDraw(canvas)`, calculate each letter's `x` and `y` coordinates using the displacement formula.
   - Use `canvas.drawText()` with dynamic text size based on scale.

4. **Haptic Feedback Profile**:
   - As the touch point moves from letter `i` to `i+1`, trigger a micro-haptic tick.
   - **API**: Use `vibrationEffect = VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)` (Android 10+).

---

## ✨ Micro-Animations & Motion Design

All UI transitions must look fluid and organic. Avoid abrupt state changes.

> [!TIP]
> Use standard Android physics-based motion (`SpringAnimation`) instead of linear interpolators. This mimics mass and momentum.

### Key Animations:
*   **App Launch Transition**: When an app is tapped, it scale-morphs into the launched activity from the click target.
*   **Notification Expand**: Tapping a favorite app pushes the adjacent app list downwards with a spring-like rebound, showing notification details inside the card.
*   **Scroll Wave Transition**: The letters must slide back to their straight-line state with a smooth decay spring animation when the finger is lifted.
