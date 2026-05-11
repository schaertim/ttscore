<script lang="ts">
	import type { PageData } from './$types';
	import * as Card from '$lib/components/ui/card/index.js';
	import { Skeleton } from '$lib/components/ui/skeleton/index.js';
	import PlayerCard from '$lib/components/PlayerCard.svelte';
	import MatchCard from '$lib/components/MatchCard.svelte';
	import { CheckCircle, XCircle, MinusCircle, UsersThree, PingPong } from 'phosphor-svelte';
	import BackButton from '$lib/components/BackButton.svelte';
	import FavoriteButton from '$lib/components/FavoriteButton.svelte';
	import NotifyButton from '$lib/components/NotifyButton.svelte';
	import SectionLabel from '$lib/components/SectionLabel.svelte';

	let { data }: { data: PageData } = $props();

	const [won, drawn, lost] = data.team.record.split('-').map(Number);
	let favorited = $state(data.favorited);
	let favoriteId = $state(data.favoriteId);
	let notifying = $state(data.notifying);
	let notifyId = $state(data.notifyId);
</script>

<div class="space-y-8 py-4 pb-20">
	<div class="mb-4 flex items-center justify-between px-1">
		<BackButton class="mb-0" />
		{#if data.user}
			<div class="flex items-center gap-0.5">
				<FavoriteButton bind:favorited bind:favoriteId targetType="team" targetId={data.team.id} />
				<NotifyButton bind:notifying bind:notifyId targetType="team" targetId={data.team.id} />
			</div>
		{/if}
	</div>

	<div class="flex items-start justify-between gap-4 px-1">
		<div class="min-w-0 space-y-1">
			<h1 class="text-3xl font-black tracking-tight">{data.team.name}</h1>
			<p class="text-sm text-muted-foreground">{data.team.groupName}</p>
		</div>
		{#if data.team.position > 0}
			<span class="shrink-0 text-6xl leading-none font-black text-muted-foreground/15 tabular-nums">
				#{data.team.position}
			</span>
		{/if}
	</div>

	<div class="grid grid-cols-2 gap-3">
		<Card.Root class="p-4">
			<p class="text-[10px] font-medium tracking-widest text-muted-foreground uppercase">Record</p>
			<p class="mt-1 text-xl font-black">
				<span class="text-win">{won}</span>
				<span class="mx-0.5 font-normal text-muted-foreground/40">–</span>
				<span class="text-muted-foreground">{drawn}</span>
				<span class="mx-0.5 font-normal text-muted-foreground/40">–</span>
				<span class="text-loss">{lost}</span>
			</p>
		</Card.Root>

		{#if data.team.lastResults.length > 0}
			<Card.Root class="p-4">
				<p class="text-[10px] font-medium tracking-widest text-muted-foreground uppercase">
					Last 5
				</p>
				<div class="mt-1.5 flex flex-wrap items-center gap-1">
					{#each data.team.lastResults.toReversed() as result}
						{#if result === 'W'}
							<CheckCircle weight="fill" class="h-5 w-5 text-win" />
						{:else if result === 'L'}
							<XCircle weight="fill" class="h-5 w-5 text-loss" />
						{:else}
							<MinusCircle weight="fill" class="h-5 w-5 text-muted-foreground/50" />
						{/if}
					{/each}
				</div>
			</Card.Root>
		{/if}
	</div>

	<section class="space-y-2">
		<SectionLabel label="Team Roster" icon={UsersThree} class="px-1" />

		<div class="divide-y divide-border/50 overflow-hidden rounded-xl border border-border bg-card">
			{#await data.streamed.roster}
				{#each [1, 2, 3] as i (i)}
					<div class="flex items-center gap-3 px-4 py-3">
						<Skeleton class="h-9 w-9 shrink-0 rounded-full" />
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

	<section class="space-y-2">
		<SectionLabel label="Match History" icon={PingPong} class="px-1" />

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
