let dark = $state(false);

export const theme = {
	get dark() {
		return dark;
	},
	init() {
		const stored = localStorage.getItem('theme');
		const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
		dark = stored === 'dark' || (!stored && prefersDark);
		apply();
	},
	toggle() {
		dark = !dark;
		localStorage.setItem('theme', dark ? 'dark' : 'light');
		apply();
	}
};

function apply() {
	document.documentElement.classList.toggle('dark', dark);
}
