<script lang="ts">
	import type { MatchPreview } from '$lib/api';
	import MatchupCard from './MatchupCard.svelte';
	import PreviewHeader from './PreviewHeader.svelte';
	import SectionLabel from '$lib/components/SectionLabel.svelte';
	import { TargetIcon, ClockCounterClockwiseIcon } from 'phosphor-svelte';
	import { _ } from 'svelte-i18n';

	let { preview }: { preview: MatchPreview } = $props();

	const pm = $derived(preview.previousMeeting);
</script>

<div class="space-y-6">
	<PreviewHeader fixture={preview} />

	<!-- Key duels -->
	{#if preview.keyMatchups.length > 0}
		<section class="space-y-2">
			<SectionLabel label={$_('preview.key_duels')} icon={TargetIcon} />
			<div class="space-y-2.5">
				{#each preview.keyMatchups as m (m.homePlayer.id + m.awayPlayer.id)}
					<MatchupCard matchup={m} />
				{/each}
			</div>
		</section>
	{/if}

	<!-- First leg -->
	{#if pm}
		<section class="space-y-2">
			<SectionLabel label={$_('preview.first_leg')} icon={ClockCounterClockwiseIcon} />
			<a
				href="/matches/{pm.matchId}"
				class="flex items-center rounded-xl border border-border bg-card px-4 py-3 transition-colors hover:bg-accent"
			>
				<span class="min-w-0 flex-1 truncate text-right text-sm font-semibold">{pm.homeTeam}</span>
				<span class="mx-3 shrink-0 font-mono text-lg font-black tabular-nums">
					{pm.homeScore ?? '?'}:{pm.awayScore ?? '?'}
				</span>
				<span class="min-w-0 flex-1 truncate text-sm font-semibold">{pm.awayTeam}</span>
			</a>
		</section>
	{/if}
</div>
