<script lang="ts">
	import { enhance } from '$app/forms';
	import { Star } from 'phosphor-svelte';
	import PlayerAvatar from './PlayerAvatar.svelte';
	import ClassBadge from './ClassBadge.svelte';

	interface Props {
		id: string;
		fullName: string;
		klass?: string | null;
		favoriteId: string;
		onunfavorite?: () => void;
	}

	let { id, fullName, klass, favoriteId, onunfavorite }: Props = $props();

	// "Firstname Lastname" â†’ "F. Lastname"
	const shortName = fullName.includes(' ')
		? `${fullName.split(' ')[0][0]}. ${fullName.split(' ').slice(1).join(' ')}`
		: fullName;
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

		<ClassBadge {klass} />
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
			class="absolute top-2 right-2 rounded-full p-0.5 text-foreground/60
			       transition-colors hover:text-foreground"
			aria-label="Remove from favourites"
		>
			<Star weight="fill" size={16} />
		</button>
	</form>
</div>
