import type { PageServerLoad } from './$types';
import { redirect } from '@sveltejs/kit';
import { authedKtor } from '$lib/server/ktor';

export const load: PageServerLoad = async ({ locals }) => {
	const { session } = await locals.safeGetSession();

	if (!session) {
		redirect(303, '/');
	}

	const ktor = authedKtor(session.access_token);
	const favorites = ktor
		.get('/favorites')
		.then((res) => (res.ok ? res.json() : []))
		.catch(() => []);

	return { favorites };
};
