<script lang="ts">
	import { enhance } from '$app/forms';
	import { Star } from 'phosphor-svelte';
	import PlayerAvatar from './PlayerAvatar.svelte';
	import KlassBadge from './KlassBadge.svelte';

	interface Props {
		id: string;
		fullName: string;
		klass?: string | null;
		currentElo?: number | null;
		favoriteId: string;
		onunfavorite?: () => void;
	}

	let { id, fullName, klass, currentElo, favoriteId, onunfavorite }: Props = $props();

	// "Firstname Lastname" → "F. Lastname"
	const shortName = fullName.includes(' ')
		? `${fullName.split(' ')[0][0]}. ${fullName.split(' ').slice(1).join(' ')}`
		: fullName;
</script>

<div class="relative w-32 shrink-0">
	<a
		href="/players/{id}"
		class="flex w-full flex-col items-center gap-2 rounded-2xl border border-border bg-card
		       p-3 pt-5 transition-colors hover:bg-accent"
	>
		<PlayerAvatar {fullName} size="lg" />

		<p class="w-full truncate text-center text-xs font-semibold leading-tight">
			{shortName}
		</p>

		<div class="flex flex-col items-center gap-0.5">
			<KlassBadge {klass} />
			{#if currentElo}
				<span class="text-xs font-black tabular-nums">{currentElo}</span>
			{/if}
		</div>
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
			class="absolute top-1.5 right-1.5 rounded-full p-0.5 text-foreground/60
			       transition-colors hover:text-foreground"
			aria-label="Remove from favourites"
		>
			<Star weight="fill" class="size-4.5" />
		</button>
	</form>
</div>
