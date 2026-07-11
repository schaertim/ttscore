import { error } from '@sveltejs/kit';
import type { PageServerLoad } from './$types';
import { api } from '$lib/api';

export const load: PageServerLoad = async ({ params }) => {
	const player = await api.players.get(params.id).catch(() => null);
	if (!player) error(404, 'Player not found');

	return {
		player,
		streamed: {
			matches: api.players.matches(params.id),
			// Null when the player has no current team — the tab shows its empty state.
			upcoming: api.players.upcoming(params.id).catch(() => null)
		}
	};
};
