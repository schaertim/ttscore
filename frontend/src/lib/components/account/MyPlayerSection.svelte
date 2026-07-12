<script lang="ts">
	import { invalidate } from '$app/navigation';
	import { enhance } from '$app/forms';
	import type { SupabaseClient } from '@supabase/supabase-js';
	import { TrashIcon, UserIcon } from 'phosphor-svelte';
	import { _ } from 'svelte-i18n';
	import type { Player } from '$lib/api';
	import { setHomePlayer } from '$lib/homePlayer';
	import { formatName } from '$lib/utils';
	import PlayerAvatar from '$lib/components/PlayerAvatar.svelte';
	import ClassBadge from '$lib/components/ClassBadge.svelte';
	import IconButton from '$lib/components/IconButton.svelte';
	import SectionLabel from '$lib/components/SectionLabel.svelte';
	import SetPlayerSearch from '$lib/components/SetPlayerSearch.svelte';

	interface Props {
		homePlayerId: string | null;
		homePlayerName: string | null;
		/** Full player record for the badge/club line; null while unset or unavailable. */
		homePlayer: Player | null;
		supabase: SupabaseClient;
	}

	let { homePlayerId, homePlayerName, homePlayer, supabase }: Props = $props();

	let settingPlayer = $state(false);

	async function handleSelectPlayer(player: Player) {
		settingPlayer = true;
		try {
			await setHomePlayer(supabase, player.id);
			await invalidate('supabase:auth');
		} finally {
			settingPlayer = false;
		}
	}
</script>

<section class="space-y-3">
	<SectionLabel label={$_('account.my_player')} icon={UserIcon} />

	{#if homePlayerId}
		<!-- The remove button sits next to the link, not inside it — nested interactive
		     elements are invalid HTML and confuse screen readers. -->
		<div
			class="flex items-center gap-3 rounded-xl border border-border bg-card px-4 py-3 transition-colors hover:bg-accent"
		>
			<a href="/players/{homePlayerId}" class="flex min-w-0 flex-1 items-center gap-3">
				<PlayerAvatar fullName={homePlayerName ?? ''} size="md" />
				<div class="min-w-0 flex-1">
					<div class="flex items-center gap-2">
						<p class="truncate text-sm font-semibold">{formatName(homePlayerName)}</p>
						{#if homePlayer?.liveClassification ?? homePlayer?.classification}
							<ClassBadge
								classification={homePlayer.liveClassification ?? homePlayer.classification}
							/>
						{/if}
					</div>
					<p class="truncate text-2xs tracking-wide text-muted-foreground">
						{homePlayer?.currentClubName ?? '—'}
					</p>
				</div>
			</a>
			<form method="POST" action="?/removeHomePlayer" use:enhance>
				<IconButton type="submit" ariaLabel={$_('common.remove')}>
					<TrashIcon size={18} />
				</IconButton>
			</form>
		</div>
	{:else}
		<SetPlayerSearch onSelect={handleSelectPlayer} disabled={settingPlayer} />
	{/if}
</section>
