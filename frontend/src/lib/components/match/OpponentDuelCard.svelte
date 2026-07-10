<script lang="ts">
	import type { PreviewMatchup } from '$lib/api';
	import ClassBadge from '$lib/components/ClassBadge.svelte';
	import FormPills from '$lib/components/FormPills.svelte';
	import { comparePlayers } from '$lib/h2h.svelte';
	import { formatShortName } from '$lib/utils';
	import { _ } from 'svelte-i18n';

	// Single-sided variant of MatchupCard for the player preview: homePlayer is always the
	// focus player, so only the opponent is shown. Record and win% read from the focus
	// player's perspective.
	let { duel }: { duel: PreviewMatchup } = $props();

	const opp = $derived(duel.awayPlayer);
	const pct = $derived(
		duel.homeWinProbability != null ? Math.round(duel.homeWinProbability * 100) : null
	);
</script>

<button
	type="button"
	onclick={() => comparePlayers(duel.homePlayer.id, opp.id)}
	class="w-full rounded-xl border border-border bg-card px-4 py-3 text-left transition-colors hover:bg-accent"
>
	<div class="flex items-center justify-between gap-3">
		<div class="min-w-0">
			<div class="flex min-w-0 items-center gap-1.5">
				<p class="min-w-0 truncate text-sm font-semibold">{formatShortName(opp.fullName)}</p>
				<ClassBadge classification={opp.classification} />
			</div>
			<span class="mt-0.5 block font-mono text-2xs text-muted-foreground tabular-nums">
				{#if opp.elo != null}{opp.elo} ELO{:else}&nbsp;{/if}
			</span>
		</div>

		<div class="flex shrink-0 items-center">
			{#if duel.results.length > 0}
				<FormPills form={duel.results} size={18} class="flex-wrap justify-end" />
			{:else}
				<span class="text-2xs font-semibold tracking-widest text-muted-foreground uppercase">
					{$_('preview.first_meeting')}
				</span>
			{/if}
		</div>
	</div>

	{#if pct != null}
		<div class="mt-2.5 flex items-center gap-2">
			<div class="h-1.5 flex-1 overflow-hidden rounded-full bg-muted">
				<div class="h-full rounded-full bg-primary" style="width: {pct}%"></div>
			</div>
			<span class="shrink-0 font-mono text-2xs text-muted-foreground tabular-nums">{pct}%</span>
		</div>
	{/if}
</button>
