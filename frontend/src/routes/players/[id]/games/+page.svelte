<script lang="ts">
	import type { PageData } from './$types';
	import type { PlayerGame } from '$lib/api';
	import { Skeleton } from '$lib/components/ui/skeleton/index.js';
	import * as Accordion from '$lib/components/ui/accordion/index.js';
	import BackButton from '$lib/components/BackButton.svelte';
	import GameCard from '$lib/components/GameCard.svelte';
	import CaretDownIcon from 'phosphor-svelte/lib/CaretDownIcon';
	import CaretUpIcon from 'phosphor-svelte/lib/CaretUpIcon';
	import TrendUpIcon from 'phosphor-svelte/lib/TrendUpIcon';
	import TrendDownIcon from 'phosphor-svelte/lib/TrendDownIcon';

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
			const label = new Date(Number(year), Number(month) - 1).toLocaleDateString('de-CH', {
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
			<h1 class="mb-1.5 text-3xl leading-none font-black tracking-tighter wrap-break-word">
				{data.player.fullName}
			</h1>
			<p class="text-sm text-muted-foreground">Game History</p>
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
			<p class="py-8 text-center text-sm text-muted-foreground">No games found.</p>
		{:else}
			<Accordion.Root type="multiple" bind:value={expandedMonths} class="space-y-3">
				{#each groups as group (group.key)}
					{@const expanded = expandedMonths.includes(group.key)}
					<Accordion.Item
						value={group.key}
						class="overflow-hidden rounded-2xl border border-border/50 bg-card not-last:border-b-0"
					>
						<Accordion.Trigger
							class="w-full items-center px-5 py-4 hover:no-underline [&_[data-slot=accordion-trigger-icon]]:hidden"
						>
							<div class="min-w-0 flex-1 text-left">
								<p class="text-[10px] font-medium tracking-widest text-muted-foreground uppercase">
									{group.games.length}
									{group.games.length === 1 ? 'Game' : 'Games'}
								</p>
								<p class="mt-0.5 text-xl font-bold tracking-tight">{group.label}</p>
							</div>
							<div class="flex shrink-0 items-center gap-3">
								{#if group.totalElo !== 0}
									<span
										class="flex items-center gap-1 rounded px-2.5 py-1 text-xs font-black tabular-nums
										{group.totalElo > 0 ? 'bg-win/15 text-win' : 'bg-loss/15 text-loss'}"
									>
										{formatElo(group.totalElo)}
										{#if group.totalElo > 0}
											<TrendUpIcon size="12" weight="bold" />
										{:else}
											<TrendDownIcon size="12" weight="bold" />
										{/if}
									</span>
								{/if}
								{#if expanded}
									<CaretUpIcon size="16" class="shrink-0 text-muted-foreground" />
								{:else}
									<CaretDownIcon size="16" class="shrink-0 text-muted-foreground" />
								{/if}
							</div>
						</Accordion.Trigger>
						<Accordion.Content class="p-0">
							<div class="space-y-4 bg-background/30 px-4 py-4">
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
