<script lang="ts">
	import type { PageData } from './$types';
	import { goto, invalidate } from '$app/navigation';
	import { enhance } from '$app/forms';
	import { Button } from '$lib/components/ui/button/index.js';
	import { Input } from '$lib/components/ui/input/index.js';
	import { api, type Player, type FollowResponse } from '$lib/api';
	import { StarIcon, BellIcon, BellRingingIcon, SunIcon, MoonIcon, TrashIcon, UserIcon, UsersThreeIcon, TrophyIcon, PaintBrushHouseholdIcon, MagnifyingGlassIcon } from 'phosphor-svelte';
	import PlayerAvatar from '$lib/components/PlayerAvatar.svelte';
	import ClassBadge from '$lib/components/ClassBadge.svelte';
	import { theme } from '$lib/theme.svelte';
	import { _ } from 'svelte-i18n';
	import { formatName } from '$lib/utils';
	import SectionLabel from '$lib/components/SectionLabel.svelte';
	import PageTitle from '$lib/components/PageTitle.svelte';
	import { page } from '$app/state';
	import LanguageSwitcher from '$lib/components/LanguageSwitcher.svelte';
	import { subscribe, unsubscribe, getSubscription } from '$lib/push';

	let { data }: { data: PageData } = $props();

	let query = $state('');
	let results = $state<Player[]>([]);
	let searching = $state(false);
	let searchTimeout: ReturnType<typeof setTimeout>;

	const followGroups = $derived([
		{ label: $_('common.leagues_label'), icon: TrophyIcon, items: data.follows.filter((f: FollowResponse) => f.targetType === 'division_group') },
		{ label: $_('common.teams_label'), icon: UsersThreeIcon, items: data.follows.filter((f: FollowResponse) => f.targetType === 'team') },
		{ label: $_('common.players_label'), icon: UserIcon, items: data.follows.filter((f: FollowResponse) => f.targetType === 'player') },
	].filter(g => g.items.length > 0));

	function onInput() {
		clearTimeout(searchTimeout);
		results = [];
		if (!query.trim()) return;
		searchTimeout = setTimeout(async () => {
			searching = true;
			try {
				const res = await api.players.search(query, 0, 6);
				results = res.items;
			} finally {
				searching = false;
			}
		}, 300);
	}

	async function signOut() {
		await data.supabase.auth.signOut();
		await invalidate('supabase:auth');
		await goto('/');
	}

	let pushSubscribed = $state<boolean | null>(null);
	let pushLoading = $state(false);
	let pushUnsupported = $state(false);

	$effect(() => {
		if (!('serviceWorker' in navigator) || !('PushManager' in window)) {
			pushUnsupported = true;
			return;
		}
		getSubscription().then((sub) => { pushSubscribed = sub !== null; });
	});

	async function togglePush() {
		pushLoading = true;
		try {
			const { data: sessionData } = await data.supabase.auth.getSession();
			const token = sessionData.session?.access_token ?? '';
			if (pushSubscribed) {
				await unsubscribe(token);
				pushSubscribed = false;
			} else {
				const ok = await subscribe(token);
				if (ok) pushSubscribed = true;
			}
		} finally {
			pushLoading = false;
		}
	}

	function entityHref(targetType: string, targetId: string) {
		const segment = targetType === 'division_group' ? 'groups' : targetType === 'team' ? 'teams' : 'players';
		return `/${segment}/${targetId}`;
	}
</script>

<div class="space-y-6">
	<header class="space-y-4">
		<div>
			<p class="text-xs font-semibold tracking-widest text-muted-foreground uppercase mb-1">{$_("account.subtitle")}</p>
			<PageTitle>{$_("account.title")}</PageTitle>
		</div>
	</header>

	<section class="space-y-3">
		<SectionLabel label={$_("account.my_player")} icon={UserIcon} />

		{#if data.profile.homePlayerId}
			<a
				href="/players/{data.profile.homePlayerId}"
				class="flex items-center gap-3 rounded-xl border border-border bg-card px-4 py-3 transition-colors hover:bg-accent"
			>
				<PlayerAvatar fullName={data.profile.homePlayerName ?? ''} size="md" />
				<div class="min-w-0 flex-1">
					<div class="flex items-center gap-2">
						<p class="truncate text-sm font-semibold">{formatName(data.profile.homePlayerName) ?? 'Unknown player'}</p>
						{#if data.homePlayer?.liveClassification ?? data.homePlayer?.classification}
							<ClassBadge classification={data.homePlayer.liveClassification ?? data.homePlayer.classification} />
						{/if}
					</div>
					<p class="truncate text-2xs tracking-wide text-muted-foreground">{data.homePlayer?.currentClubName ?? '-'}</p>
				</div>
				<form method="POST" action="?/removeHomePlayer" use:enhance>
					<button
						type="submit"
						onclick={(e) => e.stopPropagation()}
						class="flex items-center justify-center rounded-full p-2 text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
						aria-label="Remove home player"
					>
						<TrashIcon size="18" />
					</button>
				</form>
			</a>
		{:else}
			{#if page.form?.error}
				<p class="text-sm text-destructive">{page.form.error}</p>
			{/if}

			<div class="space-y-3">
				<div class="relative">
					<MagnifyingGlassIcon
						size="16"
						class="absolute top-1/2 left-3 -translate-y-1/2 text-muted-foreground"
					/>
					<Input
						placeholder={$_("account.search_placeholder")}
						bind:value={query}
						oninput={onInput}
						class="w-full py-4 pl-9 text-base"
					/>
				</div>

				{#if results.length > 0}
					<div class="divide-y divide-border/50 overflow-hidden rounded-xl border border-border bg-card">
						{#each results as player (player.id)}
							<form
								method="POST"
								action="?/setHomePlayer"
								use:enhance={() => {
									return async ({ update }) => {
										query = '';
										results = [];
										await update();
									};
								}}
							>
								<input type="hidden" name="playerId" value={player.id} />
								<button
									type="submit"
									class="flex w-full items-center justify-between px-4 py-3 text-left transition-colors hover:bg-accent"
								>
									<span class="font-semibold">{formatName(player.fullName)}</span>
									<span class="text-xs text-muted-foreground">{player.currentClubName ?? ''}</span>
								</button>
							</form>
						{/each}
					</div>
				{/if}
			</div>
		{/if}
	</section>

	{#if followGroups.length > 0}
		<section class="space-y-3">
			<SectionLabel label={$_("account.favorites")} icon={StarIcon} />
			{#each followGroups as group (group.label)}
				<div class="divide-y divide-border/50 overflow-hidden rounded-xl border border-border bg-card">
					{#each group.items as item (item.id)}
						<div class="flex items-center justify-between px-4 py-3 transition-colors hover:bg-accent">
							<a
								href={entityHref(item.targetType, item.targetId)}
								class="flex min-w-0 flex-1 items-center gap-3"
							>
								<group.icon size="18" class="shrink-0 text-muted-foreground" />
								<span class="truncate font-semibold">{item.targetType === 'player' ? formatName(item.targetName) : item.targetName}</span>
							</a>
							<div class="flex shrink-0 items-center gap-1 pl-2">
								<form method="POST" action="?/setNotify" use:enhance>
									<input type="hidden" name="followId" value={item.id} />
									<input type="hidden" name="notify" value={String(!item.notify)} />
									<button
										type="submit"
										class="flex items-center justify-center rounded-full p-2 transition-colors hover:bg-muted {item.notify ? 'text-foreground' : 'text-muted-foreground'}"
										aria-label={item.notify ? 'Turn off notifications' : 'Turn on notifications'}
									>
										{#if item.notify}
											<BellRingingIcon size="18" weight="fill" />
										{:else}
											<BellIcon size="18" />
										{/if}
									</button>
								</form>
								<form method="POST" action="?/unfollow" use:enhance>
									<input type="hidden" name="followId" value={item.id} />
									<button
										type="submit"
										class="flex items-center justify-center rounded-full p-2 text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
										aria-label="Unfollow"
									>
										<TrashIcon size="18" />
									</button>
								</form>
							</div>
						</div>
					{/each}
				</div>
			{/each}
		</section>
	{/if}

	{#if !pushUnsupported}
		<section class="space-y-3">
			<SectionLabel label={$_("account.push_notifications")} icon={BellRingingIcon} />
			<button
				onclick={togglePush}
				disabled={pushLoading || pushSubscribed === null}
				class="flex w-full items-center justify-between rounded-xl border border-border bg-card px-4 py-3 transition-colors hover:bg-accent disabled:opacity-50"
			>
				<div class="flex min-w-0 flex-1 flex-col items-start gap-1">
					<span class="font-semibold">
						{$_(pushSubscribed ? 'account.push_enabled' : 'account.push_disabled')}
					</span>
					<span class="text-xs text-muted-foreground">
						{pushSubscribed
							? $_('account.push_enabled_desc')
							: $_('account.push_disabled_desc')}
					</span>
				</div>
				<BellRingingIcon
					size="20"
					weight={pushSubscribed ? 'fill' : 'regular'}
					class="m-1 shrink-0 text-muted-foreground"
				/>
			</button>
		</section>
	{/if}

	<section class="space-y-3">
		<SectionLabel label={$_("account.appearance")} icon={PaintBrushHouseholdIcon} />
		<button
			onclick={() => theme.toggle()}
			class="flex w-full items-center justify-between rounded-xl border border-border bg-card px-4 py-3 transition-colors hover:bg-accent"
		>
			<span class="font-semibold">{$_(theme.dark ? 'account.dark_mode' : 'account.light_mode')}</span>
			{#if theme.dark}
				<MoonIcon size="20" class="text-muted-foreground" />
			{:else}
				<SunIcon size="20" class="text-muted-foreground" />
			{/if}
		</button>
	</section>

	<LanguageSwitcher />

	<Button variant="destructive" onclick={signOut} class="w-full">{$_("account.sign_out")}</Button>
</div>
