import { STORAGE_KEYS } from '$lib/storageKeys';

let dark = $state(false);

export const theme = {
	get dark() {
		return dark;
	},
	init() {
		const stored = localStorage.getItem(STORAGE_KEYS.theme);
		const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
		dark = stored === 'dark' || (!stored && prefersDark);
		apply();
	},
	toggle() {
		dark = !dark;
		localStorage.setItem(STORAGE_KEYS.theme, dark ? 'dark' : 'light');
		apply();
	}
};

// Keep the Android status bar (theme-color) in sync with --background, matching
// the user's actual theme (including their manual override), not just the OS's
// prefers-color-scheme.
const LIGHT_BACKGROUND = '#ffffff';
const DARK_BACKGROUND = '#09090b';

function apply() {
	document.documentElement.classList.toggle('dark', dark);
	document
		.querySelector('meta[name="theme-color"]')
		?.setAttribute('content', dark ? DARK_BACKGROUND : LIGHT_BACKGROUND);
}
