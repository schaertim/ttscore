<script lang="ts">
	import type { PageData } from './$types';
	import LeagueBrowser from '$lib/components/home/LeagueBrowser.svelte';

	let { data }: { data: PageData } = $props();
</script>

<LeagueBrowser seasons={data.seasons} federations={data.federations} />
