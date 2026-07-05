import { redirect, fail } from '@sveltejs/kit';
import type { Actions, PageServerLoad } from './$types';
import { authedKtor } from '$lib/server/ktor';

export const load: PageServerLoad = async ({ url }) => {
	// "success" | "cancelled" — Stripe redirects back here after checkout.
	return { checkoutStatus: url.searchParams.get('checkout') };
};

export const actions: Actions = {
	// Plain (non-enhanced) form: the thrown 303 to Stripe's hosted Checkout is an
	// external redirect the browser must follow itself.
	checkout: async ({ locals, request }) => {
		const { session } = await locals.safeGetSession();
		if (!session) redirect(303, '/signin?redirectTo=/pro');

		const plan = (await request.formData()).get('plan');
		const res = await authedKtor(session.access_token).post('/billing/checkout', { plan });
		if (!res.ok) return fail(res.status, { error: 'checkout_failed' });

		const { url } = await res.json();
		redirect(303, url);
	}
};
