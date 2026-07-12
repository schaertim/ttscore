<script lang="ts">
	import { page } from '$app/state';
	import { Button } from '$lib/components/ui/button/index.js';
	import { HouseIcon } from 'phosphor-svelte';
	import { _ } from 'svelte-i18n';
</script>

<div class="flex min-h-[60vh] flex-col items-center justify-center gap-4 text-center">
	<p class="font-mono text-7xl leading-none font-black text-muted-foreground/20 tabular-nums">
		{page.status}
	</p>
	<p class="text-base font-semibold">
		{$_(page.status === 404 ? 'error.not_found' : 'error.generic')}
	</p>
	<Button href="/" variant="outline" class="rounded-full">
		<HouseIcon size={16} />
		{$_('error.go_home')}
	</Button>
</div>
