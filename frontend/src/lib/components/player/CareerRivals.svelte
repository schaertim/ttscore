<script lang="ts">
	import type { CareerRival } from '$lib/api';
	import * as Carousel from '$lib/components/ui/carousel/index.js';
	import PlayerTile from '$lib/components/PlayerTile.svelte';
	import ScoreLine from '$lib/components/ScoreLine.svelte';
	import { comparePlayers } from '$lib/h2h.svelte';
	import { ScalesIcon } from 'phosphor-svelte';
	import Autoplay from 'embla-carousel-autoplay';

	interface Props {
		rivals: CareerRival[];
		playerId: string;
	}

	let { rivals, playerId }: Props = $props();

	// Show the top 10 rivals as carousel cards.
	const topRivals = $derived(rivals.slice(0, 10));

	// Created once so the plugin instance (and its timer) isn't torn down on every render.
	const autoplay = Autoplay({ delay: 4000, stopOnInteraction: false, stopOnMouseEnter: true });
</script>

<Carousel.Root opts={{ align: 'start', loop: true }} plugins={[autoplay]} class="w-full">
	<Carousel.Content class="-ms-3">
		{#each topRivals as rival (rival.opponentId)}
			<Carousel.Item class="basis-1/3 ps-3 md:basis-1/5">
				<PlayerTile
					fullName={rival.opponentName}
					classification={rival.opponentClass}
					onclick={() => comparePlayers(playerId, rival.opponentId)}
					cornerPosition="bottom-right"
				>
					{#snippet content()}
						<ScoreLine
							class="text-sm"
							segments={[
								{ value: rival.wins, tone: 'win' },
								{ value: rival.losses, tone: 'loss' }
							]}
						/>
					{/snippet}
					{#snippet corner()}
						<ScalesIcon size={16} class="text-muted-foreground/40" />
					{/snippet}
				</PlayerTile>
			</Carousel.Item>
		{/each}
	</Carousel.Content>
</Carousel.Root>
