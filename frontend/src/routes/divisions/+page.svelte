<script lang="ts">
	import type { PageData } from './$types';
	import LeagueBrowser from '$lib/components/home/LeagueBrowser.svelte';
	import OnboardingModal from '$lib/components/OnboardingModal.svelte';
	import SetPlayerBanner from '$lib/components/home/SetPlayerBanner.svelte';

	let { data }: { data: PageData } = $props();
</script>

{#if data.user && !data.hasHomePlayer}
	<div class="mb-4">
		<SetPlayerBanner />
	</div>
{/if}

<LeagueBrowser seasons={data.seasons} federations={data.federations} />

{#if !data.user}
	<OnboardingModal />
{/if}
