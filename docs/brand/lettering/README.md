# Lettered page titles

The app's main page titles (stops / zastávky, radar, settings / nastavení; lowercase like the
wordmark) are drawn in the wordmark's construction rather than set in a font: monoline strokes 24 units wide with round caps
and joins, x-height 100, ascender and cap height 150, descender 65 below the baseline, accents as
a short red stroke like the háček.

- `letters.py`: the glyphs used by the titles; `python3 letters.py preview.html` writes a page that
  shows each title next to the wordmark.
- `android.py`: writes the vector drawables (`res/drawable{,-cs}{,-night}/title_*.xml`):
  `python3 android.py ../../../android/app/src/main/res`.

A new title needs its letters in `G` (see the existing ones for the grid) and an entry in
`TITLES`.
