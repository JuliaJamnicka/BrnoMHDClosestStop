// State shared between pages (pages are replaced, not stacked, on lite wearables).
export default {
  /** Platform chosen in the stop list / platform picker / reverse button; null = follow the nearest stop. */
  pinned: null,
  /** Last "dep" reply, for the platform picker and quick redraw when returning to home. */
  lastDep: null,
  /** Difference phone clock - watch clock in seconds, from the last reply. */
  clockOffset: 0,
  lang: 'cs',
};
