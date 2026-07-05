import tailwindcss from '@tailwindcss/vite';
import { sveltekit } from '@sveltejs/kit/vite';
import { defineConfig } from 'vite';

export default defineConfig({
	plugins: [tailwindcss(), sveltekit()],
	optimizeDeps: {
		include: [
			'phosphor-svelte',
			'svelte-i18n',
			'@supabase/ssr',
			'@supabase/supabase-js',
			'layerchart',
			'd3-shape',
			'd3-scale',
			'svelte-sonner',
			'mode-watcher'
		]
	}
});
