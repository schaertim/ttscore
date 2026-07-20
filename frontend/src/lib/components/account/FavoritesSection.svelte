<script lang="ts">
	import { enhance } from '$app/forms';
	import { goto } from '$app/navigation';
	import type { ActionResult } from '@sveltejs/kit';
	import {
		BellIcon,
		BellRingingIcon,
		StarIcon,
		TrashIcon,
		TrophyIcon,
		UserIcon,
		UsersThreeIcon
	} from 'phosphor-svelte';
	import { toast } from 'svelte-sonner';
	import { _ } from 'svelte-i18n';
	import { get } from 'svelte/store';
	import type { FollowResponse } from '$lib/api';
	import { formatName } from '$lib/utils';
	import IconButton from '$lib/components/IconButton.svelte';
	import SectionLabel from '$lib/components/SectionLabel.svelte';

	interface Props {
		follows: FollowResponse[];
	}

	let { follows }: Props = $props();

	// Mirrors NotifyButton's toggleEnhance: the backend caps free-tier notify toggles and reports
	// it as a form-action failure with reason "notify_pro" — surfaced as an upsell toast here too,
	// since this section hand-rolls its own form instead of reusing NotifyButton.
	function notifyEnhance() {
		return async ({ result, update }: { result: ActionResult; update(): Promise<void> }) => {
			if (result.type === 'failure' && result.data?.reason === 'notify_pro') {
				toast.error(get(_)('pro.notify_title'), {
					description: get(_)('pro.notify_desc'),
					action: { label: get(_)('pro.unlock'), onClick: () => goto('/pro') }
				});
			}
			await update();
		};
	}

	const followGroups = $derived(
		[
			{
				label: $_('common.leagues_label'),
				icon: TrophyIcon,
				items: follows.filter((f) => f.targetType === 'division_group')
			},
			{
				label: $_('common.teams_label'),
				icon: UsersThreeIcon,
				items: follows.filter((f) => f.targetType === 'team')
			},
			{
				label: $_('common.players_label'),
				icon: UserIcon,
				items: follows.filter((f) => f.targetType === 'player')
			}
		].filter((g) => g.items.length > 0)
	);

	function entityHref(targetType: string, targetId: string) {
		const segment =
			targetType === 'division_group' ? 'groups' : targetType === 'team' ? 'teams' : 'players';
		return `/${segment}/${targetId}`;
	}
</script>

{#if followGroups.length > 0}
	<section class="space-y-3">
		<SectionLabel label={$_('account.favorites')} icon={StarIcon} />
		{#each followGroups as group (group.label)}
			<div
				class="divide-y divide-border/50 overflow-hidden rounded-xl border border-border bg-card"
			>
				{#each group.items as item (item.id)}
					<div
						class="flex items-center justify-between px-4 py-3 transition-colors hover:bg-accent"
					>
						<a
							href={entityHref(item.targetType, item.targetId)}
							class="flex min-w-0 flex-1 items-center gap-3"
						>
							<group.icon size={18} class="shrink-0 text-muted-foreground" />
							<span class="truncate font-semibold"
								>{item.targetType === 'player'
									? formatName(item.targetName)
									: item.targetName}</span
							>
						</a>
						<div class="flex shrink-0 items-center gap-1 pl-2">
							<form method="POST" action="?/setNotify" use:enhance={notifyEnhance}>
								<input type="hidden" name="followId" value={item.id} />
								<input type="hidden" name="notify" value={String(!item.notify)} />
								<IconButton
									type="submit"
									tone={item.notify ? 'foreground' : 'muted'}
									ariaLabel={$_(
										item.notify ? 'common.notifications_off' : 'common.notifications_on'
									)}
								>
									{#if item.notify}
										<BellRingingIcon size={18} weight="fill" />
									{:else}
										<BellIcon size={18} />
									{/if}
								</IconButton>
							</form>
							<form method="POST" action="?/unfollow" use:enhance>
								<input type="hidden" name="followId" value={item.id} />
								<IconButton type="submit" ariaLabel={$_('common.unfollow')}>
									<TrashIcon size={18} />
								</IconButton>
							</form>
						</div>
					</div>
				{/each}
			</div>
		{/each}
	</section>
{/if}
