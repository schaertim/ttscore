<script lang="ts">
	import { onMount } from 'svelte';
	import { goto } from '$app/navigation';
	import { fade, fly } from 'svelte/transition';
	import { TrophyIcon, ChartLineUpIcon, BellRingingIcon } from 'phosphor-svelte';
	import * as Card from '$lib/components/ui/card/index.js';
	import { _ } from 'svelte-i18n';

	const STORAGE_KEY = 'ttscore_onboarded';

	const slideIcons = [TrophyIcon, ChartLineUpIcon, BellRingingIcon];
	const slideKeys = [
		{ headline: 'onboarding.slide1_headline', body: 'onboarding.slide1_body' },
		{ headline: 'onboarding.slide2_headline', body: 'onboarding.slide2_body' },
		{ headline: 'onboarding.slide3_headline', body: 'onboarding.slide3_body' }
	];

	let visible = $state(false);
	let current = $state(0);

	onMount(() => {
		if (!localStorage.getItem(STORAGE_KEY)) {
			visible = true;
		}
	});

	function dismiss() {
		localStorage.setItem(STORAGE_KEY, '1');
		visible = false;
	}

	function next() {
		if (current < slideKeys.length - 1) {
			current++;
		}
	}

	function signUp() {
		dismiss();
		goto('/signin');
	}
</script>

{#if visible}
	<div
		class="fixed inset-0 z-50 flex items-end"
		role="dialog"
		aria-modal="true"
		aria-label="Welcome to ttscore"
		transition:fade={{ duration: 200 }}
	>
		<!-- backdrop -->
		<div
			class="absolute inset-0 bg-background/75 backdrop-blur-sm"
			onclick={dismiss}
			aria-hidden="true"
		></div>

		<!-- panel -->
		<div
			class="relative w-full"
			in:fly={{ y: 80, duration: 300, delay: 100 }}
			out:fly={{ y: 80, duration: 200 }}
		>
			<div class="mx-auto max-w-2xl px-4 pb-6">
				<Card.Root class="shadow-2xl">
					<Card.Content class="space-y-6 p-6">
						<!-- slide -->
						{#key current}
							{@const SlideIcon = slideIcons[current]}
							<div
								class="flex flex-col items-center gap-4 pt-2 text-center"
								in:fade={{ duration: 180 }}
							>
								<div class="rounded-2xl bg-primary/10 p-4">
									<SlideIcon size="32" class="text-primary" weight="fill" />
								</div>
								<div class="space-y-2">
									<h2 class="text-xl font-black tracking-tight">
										{$_(slideKeys[current].headline)}
									</h2>
									<p class="text-sm leading-relaxed text-muted-foreground">
										{$_(slideKeys[current].body)}
									</p>
								</div>
							</div>
						{/key}

						<!-- progress dots -->
						<div class="flex justify-center gap-2">
							{#each slideKeys as _, i}
								<div
									class="h-1.5 rounded-full transition-all duration-300
										{i === current
										? 'w-6 bg-primary'
										: 'w-1.5 bg-muted-foreground/25'}"
								></div>
							{/each}
						</div>

						<!-- actions -->
						<div class="flex flex-col gap-2">
							{#if current < slideKeys.length - 1}
								<button
									onclick={next}
									class="w-full rounded-xl bg-primary px-4 py-3 text-sm font-bold text-primary-foreground transition-opacity hover:opacity-90 active:opacity-75"
								>
									{$_('onboarding.next')}
								</button>
							{:else}
								<button
									onclick={signUp}
									class="w-full rounded-xl bg-primary px-4 py-3 text-sm font-bold text-primary-foreground transition-opacity hover:opacity-90 active:opacity-75"
								>
									{$_('onboarding.create_account')}
								</button>
							{/if}
							<button
								onclick={dismiss}
								class="w-full rounded-xl px-4 py-3 text-sm font-medium text-muted-foreground transition-colors hover:text-foreground"
							>
								{$_('onboarding.skip')}
							</button>
						</div>
					</Card.Content>
				</Card.Root>
			</div>
		</div>
	</div>
{/if}
