<script lang="ts">
	import type { PageData } from './$types';
	import { Skeleton } from '$lib/components/ui/skeleton/index.js';
	import PlayerCard from '$lib/components/PlayerCard.svelte';
	import MatchCard from '$lib/components/MatchCard.svelte';
	import FormPills from '$lib/components/FormPills.svelte';
	import { UsersThreeIcon, PingPongIcon } from 'phosphor-svelte';
	import BackButton from '$lib/components/BackButton.svelte';
	import PageTitle from '$lib/components/PageTitle.svelte';
	import FollowButton from '$lib/components/FollowButton.svelte';
	import NotifyButton from '$lib/components/NotifyButton.svelte';
	import SectionLabel from '$lib/components/SectionLabel.svelte';
	import StatTile from '$lib/components/StatTile.svelte';
	import ScoreLine from '$lib/components/ScoreLine.svelte';
	import { _ } from 'svelte-i18n';

	let { data }: { data: PageData } = $props();

	const record = $derived(data.team.record.split('-').map(Number));

	// Synced in an effect (not just initialised) so client-side navigation between
	// teams — which reuses this component — picks up the new team's follow state.
	let following = $state(false);
	let followId = $state<string | null>(null);
	let notify = $state(false);

	$effect.pre(() => {
		following = data.following;
		followId = data.followId;
		notify = data.notify;
	});
</script>

<div class="space-y-6">
	<header class="space-y-4">
		<div class="flex items-center justify-between">
			<BackButton />
			<div class="flex items-center">
				<FollowButton
					bind:following
					bind:followId
					bind:notify
					targetType="team"
					targetId={data.team.id}
					authenticated={!!data.user}
				/>
				<NotifyButton {following} {followId} bind:notify authenticated={!!data.user} />
			</div>
		</div>

		<div class="flex items-start justify-between gap-4">
			<div class="min-w-0">
				<PageTitle class="mb-1">{data.team.name}</PageTitle>
				<p class="text-sm text-muted-foreground">{data.team.groupName}</p>
			</div>
			{#if data.team.position > 0}
				<a
					href="/groups/{data.team.groupId}"
					class="shrink-0 font-mono text-6xl leading-none font-black text-muted-foreground/15 tabular-nums transition-colors hover:text-muted-foreground/40"
				>
					#{data.team.position}
				</a>
			{/if}
		</div>
	</header>

	<div class="grid grid-cols-2 gap-3">
		<StatTile label={$_('team.record')}>
			<ScoreLine
				segments={[
					{ value: record[0], tone: 'win' },
					{ value: record[1], tone: 'neutral' },
					{ value: record[2], tone: 'loss' }
				]}
			/>
		</StatTile>

		{#if data.team.lastResults.length > 0}
			<StatTile label={$_('team.last_5')}>
				<FormPills form={data.team.lastResults} size={20} class="flex-wrap" />
			</StatTile>
		{/if}
	</div>

	<section class="space-y-3">
		<SectionLabel label={$_('team.roster')} icon={UsersThreeIcon} />

		<div class="divide-y divide-border/50 overflow-hidden rounded-xl border border-border bg-card">
			{#await data.streamed.roster}
				{#each [1, 2, 3] as i (i)}
					<div class="flex items-center gap-3 px-4 py-3">
						<Skeleton class="h-9 w-9 shrink-0 rounded-full" />
						<div class="flex-1 space-y-2">
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
						classification={player.classification}
						wins={player.wins}
						losses={player.losses}
					/>
				{/each}
			{/await}
		</div>
	</section>

	<section class="space-y-3">
		<SectionLabel label={$_('team.match_history')} icon={PingPongIcon} />

		{#await data.streamed.matches}
			<Skeleton class="h-16 w-full rounded-xl" />
			<Skeleton class="h-16 w-full rounded-xl" />
			<Skeleton class="h-16 w-full rounded-xl" />
		{:then matches}
			<div class="space-y-3">
				{#each matches as match (match.id)}
					<MatchCard {match} perspectiveTeam={data.team.name} />
				{/each}
			</div>
		{/await}
	</section>
</div>
