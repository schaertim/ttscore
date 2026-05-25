<script lang="ts">
	import type { PageData } from './$types';
	import PlayerDashboard from '$lib/components/home/PlayerDashboard.svelte';

	let { data }: { data: PageData } = $props();
</script>

{#if data.player !== null && data.streamed !== null}
	<PlayerDashboard player={data.player} streamed={data.streamed} />
{/if}
