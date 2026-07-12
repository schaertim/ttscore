<script lang="ts">
	import { _ } from 'svelte-i18n';
	import type { Group, Standing } from '$lib/api';
	import * as Table from '$lib/components/ui/table/index.js';

	interface Props {
		standings: Standing[];
		/** Provides the promotion/relegation spot counts for the zone markers. */
		group: Group;
	}

	let { standings, group }: Props = $props();

	const sorted = $derived([...standings].sort((a, b) => a.position - b.position));

	function zone(pos: number): 'promotion' | 'relegation' | null {
		const { promotionSpots, relegationSpots } = group;
		if (promotionSpots && pos <= promotionSpots) return 'promotion';
		if (relegationSpots && pos > sorted.length - relegationSpots) return 'relegation';
		return null;
	}

	function diff(won: number, lost: number): string {
		const d = won - lost;
		return d > 0 ? `+${d}` : `${d}`;
	}
</script>

<div class="overflow-hidden rounded-xl border border-border">
	<Table.Root>
		<Table.Header>
			<Table.Row class="border-border hover:bg-transparent">
				<Table.Head class="w-8 pl-4 text-xs">{$_('group.pos')}</Table.Head>
				<Table.Head class="text-xs">{$_('group.team')}</Table.Head>
				<Table.Head class="w-10 text-center text-xs">{$_('group.played')}</Table.Head>
				<Table.Head class="w-10 text-center text-xs">{$_('group.points')}</Table.Head>
				<Table.Head class="w-12 pr-4 text-right text-xs">{$_('group.diff')}</Table.Head>
			</Table.Row>
		</Table.Header>
		<Table.Body>
			{#each sorted as row (row.teamId)}
				{@const z = zone(row.position)}
				<Table.Row class="border-border">
					<Table.Cell
						class="border-l-2 pl-4 font-mono font-semibold tabular-nums
						{z === 'promotion'
							? 'border-l-win'
							: z === 'relegation'
								? 'border-l-loss'
								: 'border-l-transparent'}"
					>
						{row.position}
					</Table.Cell>

					<Table.Cell class="text-sm font-semibold">
						<a href="/teams/{row.teamId}" class="hover:underline">
							{row.team}
						</a>
					</Table.Cell>

					<Table.Cell class="text-center font-mono tabular-nums">
						{row.played}
					</Table.Cell>

					<Table.Cell class="text-center font-mono font-semibold tabular-nums">
						{row.points}
					</Table.Cell>

					<Table.Cell
						class="pr-4 text-right font-mono font-semibold tabular-nums
						{row.gamesWon - row.gamesLost > 0
							? 'text-win'
							: row.gamesWon - row.gamesLost < 0
								? 'text-loss'
								: 'text-muted-foreground'}"
					>
						{diff(row.gamesWon, row.gamesLost)}
					</Table.Cell>
				</Table.Row>
			{/each}
		</Table.Body>
	</Table.Root>
</div>
