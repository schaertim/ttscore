import type { RequestHandler } from './$types';
import { SUPPORTED_LOCALES } from '$lib/i18n';

export const POST: RequestHandler = async ({ request, cookies }) => {
	const { locale } = await request.json();

	if (!SUPPORTED_LOCALES.includes(locale)) {
		return new Response('Unsupported locale', { status: 400 });
	}

	cookies.set('ttscore_locale', locale, {
		path: '/',
		maxAge: 60 * 60 * 24 * 365, // 1 year
		httpOnly: false,
		sameSite: 'lax'
	});

	return new Response(null, { status: 204 });
};
