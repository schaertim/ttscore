<script lang="ts">
	import { Input } from '$lib/components/ui/input/index.js';
	import { MagnifyingGlassIcon } from 'phosphor-svelte';
	import { api, type Player } from '$lib/api';
	import { debounce } from '$lib/debounce';
	import { formatName } from '$lib/utils';
	import { _ } from 'svelte-i18n';

	interface Props {
		/** Called when the user picks a player from the results. */
		onSelect: (player: Player) => void | Promise<void>;
		/** Disable interaction while a selection is being processed. */
		disabled?: boolean;
	}

	let { onSelect, disabled = false }: Props = $props();

	let query = $state('');
	let results = $state<Player[]>([]);

	const runSearch = debounce(async () => {
		const res = await api.players.search(query, 0, 6);
		results = res.items;
	});

	function onInput() {
		results = [];
		if (!query.trim()) {
			runSearch.cancel();
			return;
		}
		runSearch();
	}

	async function select(player: Player) {
		await onSelect(player);
		query = '';
		results = [];
	}
</script>

<div class="space-y-3">
	<div class="relative">
		<MagnifyingGlassIcon
			size={16}
			class="absolute top-1/2 left-3 -translate-y-1/2 text-muted-foreground"
		/>
		<Input
			placeholder={$_('account.search_placeholder')}
			bind:value={query}
			oninput={onInput}
			{disabled}
			class="w-full py-4 pl-9 text-base"
		/>
	</div>

	{#if results.length > 0}
		<div class="divide-y divide-border/50 overflow-hidden rounded-xl border border-border bg-card">
			{#each results as player (player.id)}
				<button
					type="button"
					{disabled}
					onclick={() => select(player)}
					class="flex w-full items-center justify-between px-4 py-3 text-left transition-colors hover:bg-accent disabled:opacity-50"
				>
					<span class="font-semibold">{formatName(player.fullName)}</span>
					<span class="text-xs text-muted-foreground">{player.currentClubName ?? ''}</span>
				</button>
			{/each}
		</div>
	{/if}
</div>
