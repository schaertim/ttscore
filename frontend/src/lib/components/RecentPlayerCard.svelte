<script lang="ts">
	import { XIcon } from 'phosphor-svelte';
	import PlayerAvatar from './PlayerAvatar.svelte';
	import ClassBadge from './ClassBadge.svelte';
	import { formatName } from '$lib/utils';
	import { removeRecentPlayer } from '$lib/recentPlayers';

	interface Props {
		id: string;
		fullName: string;
		classification?: string | null;
		onremove?: () => void;
	}

	let { id, fullName, classification, onremove }: Props = $props();

	const formatted = $derived(formatName(fullName));
	// Abbreviate to "F. Lastname" for compact display — same as FavoritePlayerCard
	const shortName = $derived(
		formatted.includes(' ')
			? `${formatted.split(' ')[0][0]}. ${formatted.split(' ').slice(1).join(' ')}`
			: formatted
	);

	function handleRemove(e: MouseEvent) {
		e.preventDefault();
		e.stopPropagation();
		removeRecentPlayer(id);
		onremove?.();
	}
</script>

<div class="relative w-32 shrink-0">
	<a
		href="/players/{id}"
		class="flex w-full flex-col items-center gap-2 rounded-2xl border border-border bg-card
		       p-4 transition-colors hover:bg-accent"
	>
		<PlayerAvatar {fullName} size="lg" />

		<p class="w-full truncate text-center text-xs leading-tight font-semibold">
			{shortName}
		</p>

		<ClassBadge {classification} />
	</a>

	<button
		onclick={handleRemove}
		class="absolute top-2 right-2 rounded-full p-0.5 text-foreground/40
		       transition-colors hover:text-foreground"
		aria-label="Remove from recently viewed"
	>
		<XIcon size="16" />
	</button>
</div>
