<script lang="ts">
	import { enhance } from '$app/forms';
	import { StarIcon } from 'phosphor-svelte';
	import PlayerAvatar from './PlayerAvatar.svelte';
	import ClassBadge from './ClassBadge.svelte';
	import { formatName } from '$lib/utils';

	interface Props {
		id: string;
		fullName: string;
		classification?: string | null;
		favoriteId: string;
		onunfavorite?: () => void;
	}

	let { id, fullName, classification, favoriteId, onunfavorite }: Props = $props();

	const formatted = $derived(formatName(fullName));
	// Abbreviate to "F. Lastname" for compact display
	const shortName = $derived(formatted.includes(' ')
		? `${formatted.split(' ')[0][0]}. ${formatted.split(' ').slice(1).join(' ')}`
		: formatted);
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

	<form
		method="POST"
		action="?/unfavorite"
		use:enhance={() => {
			onunfavorite?.();
			return async ({ update }) => {
				await update({ reset: false, invalidateAll: false });
			};
		}}
	>
		<input type="hidden" name="favoriteId" value={favoriteId} />
		<button
			type="submit"
			onclick={(e) => e.stopPropagation()}
			class="absolute top-2 right-2 rounded-full p-1 text-foreground/60
			       transition-colors hover:text-foreground"
			aria-label="Remove from favourites"
		>
			<StarIcon weight="fill" size="16" />
		</button>
	</form>
</div>
