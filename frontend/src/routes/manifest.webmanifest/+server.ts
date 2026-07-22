import type { RequestHandler } from './$types';
import { resolveLocale } from '$lib/i18n';
import { STORAGE_KEYS } from '$lib/storageKeys';
import en from '$lib/i18n/en.json';
import de from '$lib/i18n/de.json';
import fr from '$lib/i18n/fr.json';
import it from '$lib/i18n/it.json';

// Same locale files as the app UI, so the manifest description (shown on the
// install prompt / app store listing) never drifts out of sync with the rest
// of the copy.
const descriptions: Record<string, string> = {
	en: en.manifest.description,
	de: de.manifest.description,
	fr: fr.manifest.description,
	it: it.manifest.description
};

export const GET: RequestHandler = ({ cookies, request }) => {
	// Same resolution order as +layout.server.ts: explicit cookie (set once the
	// user picks a language in-app), then the browser's Accept-Language header.
	const locale = resolveLocale(
		cookies.get(STORAGE_KEYS.locale) ?? request.headers.get('accept-language')
	);

	const manifest = {
		name: 'TTScore',
		short_name: 'TTScore',
		description: descriptions[locale],
		start_url: '/',
		scope: '/',
		display: 'standalone',
		background_color: '#09090b',
		theme_color: '#09090b',
		// Chrome's installability check requires a real square PNG — an SVG-only
		// declaration isn't sufficient (DevTools: "Most operating systems require
		// square icons"). The SVG is intentionally NOT listed: Chrome fails to
		// rasterize it as an install icon ("Icon … failed to load"), and the PNGs
		// already cover every case. Not marked maskable: the source design bleeds to
		// the edge with no safe-zone padding, so an OS adaptive-icon mask
		// (circle/squircle) would clip into the logo.
		icons: [
			{ src: '/icon-192.png', type: 'image/png', sizes: '192x192', purpose: 'any' },
			{ src: '/icon-512.png', type: 'image/png', sizes: '512x512', purpose: 'any' }
		]
	};

	return new Response(JSON.stringify(manifest), {
		headers: {
			'Content-Type': 'application/manifest+json',
			// Locale-dependent (cookie or Accept-Language) — never cache across users/languages.
			'Cache-Control': 'private, max-age=3600',
			Vary: 'Cookie, Accept-Language'
		}
	});
};
