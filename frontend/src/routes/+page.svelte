<script lang="ts">
	import type { PageData } from './$types';
	import LeagueBrowser from '$lib/components/home/LeagueBrowser.svelte';
	import SetPlayerBanner from '$lib/components/home/SetPlayerBanner.svelte';
	import PlayerDashboard from '$lib/components/home/PlayerDashboard.svelte';

	let { data }: { data: PageData } = $props();
</script>

{#if data.state === 'dashboard' && data.player !== null && data.streamed !== null}
	<PlayerDashboard player={data.player} streamed={data.streamed} />
{:else if data.state === 'no-home-player'}
	<div class="space-y-6">
		<SetPlayerBanner />
		<LeagueBrowser seasons={data.seasons} federations={data.federations} />
	</div>
{:else}
	<LeagueBrowser seasons={data.seasons} federations={data.federations} />
{/if}
