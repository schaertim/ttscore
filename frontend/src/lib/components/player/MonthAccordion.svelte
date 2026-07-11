<script lang="ts">
	import { locale, _ } from 'svelte-i18n';
	import * as Accordion from '$lib/components/ui/accordion/index.js';
	import PlayerGameCard from '$lib/components/PlayerGameCard.svelte';
	import { monthYear } from '$lib/date';
	import type { MonthGroup } from './monthGroups';
	import {
		ArrowRightIcon,
		CaretDownIcon,
		CaretUpIcon,
		TrendDownIcon,
		TrendUpIcon
	} from 'phosphor-svelte';

	interface Props {
		groups: MonthGroup[];
		/** Keys of the expanded month panels (bindable — the caller decides the default). */
		expandedMonths: string[];
	}

	let { groups, expandedMonths = $bindable() }: Props = $props();
</script>

<Accordion.Root type="multiple" bind:value={expandedMonths} class="space-y-3">
	{#each groups as group (group.key)}
		{@const expanded = expandedMonths.includes(group.key)}
		{@const isPositive = group.totalElo > 0 || (group.totalElo === 0 && group.rawElo > 0)}
		{@const isNegative = group.totalElo < 0 || (group.totalElo === 0 && group.rawElo < 0)}
		{@const eloLabel = isPositive
			? `+${Math.abs(group.totalElo)}`
			: isNegative
				? `-${Math.abs(group.totalElo)}`
				: '±0'}
		<Accordion.Item
			value={group.key}
			class="overflow-hidden rounded-2xl border border-border/50 bg-card not-last:border-b-0"
		>
			<Accordion.Trigger
				class="w-full items-center rounded-none px-4 py-4 transition-colors hover:bg-accent hover:no-underline [&_[data-slot=accordion-trigger-icon]]:hidden"
			>
				<div class="min-w-0 flex-1 text-left">
					<p class="text-2xs font-semibold tracking-widest text-muted-foreground uppercase">
						{$_('player.games_count', { values: { count: group.games.length } })}
					</p>
					<p class="mt-1 text-xl font-semibold tracking-tight">
						{monthYear(group.month, $locale) ?? $_('common.tbd')}
					</p>
				</div>
				<div class="flex shrink-0 items-center gap-3">
					{#if group.hasEloData}
						<span
							class="flex items-center gap-1 rounded-md border px-2 py-1 font-mono text-2xs font-semibold tabular-nums {isPositive
								? 'border-win text-win'
								: isNegative
									? 'border-loss text-loss'
									: 'border-current text-muted-foreground'}"
						>
							{eloLabel} ELO
							{#if isPositive}
								<TrendUpIcon size={10} weight="bold" />
							{:else if isNegative}
								<TrendDownIcon size={10} weight="bold" />
							{:else}
								<ArrowRightIcon size={10} weight="bold" />
							{/if}
						</span>
					{/if}
					{#if expanded}
						<CaretUpIcon size={20} class="text-muted-foreground" />
					{:else}
						<CaretDownIcon size={20} class="text-muted-foreground" />
					{/if}
				</div>
			</Accordion.Trigger>
			<Accordion.Content class="p-0">
				<div class="space-y-3 bg-background/50 px-4 py-4">
					{#each group.games as game (game.gameId)}
						<PlayerGameCard {game} />
					{/each}
				</div>
			</Accordion.Content>
		</Accordion.Item>
	{/each}
</Accordion.Root>
