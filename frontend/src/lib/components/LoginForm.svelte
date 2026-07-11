<script lang="ts">
	import { goto, invalidate } from '$app/navigation';
	import { page } from '$app/state';
	import { Button } from '$lib/components/ui/button/index.js';
	import { Input } from '$lib/components/ui/input/index.js';
	import { Label } from '$lib/components/ui/label/index.js';
	import { Spinner } from '$lib/components/ui/spinner/index.js';
	import GoogleIcon from '$lib/components/icons/GoogleIcon.svelte';
	import { _ } from 'svelte-i18n';

	type Mode = 'signin' | 'signup';
	let mode: Mode = $state('signin');
	let email = $state('');
	let password = $state('');
	let confirmPassword = $state('');
	let error = $state('');
	let checkEmail = $state(false);
	let loading = $state(false);

	const redirectTo = $derived(page.url.searchParams.get('redirectTo') ?? '/');

	function switchMode(next: Mode) {
		mode = next;
		confirmPassword = '';
		error = '';
		checkEmail = false;
	}

	async function handleOAuth(provider: 'google') {
		error = '';
		const { error: oauthError } = await page.data.supabase.auth.signInWithOAuth({
			provider,
			options: {
				redirectTo: `${window.location.origin}/auth/callback?next=${encodeURIComponent(redirectTo)}`
			}
		});
		if (oauthError) error = oauthError.message;
	}

	async function handleSubmit(e: Event) {
		e.preventDefault();
		error = '';
		checkEmail = false;
		loading = true;
		try {
			if (mode === 'signin') {
				const { error: signInError } = await page.data.supabase.auth.signInWithPassword({
					email,
					password
				});
				if (signInError) {
					error = signInError.message;
					return;
				}
				// Refresh the server layout data (hasHomePlayer / isPro are computed there
				// from the session cookie) BEFORE navigating, so the target page renders
				// with the correct signed-in state instead of the pre-login state.
				await invalidate('supabase:auth');
				goto(redirectTo);
			} else {
				if (password !== confirmPassword) {
					error = $_('auth.password_mismatch');
					return;
				}
				const { data, error: signUpError } = await page.data.supabase.auth.signUp({
					email,
					password
				});
				if (signUpError) {
					error = signUpError.message;
					return;
				}
				if (data.session) {
					// Email confirmation is disabled — the user is signed in immediately.
					await invalidate('supabase:auth');
					goto(redirectTo);
				} else {
					// Supabase sent a confirmation email; stay here and say so.
					checkEmail = true;
				}
			}
		} finally {
			loading = false;
		}
	}
</script>

<div class="w-full max-w-sm">
	<div class="flex flex-col items-center gap-2 text-center">
		<div
			class="mb-1 flex size-11 items-center justify-center rounded-xl bg-foreground text-base leading-none font-black text-background"
		>
			tt
		</div>
		<h1 class="text-xl font-black tracking-tight">
			{$_(mode === 'signin' ? 'auth.welcome_back' : 'auth.create_account')}
		</h1>
		<p class="text-sm text-muted-foreground">
			{$_(mode === 'signin' ? 'auth.signin_desc' : 'auth.signup_desc')}
		</p>
	</div>

	<div class="mt-6 flex flex-col gap-5">
		<Button
			variant="outline"
			type="button"
			class="w-full rounded-full"
			onclick={() => handleOAuth('google')}
		>
			<GoogleIcon />
			{$_('auth.continue_google')}
		</Button>

		<div class="flex items-center gap-3">
			<div class="h-px flex-1 bg-border"></div>
			<span class="text-xs text-muted-foreground">{$_('auth.or')}</span>
			<div class="h-px flex-1 bg-border"></div>
		</div>

		<form onsubmit={handleSubmit} class="flex flex-col gap-4">
			<div class="flex flex-col gap-2">
				<Label for="email">{$_('auth.email')}</Label>
				<Input
					id="email"
					name="email"
					type="email"
					placeholder="m@example.com"
					bind:value={email}
					required
					autocomplete="email"
				/>
			</div>

			<div class="flex flex-col gap-2">
				<Label for="password">{$_('auth.password')}</Label>
				<Input
					id="password"
					name="password"
					type="password"
					bind:value={password}
					required
					autocomplete={mode === 'signin' ? 'current-password' : 'new-password'}
				/>
			</div>

			{#if mode === 'signup'}
				<div class="flex flex-col gap-2">
					<Label for="confirm-password">{$_('auth.confirm_password')}</Label>
					<Input
						id="confirm-password"
						name="confirm-password"
						type="password"
						bind:value={confirmPassword}
						required
						autocomplete="new-password"
					/>
				</div>
			{/if}

			{#if error}
				<p
					class="rounded-lg border border-destructive/30 bg-destructive/10 px-3 py-2 text-sm text-destructive"
				>
					{error}
				</p>
			{/if}
			{#if checkEmail}
				<p class="rounded-lg border border-primary/20 bg-primary/5 px-3 py-2 text-sm">
					{$_('auth.check_email')}
				</p>
			{/if}

			<Button type="submit" class="w-full rounded-full" disabled={loading}>
				{#if loading}
					<Spinner class="size-4" />
				{/if}
				{$_(mode === 'signin' ? 'auth.sign_in' : 'auth.create_account')}
			</Button>
		</form>

		<p class="text-center text-sm text-muted-foreground">
			{#if mode === 'signin'}
				{$_('auth.no_account')}
				<button
					type="button"
					class="font-semibold text-foreground underline underline-offset-4"
					onclick={() => switchMode('signup')}>{$_('auth.sign_up')}</button
				>
			{:else}
				{$_('auth.have_account')}
				<button
					type="button"
					class="font-semibold text-foreground underline underline-offset-4"
					onclick={() => switchMode('signin')}>{$_('auth.sign_in')}</button
				>
			{/if}
		</p>
	</div>
</div>
