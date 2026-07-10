<script lang="ts">
	import { _ } from 'svelte-i18n';
	import { page } from '$app/state';
	import { invalidateAll } from '$app/navigation';
	import PageTitle from '$lib/components/PageTitle.svelte';
	import BackButton from '$lib/components/BackButton.svelte';
	import { classColorVar } from '$lib/utils';
	import {
		SparkleIcon,
		TargetIcon,
		BinocularsIcon,
		ChartLineUpIcon,
		StarIcon
	} from 'phosphor-svelte';

	const isPro = $derived(!!page.data.isPro);
	const checkoutStatus = $derived(page.data.checkoutStatus as string | null);
	// Stripe only redirects to the success URL after payment completes, so show the
	// Pro state right away; the webhook-driven isPro flag catches up via the poll below.
	const showAsPro = $derived(isPro || checkoutStatus === 'success');
	const accent = $derived(classColorVar(page.data.homePlayerClassification));

	// isPro flips when the Stripe webhook lands — poll briefly so paywalls across the
	// app unlock without a manual refresh.
	$effect(() => {
		if (checkoutStatus !== 'success' || isPro) return;
		const poll = setInterval(() => invalidateAll(), 3000);
		const stop = setTimeout(() => clearInterval(poll), 30000);
		return () => {
			clearInterval(poll);
			clearTimeout(stop);
		};
	});

	const proFeatures = $derived([
		{
			icon: TargetIcon,
			title: $_('pro.feature_h2h_title'),
			desc: $_('pro.feature_h2h_desc')
		},
		{
			icon: BinocularsIcon,
			title: $_('pro.feature_preview_title'),
			desc: $_('pro.feature_preview_desc')
		},
		{
			icon: ChartLineUpIcon,
			title: $_('pro.feature_career_title'),
			desc: $_('pro.feature_career_desc')
		},
		{
			icon: StarIcon,
			title: $_('pro.feature_follow_notify_title'),
			desc: $_('pro.feature_follow_notify_desc')
		}
	]);
</script>

<div class="space-y-4">
	{#if !showAsPro}
		<BackButton />
	{/if}

	<!-- Accent is the home player's classification colour (primary when none is set),
	     like HomeHero. -->
	<div
		class="relative overflow-hidden rounded-2xl border border-border/50 bg-card px-6 py-8 text-center"
		style="background-image: radial-gradient(circle at 50% -10%, color-mix(in srgb, {accent} 28%, transparent) 0%, color-mix(in srgb, {accent} 7%, transparent) 45%, transparent 72%);"
	>
		<div
			class="mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded-2xl ring-1 ring-current/25"
			style="background: color-mix(in srgb, {accent} 15%, transparent); color: {accent};"
		>
			<SparkleIcon size="28" weight="fill" />
		</div>
		<PageTitle>{$_('pro.page_title')}</PageTitle>
		<p class="mx-auto mt-2 max-w-xs text-sm text-muted-foreground">{$_('pro.page_subtitle')}</p>
	</div>

	<!-- Plans and feature list flow as one card. -->
	<div class="rounded-2xl border border-border/50 bg-card">
		{#if showAsPro}
			<div
				class="m-4 rounded-xl border p-5 text-center"
				style="border-color: color-mix(in srgb, {accent} 25%, transparent); background: color-mix(in srgb, {accent} 15%, transparent);"
			>
				<p class="text-base font-semibold">{$_('pro.already_pro')}</p>
				<p class="mt-1 text-sm text-muted-foreground">{$_('pro.already_pro_desc')}</p>
			</div>
		{:else}
			<!-- One form, plan picked via CSS-only radio rows — works without JS, and a
			     single CTA keeps the choice calm. Yearly is preselected. The submit button
			     lives after the feature list but stays wired to this form via form="". -->
			<form
				id="pro-checkout-form"
				method="POST"
				action="?/checkout"
				class="space-y-3 p-4 pb-0"
				style="--plan-accent: {accent}"
			>
				<label
					class="flex cursor-pointer items-center gap-3 rounded-xl border border-border p-4 transition-colors has-checked:border-(--plan-accent) has-checked:bg-(--plan-accent)/10 has-[:focus-visible]:ring-2 has-[:focus-visible]:ring-ring"
				>
					<input type="radio" name="plan" value="yearly" checked class="peer sr-only" />
					<span
						class="h-5 w-5 shrink-0 rounded-full border-2 border-muted-foreground/30 bg-background transition-all peer-checked:border-[6px] peer-checked:border-(--plan-accent)"
					></span>
					<span class="min-w-0 flex-1">
						<span class="flex items-center gap-2">
							<span class="text-sm font-semibold">{$_('pro.plan_yearly')}</span>
							<span
								class="rounded-full px-2 py-0.5 text-2xs font-bold"
								style="background: {accent}; color: var(--card);"
							>
								{$_('pro.yearly_save')}
							</span>
						</span>
						<span class="mt-0.5 block text-xs text-muted-foreground">
							{$_('pro.yearly_equiv')}
						</span>
					</span>
					<span class="shrink-0 text-right">
						<span class="block font-mono text-lg font-black tabular-nums leading-none">25 CHF</span>
						<span class="mt-0.5 block text-xs text-muted-foreground">{$_('pro.per_year')}</span>
					</span>
				</label>

				<label
					class="flex cursor-pointer items-center gap-3 rounded-xl border border-border p-4 transition-colors has-checked:border-(--plan-accent) has-checked:bg-(--plan-accent)/10 has-[:focus-visible]:ring-2 has-[:focus-visible]:ring-ring"
				>
					<input type="radio" name="plan" value="monthly" class="peer sr-only" />
					<span
						class="h-5 w-5 shrink-0 rounded-full border-2 border-muted-foreground/30 bg-background transition-all peer-checked:border-[6px] peer-checked:border-(--plan-accent)"
					></span>
					<span class="min-w-0 flex-1 text-sm font-semibold">{$_('pro.plan_monthly')}</span>
					<span class="shrink-0 text-right">
						<span class="block font-mono text-lg font-black tabular-nums leading-none">3 CHF</span>
						<span class="mt-0.5 block text-xs text-muted-foreground">{$_('pro.per_month')}</span>
					</span>
				</label>
			</form>
		{/if}

		<div class="space-y-3.5 px-4 pt-4 pb-4">
			{#each proFeatures as feature (feature.title)}
				<div class="flex items-start gap-3.5 px-2">
					<div
						class="mt-0.5 shrink-0 rounded-xl p-2"
						style="background: color-mix(in srgb, {accent} 12%, transparent); color: {accent};"
					>
						<feature.icon size="20" weight="duotone" />
					</div>
					<div class="min-w-0 space-y-0.5">
						<p class="text-sm font-semibold">{feature.title}</p>
						<p class="text-sm text-muted-foreground">{feature.desc}</p>
					</div>
				</div>
			{/each}
		</div>

		{#if !showAsPro}
			<div class="px-4 pb-4">
				<button
					type="submit"
					form="pro-checkout-form"
					class="w-full rounded-full py-2.5 text-sm font-bold transition-opacity hover:opacity-90"
					style="background: {accent}; color: var(--card);"
				>
					{$_('pro.subscribe')}
				</button>
			</div>
		{/if}
	</div>

	{#if !showAsPro}
		{#if checkoutStatus === 'cancelled'}
			<p class="text-center text-sm text-muted-foreground">{$_('pro.checkout_cancelled')}</p>
		{/if}
		<p class="text-center text-xs text-muted-foreground">{$_('pro.checkout_note')}</p>
	{/if}
</div>
