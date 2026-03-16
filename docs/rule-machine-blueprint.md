# Rule Machine Blueprint (SNZB-01M)

Use this template to create consistent Rule Machine automations for all gestures.

## Device

- Device: `SONOFF ORB SNZB-01M`
- Buttons: `1..4`
- Gestures: `pushed`, `doubleTapped`, `held`, `triple` (via `lastAction`)

## Standard Triggers

Create rules for each of these triggers:

- `Button 1 pushed`
- `Button 1 doubleTapped`
- `Button 1 held`
- `Button 2 pushed`
- `Button 2 doubleTapped`
- `Button 2 held`
- `Button 3 pushed`
- `Button 3 doubleTapped`
- `Button 3 held`
- `Button 4 pushed`
- `Button 4 doubleTapped`
- `Button 4 held`

## Triple Tap Triggers

Use custom attribute triggers:

- Device: `SONOFF ORB SNZB-01M`
- Attribute: `lastAction`
- Comparison: `contains`

Values:

- `button 1 triple-pushed`
- `button 2 triple-pushed`
- `button 3 triple-pushed`
- `button 4 triple-pushed`

## Suggested Action Matrix

- B1 push: Toggle living room lights
- B1 double: Living room bright scene
- B1 hold: All downstairs off
- B1 triple: Movie scene

- B2 push: Toggle kitchen lights
- B2 double: Kitchen cooking scene
- B2 hold: Kitchen all off
- B2 triple: Dinner scene

- B3 push: Toggle hallway lights
- B3 double: Night path scene
- B3 hold: Bedtime scene
- B3 triple: Good night macro

- B4 push: Toggle accent lamps
- B4 double: Relax scene
- B4 hold: Panic all-on
- B4 triple: Away mode
