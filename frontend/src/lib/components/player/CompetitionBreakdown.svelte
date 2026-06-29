<script lang="ts">
	import { _ } from 'svelte-i18n';
	import type { CompetitionStat } from '$lib/api';
	import * as Card from '$lib/components/ui/card/index.js';
	import { cn } from '$lib/utils';

	interface Props {
		competitions: CompetitionStat[];
	}

	let { competitions }: Props = $props();

	function pct(c: CompetitionStat): number {
		return c.games > 0 ? Math.round((c.wins / c.games) * 100) : 0;
	}
</script>

<div class="space-y-3">
	{#each competitions as comp (comp.name)}
		{@const p = pct(comp)}
		<Card.Root class="flex-row items-center justify-between gap-3 p-4">
			<div class="min-w-0">
				<p class="truncate text-sm font-semibold">{comp.name}</p>
				<p class="mt-0.5 text-2xs text-muted-foreground">
					{comp.isTournament ? $_('stats.tournament') : $_('stats.league')} · {comp.wins}/{comp.games}
				</p>
			</div>
			<span
				class={cn(
					'shrink-0 text-xl font-black tabular-nums',
					p >= 50 ? 'text-win' : p >= 33 ? 'text-foreground' : 'text-loss'
				)}
			>
				{p}%
			</span>
		</Card.Root>
	{/each}
</div>
