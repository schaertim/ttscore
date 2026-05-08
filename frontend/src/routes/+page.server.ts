import { redirect } from '@sveltejs/kit';
import type { PageServerLoad } from './$types';
import { authedKtor } from '$lib/server/ktor';

export const load: PageServerLoad = async ({ locals }) => {
	const { session } = await locals.safeGetSession();

	if (session) {
		const res = await authedKtor(session.access_token).get('/users/me');
		if (res.ok) {
			const profile = await res.json();
			if (profile.homePlayerId) {
				redirect(303, `/players/${profile.homePlayerId}`);
			}
		}
	}

	redirect(303, '/divisions');
};
