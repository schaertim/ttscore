/** Shared H2H drawer state. Set opponentId to open the drawer from any page. */
export const h2h = $state<{ opponentId: string | null }>({ opponentId: null });
