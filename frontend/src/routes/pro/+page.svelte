<script lang="ts">
	import { _ } from 'svelte-i18n';
	import { page } from '$app/state';
	import PageTitle from '$lib/components/PageTitle.svelte';
	import BackButton from '$lib/components/BackButton.svelte';
	import { CheckIcon, SparkleIcon } from 'phosphor-svelte';

	const isPro = $derived(!!page.data.isPro);
	const checkoutStatus = $derived(page.data.checkoutStatus as string | null);

	const freeFeatures = $derived([
		$_('pro.feature_browse'),
		$_('pro.feature_profiles'),
		$_('pro.feature_follow_own'),
		$_('pro.feature_notify_own')
	]);
	const proFeatures = $derived([
		$_('pro.feature_h2h'),
		$_('pro.feature_career'),
		$_('pro.feature_follow_unlimited'),
		$_('pro.feature_notify_all')
	]);
</script>

<div class="space-y-6">
	<BackButton />

	<header class="space-y-1 text-center">
		<PageTitle>{$_('pro.page_title')}</PageTitle>
		<p class="text-sm text-muted-foreground">{$_('pro.page_subtitle')}</p>
	</header>

	{#if checkoutStatus === 'success' && !isPro}
		<div class="rounded-2xl border border-primary/20 bg-primary/5 p-4 text-center">
			<p class="text-sm font-semibold">{$_('pro.checkout_success')}</p>
		</div>
	{:else if checkoutStatus === 'cancelled' && !isPro}
		<div class="rounded-2xl border border-border bg-card p-4 text-center">
			<p class="text-sm text-muted-foreground">{$_('pro.checkout_cancelled')}</p>
		</div>
	{/if}

	{#if isPro}
		<div class="rounded-2xl border border-primary/20 bg-primary/5 p-6 text-center">
			<p class="text-base font-semibold">{$_('pro.already_pro')}</p>
		</div>
	{:else}
		<div class="grid grid-cols-2 gap-3">
			<form
				method="POST"
				action="?/checkout"
				class="flex flex-col items-center gap-2 rounded-2xl border border-border bg-card p-5 text-center"
			>
				<input type="hidden" name="plan" value="monthly" />
				<span class="text-xl font-black">{$_('pro.price_monthly')}</span>
				<button
					type="submit"
					class="mt-1 w-full rounded-full bg-primary px-4 py-2 text-xs font-semibold text-primary-foreground transition-opacity hover:opacity-90"
				>
					{$_('pro.subscribe')}
				</button>
			</form>
			<form
				method="POST"
				action="?/checkout"
				class="flex flex-col items-center gap-2 rounded-2xl border border-primary/30 bg-primary/5 p-5 text-center"
			>
				<input type="hidden" name="plan" value="yearly" />
				<span class="text-xl font-black">{$_('pro.price_yearly')}</span>
				<button
					type="submit"
					class="mt-1 w-full rounded-full bg-primary px-4 py-2 text-xs font-semibold text-primary-foreground transition-opacity hover:opacity-90"
				>
					{$_('pro.subscribe')}
				</button>
			</form>
		</div>
		<p class="text-center text-xs text-muted-foreground">{$_('pro.checkout_note')}</p>
	{/if}

	<div class="grid gap-4 sm:grid-cols-2">
		<section class="space-y-3 rounded-2xl border border-border bg-card p-5">
			<h2 class="text-sm font-semibold">{$_('pro.free_tier')}</h2>
			<ul class="space-y-2">
				{#each freeFeatures as feature (feature)}
					<li class="flex items-start gap-2 text-sm text-muted-foreground">
						<CheckIcon size="16" class="mt-0.5 shrink-0 text-muted-foreground" />
						{feature}
					</li>
				{/each}
			</ul>
		</section>

		<section class="space-y-3 rounded-2xl border border-primary/20 bg-primary/5 p-5">
			<h2 class="flex items-center gap-1.5 text-sm font-semibold">
				<SparkleIcon size="16" weight="fill" class="text-primary" />
				{$_('pro.pro_tier')}
			</h2>
			<ul class="space-y-2">
				{#each proFeatures as feature (feature)}
					<li class="flex items-start gap-2 text-sm">
						<CheckIcon size="16" weight="bold" class="mt-0.5 shrink-0 text-primary" />
						{feature}
					</li>
				{/each}
			</ul>
		</section>
	</div>
</div>
