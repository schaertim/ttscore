import { error } from '@sveltejs/kit';
import type { PageServerLoad } from './$types';
import { api } from '$lib/api';

export const load: PageServerLoad = async ({ params }) => {
	const match = await api.matches.detail(params.id).catch(() => null);
	if (!match) throw error(404, 'Match not found');
	return { match };
};
