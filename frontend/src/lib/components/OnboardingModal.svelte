<script lang="ts">
	import { onMount } from 'svelte';
	import { goto } from '$app/navigation';
	import { page } from '$app/state';
	import { TrophyIcon, ChartLineUpIcon, BellRingingIcon } from 'phosphor-svelte';
	import * as Dialog from '$lib/components/ui/dialog/index.js';
	import * as Carousel from '$lib/components/ui/carousel/index.js';
	import type { CarouselAPI } from '$lib/components/ui/carousel/context.js';
	import { Button } from '$lib/components/ui/button/index.js';
	import { _ } from 'svelte-i18n';
	import { analytics } from '$lib/analytics';

	const STORAGE_KEY = 'ttscore_onboarded';

	const slides = [
		{ icon: TrophyIcon, headline: 'onboarding.slide1_headline', body: 'onboarding.slide1_body' },
		{
			icon: ChartLineUpIcon,
			headline: 'onboarding.slide2_headline',
			body: 'onboarding.slide2_body'
		},
		{
			icon: BellRingingIcon,
			headline: 'onboarding.slide3_headline',
			body: 'onboarding.slide3_body'
		}
	];

	let open = $state(false);
	let api = $state<CarouselAPI>();
	let current = $state(0);

	const isLast = $derived(current === slides.length - 1);

	onMount(() => {
		if (!localStorage.getItem(STORAGE_KEY)) {
			open = true;
		}
	});

	// Keep `current` in sync with the carousel (swipe, drag, or button).
	$effect(() => {
		if (!api) return;
		current = api.selectedScrollSnap();
		const onSelect = () => (current = api!.selectedScrollSnap());
		api.on('select', onSelect);
		return () => api?.off('select', onSelect);
	});

	function persistOnboarded() {
		localStorage.setItem(STORAGE_KEY, '1');
	}

	function handleOpenChange(next: boolean) {
		// Persist whenever the dialog closes (X, backdrop, ESC, or skip)
		if (!next) persistOnboarded();
	}

	function signUp() {
		persistOnboarded();
		open = false;
		analytics.signupPrompted('onboarding_modal');
		goto(`/signin?redirectTo=${encodeURIComponent(page.url.pathname)}`);
	}
</script>

<Dialog.Root bind:open onOpenChange={handleOpenChange}>
	<Dialog.Content class="sm:max-w-md">
		<!-- Accessible name/description for the dialog (slides are decorative carousel items) -->
		<Dialog.Header class="sr-only">
			<Dialog.Title>{$_('onboarding.slide1_headline')}</Dialog.Title>
			<Dialog.Description>{$_('onboarding.slide1_body')}</Dialog.Description>
		</Dialog.Header>

		<Carousel.Root setApi={(a) => (api = a)} class="w-full">
			<Carousel.Content>
				{#each slides as slide (slide.headline)}
					{@const SlideIcon = slide.icon}
					<Carousel.Item>
						<div class="flex flex-col items-center gap-4 px-2 pt-2 text-center">
							<div class="rounded-2xl bg-primary/10 p-4">
								<SlideIcon size="32" class="text-primary" weight="fill" />
							</div>
							<div class="space-y-2">
								<h2 class="text-xl font-black tracking-tight">{$_(slide.headline)}</h2>
								<p class="text-sm leading-relaxed text-muted-foreground">
									{$_(slide.body)}
								</p>
							</div>
						</div>
					</Carousel.Item>
				{/each}
			</Carousel.Content>
		</Carousel.Root>

		<!-- actions -->
		<Dialog.Footer class="flex-col gap-2 sm:flex-col sm:space-x-0">
			{#if isLast}
				<Button onclick={signUp} class="w-full">{$_('onboarding.create_account')}</Button>
			{:else}
				<Button onclick={() => api?.scrollNext()} class="w-full">
					{$_('onboarding.next')}
				</Button>
			{/if}
			<Dialog.Close>
				{#snippet child({ props })}
					<Button variant="ghost" class="w-full" {...props}>
						{$_('onboarding.skip')}
					</Button>
				{/snippet}
			</Dialog.Close>
		</Dialog.Footer>
	</Dialog.Content>
</Dialog.Root>
