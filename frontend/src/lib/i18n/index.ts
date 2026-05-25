import { register } from 'svelte-i18n';

export const SUPPORTED_LOCALES = ['de', 'fr', 'it', 'en'] as const;
export type SupportedLocale = (typeof SUPPORTED_LOCALES)[number];
export const DEFAULT_LOCALE: SupportedLocale = 'de';

register('de', () => import('./de.json'));
register('fr', () => import('./fr.json'));
register('it', () => import('./it.json'));
register('en', () => import('./en.json'));

/**
 * Pick the best supported locale from a raw Accept-Language or navigator.language string.
 * Falls back to DEFAULT_LOCALE if nothing matches.
 */
export function resolveLocale(raw: string | null | undefined): SupportedLocale {
	if (!raw) return DEFAULT_LOCALE;
	// Accept-Language may look like "de-CH,de;q=0.9,fr;q=0.8"
	const candidates = raw
		.split(',')
		.map((s) => s.split(';')[0].trim().toLowerCase().slice(0, 2));
	for (const c of candidates) {
		if (SUPPORTED_LOCALES.includes(c as SupportedLocale)) {
			return c as SupportedLocale;
		}
	}
	return DEFAULT_LOCALE;
}
