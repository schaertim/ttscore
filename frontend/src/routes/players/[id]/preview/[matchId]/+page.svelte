<script lang="ts">
	import type { PageData } from './$types';
	import BackButton from '$lib/components/BackButton.svelte';
	import PreviewHeader from '$lib/components/match/PreviewHeader.svelte';
	import OpponentDuelCard from '$lib/components/match/OpponentDuelCard.svelte';
	import PaywallTeaser from '$lib/components/PaywallTeaser.svelte';
	import SectionLabel from '$lib/components/SectionLabel.svelte';
	import { Skeleton } from '$lib/components/ui/skeleton/index.js';
	import { TargetIcon } from 'phosphor-svelte';
	import { _ } from 'svelte-i18n';

	let { data }: { data: PageData } = $props();
</script>

<div class="space-y-6">
	<header class="space-y-4">
		<BackButton />
	</header>

	{#if !data.isPro}
		<PaywallTeaser title={$_('preview.pro_title')} description={$_('preview.pro_desc')} />
	{:else}
		{#await data.streamed.preview}
			<div class="space-y-4">
				<Skeleton class="h-48 w-full rounded-2xl" />
				<Skeleton class="h-24 w-full rounded-xl" />
				<Skeleton class="h-40 w-full rounded-xl" />
			</div>
		{:then previewData}
			{#if !previewData}
				<p class="py-12 text-center text-sm text-muted-foreground">{$_('preview.unavailable')}</p>
			{:else}
				<PreviewHeader fixture={previewData} />

				<!-- One duel per opponent-roster player, strongest first -->
				{#if previewData.duels.length > 0}
					<section class="space-y-2">
						<SectionLabel label={$_('preview.duels')} icon={TargetIcon} />
						<div class="space-y-2.5">
							{#each previewData.duels as duel (duel.awayPlayer.id)}
								<OpponentDuelCard {duel} />
							{/each}
						</div>
					</section>
				{/if}
			{/if}
		{/await}
	{/if}
</div>
