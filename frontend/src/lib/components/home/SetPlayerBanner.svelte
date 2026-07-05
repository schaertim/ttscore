<script lang="ts">
	import { onMount } from 'svelte';
	import { invalidate } from '$app/navigation';
	import { UserCirclePlusIcon, XIcon } from 'phosphor-svelte';
	import type { SupabaseClient } from '@supabase/supabase-js';
	import type { Player } from '$lib/api';
	import { setHomePlayer } from '$lib/homePlayer';
	import SetPlayerSearch from '$lib/components/SetPlayerSearch.svelte';
	import { _ } from 'svelte-i18n';

	interface Props {
		supabase: SupabaseClient;
	}

	let { supabase }: Props = $props();

	const STORAGE_KEY = 'ttscore_set_player_banner_dismissed';

	let visible = $state(false);
	let submitting = $state(false);
	let error = $state('');

	onMount(() => {
		if (!localStorage.getItem(STORAGE_KEY)) {
			visible = true;
		}
	});

	function dismiss() {
		localStorage.setItem(STORAGE_KEY, '1');
		visible = false;
	}

	async function handleSelect(player: Player) {
		submitting = true;
		error = '';
		try {
			await setHomePlayer(supabase, player.id);
			// Refresh layout data so hasHomePlayer flips to true and this banner
			// (rendered only while no home player is set) disappears.
			await invalidate('supabase:auth');
		} catch (e) {
			error = e instanceof Error ? e.message : String(e);
		} finally {
			submitting = false;
		}
	}
</script>

{#if visible}
	<!-- Container styling mirrors InfoItem's `primary` variant; kept as a div (not an
	     InfoItem) because it embeds a search field rather than a single clickable row. -->
	<div class="relative rounded-2xl border border-primary/20 bg-primary/5 p-4">
		<button
			type="button"
			onclick={dismiss}
			aria-label="Dismiss"
			class="absolute top-3 right-3 z-10 rounded-md p-1 text-muted-foreground transition-colors hover:bg-primary/10 hover:text-foreground"
		>
			<XIcon size="16" />
		</button>
		<div class="flex items-start gap-4">
			<div class="shrink-0 rounded-full bg-primary/10 p-2">
				<UserCirclePlusIcon size="20" class="text-primary" />
			</div>
			<div class="min-w-0 flex-1 pr-6">
				<p class="text-sm font-semibold">{$_('set_player.banner_title')}</p>
				<p class="mt-0.5 mb-3 text-xs text-muted-foreground">{$_('set_player.banner_desc')}</p>

				<SetPlayerSearch onSelect={handleSelect} disabled={submitting} />

				{#if error}
					<p class="mt-2 text-sm text-destructive">{error}</p>
				{/if}
			</div>
		</div>
	</div>
{/if}
