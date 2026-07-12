/**
 * Shared H2H drawer state. Set `rightId` (via the helpers) to open the drawer from any page.
 *
 * `leftId === null` is a sentinel meaning "the signed-in user's home player" — the layout resolves
 * it against `data.homePlayerId`. Passing an explicit `leftId` compares two arbitrary players
 * (e.g. a profile player vs one of their rivals), independent of who is signed in.
 */
export const h2h = $state<{ leftId: string | null; rightId: string | null }>({
	leftId: null,
	rightId: null
});

/** Compare the signed-in user against `otherId` (the classic "compare with me"). */
export function compareWithMe(otherId: string) {
	h2h.leftId = null;
	h2h.rightId = otherId;
}

/** Compare two explicit players, e.g. a profile player vs one of their rivals. */
export function comparePlayers(leftId: string, rightId: string) {
	h2h.leftId = leftId;
	h2h.rightId = rightId;
}

/** Clear the comparison (drawer closed). */
export function closeH2H() {
	h2h.leftId = null;
	h2h.rightId = null;
}
