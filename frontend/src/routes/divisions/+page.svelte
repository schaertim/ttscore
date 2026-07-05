<script lang="ts">
	import type { PageData } from './$types';
	import LeagueBrowser from '$lib/components/home/LeagueBrowser.svelte';
	import OnboardingModal from '$lib/components/OnboardingModal.svelte';
	import SignInBanner from '$lib/components/home/SignInBanner.svelte';
	import SetPlayerBanner from '$lib/components/home/SetPlayerBanner.svelte';

	let { data }: { data: PageData } = $props();
</script>

<LeagueBrowser seasons={data.seasons} federations={data.federations}>
	{#snippet banner()}
		{#if !data.user}
			<SignInBanner />
		{:else if !data.hasHomePlayer}
			<SetPlayerBanner supabase={data.supabase} />
		{/if}
	{/snippet}
</LeagueBrowser>

{#if !data.user}
	<OnboardingModal />
{/if}
