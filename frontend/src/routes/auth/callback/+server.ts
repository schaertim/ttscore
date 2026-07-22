import { redirect } from '@sveltejs/kit';
import type { RequestHandler } from './$types';

/**
 * Handles the OAuth PKCE callback from Supabase.
 *
 * After Google (or any OAuth provider) redirects back through Supabase, Supabase
 * redirects here with a `code` query param. We exchange that code for a session,
 * which sets the auth cookies, then always land on the home route — same as
 * email/password sign-in (see LoginForm.svelte).
 */
export const GET: RequestHandler = async ({ url, locals: { supabase } }) => {
	const code = url.searchParams.get('code');

	if (code) {
		const { error } = await supabase.auth.exchangeCodeForSession(code);
		if (error) {
			// Exchange failed — redirect to sign-in with an error hint
			redirect(303, `/signin?error=${encodeURIComponent(error.message)}`);
		}
	}

	redirect(303, '/');
};
