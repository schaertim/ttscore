import tailwindcss from '@tailwindcss/vite';
import { sveltekit } from '@sveltejs/kit/vite';
import { sveltePhosphorOptimize } from 'phosphor-svelte/vite';
import { defineConfig } from 'vite';

export default defineConfig({
	// sveltePhosphorOptimize rewrites `import { X } from 'phosphor-svelte'` to deep
	// `phosphor-svelte/lib/X` imports so dev SSR only compiles the icons we use, rather
	// than the entire ~3000-icon barrel on the first request (was a ~35s first-load stall).
	plugins: [tailwindcss(), sveltekit(), sveltePhosphorOptimize()],
	optimizeDeps: {
		include: [
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
