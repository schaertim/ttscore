<script lang="ts">
	import type { PageData } from './$types';
	import * as Card from '$lib/components/ui/card/index.js';
	import { Skeleton } from '$lib/components/ui/skeleton/index.js';
	import PlayerCard from '$lib/components/PlayerCard.svelte';
	import MatchCard from '$lib/components/MatchCard.svelte';
	import { CheckCircle, XCircle, MinusCircle } from 'phosphor-svelte';
	import BackButton from '$lib/components/BackButton.svelte';

	let { data }: { data: PageData } = $props();

	const [won, drawn, lost] = data.team.record.split('-').map(Number);
</script>

<div class="py-4 pb-20 space-y-8">

	<BackButton />

	<!-- Header -->
	<div class="px-1 flex items-start justify-between gap-4">
		<div class="space-y-1 min-w-0">
			<h1 class="text-3xl font-extrabold tracking-tight">{data.team.name}</h1>
			<p class="text-sm text-muted-foreground">{data.team.groupName}</p>
		</div>
		{#if data.team.position > 0}
			<span class="text-6xl font-black text-muted-foreground/15 leading-none shrink-0 tabular-nums">
				#{data.team.position}
			</span>
		{/if}
	</div>

	<!-- Stats -->
	<div class="grid grid-cols-2 gap-3">
		<Card.Root class="p-4">
			<p class="text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Record</p>
			<p class="text-xl font-black mt-1">
				<span class="text-win">{won}</span>
				<span class="text-muted-foreground/40 font-normal mx-0.5">–</span>
				<span class="text-muted-foreground">{drawn}</span>
				<span class="text-muted-foreground/40 font-normal mx-0.5">–</span>
				<span class="text-loss">{lost}</span>
			</p>
		</Card.Root>

		{#if data.team.lastResults.length > 0}
			<Card.Root class="p-4">
				<p class="text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Last 5</p>
				<div class="flex items-center gap-1 mt-1.5 flex-wrap">
					{#each data.team.lastResults.toReversed() as result}
						{#if result === 'W'}
							<CheckCircle weight="fill" class="w-5 h-5 text-win" />
						{:else if result === 'L'}
							<XCircle weight="fill" class="w-5 h-5 text-loss" />
						{:else}
							<MinusCircle weight="fill" class="w-5 h-5 text-muted-foreground/50" />
						{/if}
					{/each}
				</div>
			</Card.Root>
		{/if}
	</div>

	<!-- Roster -->
	<section class="space-y-2">
		<h2 class="text-[10px] font-black uppercase tracking-[0.2em] text-muted-foreground px-1">
			Team Roster
			{#await data.streamed.roster then roster}
				<span class="ml-2 normal-case tracking-normal font-medium text-muted-foreground/60">
					{roster.length} players
				</span>
			{/await}
		</h2>

		<div class="rounded-xl overflow-hidden border border-border bg-card divide-y divide-border/50">
			{#await data.streamed.roster}
				{#each [1, 2, 3] as i (i)}
					<div class="flex items-center gap-3 px-4 py-3">
						<Skeleton class="w-9 h-9 rounded-full shrink-0" />
						<div class="flex-1 space-y-1.5">
							<Skeleton class="h-3.5 w-32" />
							<Skeleton class="h-3 w-8" />
						</div>
						<Skeleton class="h-4 w-12" />
					</div>
				{/each}
			{:then roster}
				{#each roster as player (player.id)}
					<PlayerCard
						id={player.id}
						fullName={player.fullName}
						klass={player.klass}
						wins={player.wins}
						losses={player.losses}
					/>
				{/each}
			{/await}
		</div>
	</section>

	<!-- Match history -->
	<section class="space-y-2">
		<h2 class="text-[10px] font-black uppercase tracking-[0.2em] text-muted-foreground px-1">Match History</h2>

		{#await data.streamed.matches}
			<Skeleton class="h-16 w-full rounded-xl" />
			<Skeleton class="h-16 w-full rounded-xl" />
			<Skeleton class="h-16 w-full rounded-xl" />
		{:then matches}
			<div class="space-y-2">
				{#each matches as match (match.id)}
					<MatchCard {match} perspectiveTeam={data.team.name} />
				{/each}
			</div>
		{/await}
	</section>
</div>
