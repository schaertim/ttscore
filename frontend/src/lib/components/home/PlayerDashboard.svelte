<script lang="ts">
	import type { Player, PlayerGame, NextMatch, FollowResponse } from '$lib/api';
	import { Skeleton } from '$lib/components/ui/skeleton/index.js';
	import * as Card from '$lib/components/ui/card/index.js';
	import KlassBadge from '$lib/components/KlassBadge.svelte';
	import SectionLabel from '$lib/components/SectionLabel.svelte';
	import GameCard from '$lib/components/GameCard.svelte';
	import MatchCard from '$lib/components/MatchCard.svelte';
	import FollowedEntityRow from '$lib/components/home/FollowedEntityRow.svelte';
	import { ClockCounterClockwise, CalendarBlank, Bell, GearSix } from 'phosphor-svelte';

	interface Props {
		player: Player | null;
		streamed: {
			recentMatches: Promise<PlayerGame[]>;
			nextMatch: Promise<NextMatch | null>;
			follows: Promise<FollowResponse[]>;
		};
	}

	let { player, streamed }: Props = $props();
</script>

<div class="space-y-6 py-4">
	<!-- ELO Badge -->
	{#if player}
		<a href="/players/{player.id}" class="block">
			<Card.Root class="border-border/50">
				<Card.Content class="p-5">
					<div class="flex items-start justify-between gap-4">
						<div class="min-w-0">
							<p class="text-xs font-bold tracking-widest text-muted-foreground uppercase">
								My Player
							</p>
							<h1 class="mt-1 text-2xl font-black leading-none tracking-tighter break-words">
								{player.fullName}
							</h1>
							{#if player.currentClubName}
								<p class="mt-1.5 text-sm text-muted-foreground">{player.currentClubName}</p>
							{/if}
						</div>
						<div class="flex shrink-0 flex-col items-end gap-2">
							{#if player.currentElo}
								<span class="text-4xl leading-none font-black tabular-nums">{player.currentElo}</span>
								<span class="text-[10px] font-bold tracking-widest text-muted-foreground uppercase">ELO</span>
							{/if}
							<KlassBadge klass={player.klass} />
						</div>
					</div>
				</Card.Content>
			</Card.Root>
		</a>
	{/if}

	<!-- Next Match -->
	<section class="space-y-3">
		<SectionLabel label="Next Match" icon={CalendarBlank} class="px-1" />
		{#await streamed.nextMatch}
			<Skeleton class="h-14 w-full rounded-xl" />
		{:then nextMatch}
			{#if nextMatch}
				<div>
					<p class="mb-1.5 px-1 text-[10px] font-bold tracking-widest text-muted-foreground uppercase">
						{nextMatch.groupName}
					</p>
					<MatchCard
						match={{
							id: nextMatch.matchId,
							homeTeam: nextMatch.homeTeam,
							awayTeam: nextMatch.awayTeam,
							homeScore: null,
							awayScore: null,
							round: nextMatch.round,
							playedAt: nextMatch.playedAt,
							status: 'SCHEDULED'
						}}
						perspectiveTeam={nextMatch.playerTeamName}
					/>
				</div>
			{:else}
				<p class="text-sm text-muted-foreground px-1">No upcoming matches scheduled.</p>
			{/if}
		{/await}
	</section>

	<!-- Recent Results -->
	<section class="space-y-3">
		<SectionLabel label="Recent Results" icon={ClockCounterClockwise} class="px-1" />
		{#await streamed.recentMatches}
			<div class="space-y-2">
				{#each [1, 2, 3, 4, 5] as i (i)}
					<Skeleton class="h-16 w-full rounded-xl" />
				{/each}
			</div>
		{:then matches}
			{#if matches.length === 0}
				<p class="text-sm text-muted-foreground px-1">No recent games found.</p>
			{:else}
				<div class="space-y-2">
					{#each matches.slice(0, 5) as game (game.gameId)}
						<GameCard mode="player" {game} />
					{/each}
				</div>
				{#if matches.length > 5 && player}
					<a
						href="/players/{player.id}/games"
						class="block pt-1 text-center text-xs font-bold tracking-widest text-muted-foreground uppercase hover:text-foreground"
					>
						Show Full History →
					</a>
				{/if}
			{/if}
		{/await}
	</section>

	<!-- Following -->
	{#await streamed.follows then follows}
		{#if follows.length > 0}
			<section class="space-y-3">
				<SectionLabel label="Following" icon={Bell} class="px-1" />
				<div class="space-y-3">
					{#each follows as follow (follow.id)}
						<FollowedEntityRow {follow} />
					{/each}
				</div>
			</section>
		{/if}
	{/await}

	<!-- Quick Links -->
	<section class="space-y-3">
		<SectionLabel label="Settings" icon={GearSix} class="px-1" />
		<a
			href="/account"
			class="flex items-center justify-between rounded-xl border border-border bg-card px-4 py-3 text-sm font-semibold hover:bg-accent"
		>
			Manage player & follows
		</a>
	</section>
</div>
