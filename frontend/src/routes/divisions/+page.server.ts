import { api } from '$lib/api';
import type { PageServerLoad } from './$types';

export const load: PageServerLoad = async ({ url }) => {
	const [seasons, federations] = await Promise.all([api.seasons.list(), api.federations.list()]);

	// Same "URL param, else newest" pick LeagueBrowser makes for its default season — prefetching
	// that season's groups/stats here means the initial render doesn't have to wait for a
	// client-side fetch after hydration just to show what SSR could have shipped directly.
	const paramSeasonId = url.searchParams.get('season');
	const initialSeason = seasons.find((s) => s.id === paramSeasonId) ?? seasons[0];

	return {
		seasons,
		federations,
		streamed: {
			groups: initialSeason ? api.groups.list({ season: initialSeason.name }) : Promise.resolve([]),
			stats: initialSeason
				? api.stats.get(initialSeason.name).catch(() => null)
				: Promise.resolve(null)
		}
	};
};
