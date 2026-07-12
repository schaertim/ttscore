<script lang="ts">
	import { _ } from 'svelte-i18n';
	import type { Player } from '$lib/api';
	import { Spinner } from '$lib/components/ui/spinner/index.js';
	import BackButton from '$lib/components/BackButton.svelte';
	import ClassBadge from '$lib/components/ClassBadge.svelte';
	import FollowButton from '$lib/components/FollowButton.svelte';
	import NotifyButton from '$lib/components/NotifyButton.svelte';
	import InfoItem from '$lib/components/InfoItem.svelte';
	import PageTitle from '$lib/components/PageTitle.svelte';
	import { compareWithMe } from '$lib/h2h.svelte';
	import { formatName } from '$lib/utils';
	import { ScalesIcon } from 'phosphor-svelte';

	interface Props {
		player: Player;
		/** Shows the live spinner under the ELO while the on-demand click-tt sync runs. */
		syncing: boolean;
		/** Whether the signed-in user has a home player other than this one. */
		canCompare: boolean;
		authenticated: boolean;
		following: boolean;
		followId: string | null;
		notify: boolean;
	}

	let {
		player,
		syncing,
		canCompare,
		authenticated,
		following = $bindable(),
		followId = $bindable(),
		notify = $bindable()
	}: Props = $props();

	// Class/ELO from the player's *current* rating (live where available) — match rows
	// keep their historical classes.
	const currentClass = $derived(player.liveClassification ?? player.classification);
	const displayElo = $derived(player.liveElo ?? player.currentElo);
</script>

<header class="space-y-4">
	<div class="flex items-center justify-between">
		<BackButton />
		<div class="flex items-center">
			<FollowButton
				bind:following
				bind:followId
				bind:notify
				targetType="player"
				targetId={player.id}
				{authenticated}
			/>
			<NotifyButton {following} {followId} bind:notify {authenticated} />
		</div>
	</div>

	<div class="flex items-start justify-between gap-4">
		<div class="min-w-0">
			<div class="mb-1 flex items-start gap-2">
				<PageTitle class="wrap-break-word">
					{formatName(player.fullName)}
				</PageTitle>
				<ClassBadge classification={currentClass} size="lg" class="mt-0.5 shrink-0" />
			</div>
			<p class="text-sm text-muted-foreground">
				{player.currentClubName ?? $_('player.no_club')}
			</p>
		</div>

		<div class="flex shrink-0 flex-col items-end gap-1">
			{#if displayElo}
				<span class="font-mono text-4xl leading-none font-black tabular-nums">{displayElo}</span>
				{#if syncing}
					<span class="flex items-center gap-1 text-xs tracking-widest text-muted-foreground">
						<Spinner class="size-3.5" aria-label={$_('player.syncing')} />
						{$_('player.syncing')}
					</span>
				{:else}
					<span class="text-xs tracking-widest text-muted-foreground uppercase"> ELO </span>
				{/if}
			{/if}
		</div>
	</div>

	{#if canCompare}
		<InfoItem
			variant="muted"
			size="sm"
			icon={ScalesIcon}
			title={$_('h2h.compare_with_me')}
			onclick={() => compareWithMe(player.id)}
		/>
	{/if}
</header>
