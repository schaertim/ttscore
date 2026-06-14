<script lang="ts">
	import type { PageData } from './$types';
	import type { PlayerGame } from '$lib/api';
	import { Skeleton } from '$lib/components/ui/skeleton/index.js';
	import * as Accordion from '$lib/components/ui/accordion/index.js';
	import BackButton from '$lib/components/BackButton.svelte';
	import GameCard from '$lib/components/GameCard.svelte';
	import PageTitle from '$lib/components/PageTitle.svelte';
	import CaretDownIcon from 'phosphor-svelte/lib/CaretDownIcon';
	import CaretUpIcon from 'phosphor-svelte/lib/CaretUpIcon';
	import TrendUpIcon from 'phosphor-svelte/lib/TrendUpIcon';
	import TrendDownIcon from 'phosphor-svelte/lib/TrendDownIcon';
	import { locale, _ } from 'svelte-i18n';
	import { formatName } from '$lib/utils';

	let { data }: { data: PageData } = $props();

	type MonthGroup = {
		key: string;
		label: string;
		games: PlayerGame[];
		wins: number;
		totalElo: number;
	};

	function groupByMonth(games: PlayerGame[]): MonthGroup[] {
		const map = new Map<string, PlayerGame[]>();
		for (const game of games) {
			const date = game.playedAt ? new Date(game.playedAt) : null;
			const key = date
				? `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`
				: 'unknown';
			if (!map.has(key)) map.set(key, []);
			map.get(key)!.push(game);
		}
		return Array.from(map.entries()).map(([key, monthGames]) => {
			const [year, month] = key.split('-');
			const label = new Date(Number(year), Number(month) - 1).toLocaleDateString($locale ?? 'de', {
				month: 'long',
				year: 'numeric'
			});
			const played = monthGames.filter((g) => g.result !== 'NOT_PLAYED');
			const wins = played.filter(
				(g) =>
					(g.result === 'HOME' && g.playerSide === 'home') ||
					(g.result === 'AWAY' && g.playerSide === 'away')
			).length;
			const totalElo = played.reduce((sum, g) => sum + Math.round(g.eloDelta ?? 0), 0);
			return { key, label, games: monthGames, wins, totalElo };
		});
	}

	function formatElo(delta: number): string {
		return delta >= 0 ? `+${delta} ELO` : `${delta} ELO`;
	}

	let expandedMonths = $state<string[]>([]);

	$effect(() => {
		data.streamed.matches.then((matches) => {
			const groups = groupByMonth(matches);
			if (groups.length > 0) {
				expandedMonths = [groups[0].key];
			}
		});
	});
</script>

<div class="space-y-6">
	<header class="space-y-4">
		<BackButton />
		<div>
			<PageTitle class="mb-1">{$_('player.game_history')}</PageTitle>
			<p class="text-sm text-muted-foreground">{formatName(data.player.fullName)}</p>
		</div>
	</header>

	{#await data.streamed.matches}
		<div class="space-y-3">
			{#each [1, 2, 3] as _}
				<Skeleton class="h-16 w-full rounded-2xl" />
			{/each}
		</div>
	{:then matches}
		{@const groups = groupByMonth(matches)}

		{#if groups.length === 0}
			<p class="py-12 text-center text-sm text-muted-foreground">{$_('player.no_games')}</p>
		{:else}
			<Accordion.Root type="multiple" bind:value={expandedMonths} class="space-y-3">
				{#each groups as group (group.key)}
					{@const expanded = expandedMonths.includes(group.key)}
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
								<p class="mt-1 text-xl font-semibold tracking-tight">{group.label}</p>
							</div>
							<div class="flex shrink-0 items-center gap-3">
								{#if group.totalElo !== 0}
									<span class="flex items-center gap-1 rounded-md border border-current px-2 py-1 text-2xs font-semibold tabular-nums {group.totalElo > 0 ? 'text-win' : 'text-loss'}">
										{group.totalElo > 0 ? `+${group.totalElo}` : group.totalElo} ELO
										{#if group.totalElo > 0}
											<TrendUpIcon size="9" weight="bold" />
										{:else}
											<TrendDownIcon size="9" weight="bold" />
										{/if}
									</span>
								{/if}
								{#if expanded}
									<CaretUpIcon size="20" class="text-muted-foreground" />
								{:else}
									<CaretDownIcon size="20" class="text-muted-foreground" />
								{/if}
							</div>
						</Accordion.Trigger>
						<Accordion.Content class="p-0">
							<div class="space-y-3 bg-background/50 px-4 py-4">
								{#each group.games as game (game.gameId)}
									<GameCard mode="player" {game} />
								{/each}
							</div>
						</Accordion.Content>
					</Accordion.Item>
				{/each}
			</Accordion.Root>
		{/if}
	{/await}
</div>
