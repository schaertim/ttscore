<script lang="ts">
	import { goto } from '$app/navigation';
	import { page } from '$app/state';
	import { Button } from '$lib/components/ui/button/index.js';
	import { Input } from '$lib/components/ui/input/index.js';
	import { _ } from 'svelte-i18n';

	// The Supabase client is passed down through the layout's page data.
	// Using $page.data gives us access to the supabase instance created in +layout.ts.
	import { page as pageStore } from '$app/stores';

	type Mode = 'signin' | 'signup';
	let mode: Mode = $state('signin');
	let email = $state('');
	let password = $state('');
	let error = $state('');
	let loading = $state(false);

	const redirectTo = $derived(page.url.searchParams.get('redirectTo') ?? '/');

	async function handleGoogleSignIn() {
		const { data, error: oauthError } = await $pageStore.data.supabase.auth.signInWithOAuth({
			provider: 'google',
			options: {
				redirectTo: `${window.location.origin}/auth/callback?next=${encodeURIComponent(redirectTo)}`
			}
		});
		if (oauthError) error = oauthError.message;
	}

	async function handleSubmit(e: Event) {
		e.preventDefault();
		error = '';
		loading = true;
		try {
			if (mode === 'signin') {
				const { error: signInError } = await $pageStore.data.supabase.auth.signInWithPassword({
					email,
					password
				});
				if (signInError) {
					error = signInError.message;
				} else {
					goto(redirectTo);
				}
			} else {
				const { error: signUpError } = await $pageStore.data.supabase.auth.signUp({
					email,
					password
				});
				if (signUpError) {
					error = signUpError.message;
				} else {
					// Supabase sends a confirmation email by default.
					// If email confirmation is disabled in the dashboard, the user is
					// signed in immediately and we redirect. Otherwise show a message.
					error = 'Check your email to confirm your account.';
					goto(redirectTo);
				}
			}
		} finally {
			loading = false;
		}
	}
</script>

<div class="w-full max-w-sm">
	<form onsubmit={handleSubmit} class="flex flex-col gap-6">
		<div class="flex flex-col items-center gap-2 text-center">
			<div class="flex size-8 items-center justify-center rounded-md bg-foreground text-background">
				<span class="text-sm leading-none font-bold">tt</span>
			</div>
			<h1 class="text-xl font-bold">
				{$_(mode === 'signin' ? 'auth.welcome_back' : 'auth.create_account')}
			</h1>
			<p class="text-sm text-muted-foreground">
				{#if mode === 'signin'}
					{$_('auth.no_account')}
					<button
						type="button"
						class="underline underline-offset-4 hover:text-foreground"
						onclick={() => {
							mode = 'signup';
							error = '';
						}}>{$_('auth.sign_up')}</button
					>
				{:else}
					{$_('auth.have_account')}
					<button
						type="button"
						class="underline underline-offset-4 hover:text-foreground"
						onclick={() => {
							mode = 'signin';
							error = '';
						}}>{$_('auth.sign_in')}</button
					>
				{/if}
			</p>
		</div>

		<Button variant="outline" type="button" class="w-full" onclick={handleGoogleSignIn}>
			<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" class="mr-2 size-4 shrink-0">
				<path
					d="M12.48 10.92v3.28h7.84c-.24 1.84-.853 3.187-1.787 4.133-1.147 1.147-2.933 2.4-6.053 2.4-4.827 0-8.6-3.893-8.6-8.72s3.773-8.72 8.6-8.72c2.6 0 4.507 1.027 5.907 2.347l2.307-2.307C18.747 1.44 16.133 0 12.48 0 5.867 0 .307 5.387.307 12s5.56 12 12.173 12c3.573 0 6.267-1.173 8.373-3.36 2.16-2.16 2.84-5.213 2.84-7.667 0-.76-.053-1.467-.173-2.053H12.48z"
					fill="currentColor"
				/>
			</svg>
			Continue with Google
		</Button>

		<div class="flex items-center gap-3">
			<div class="h-px flex-1 bg-border"></div>
			<span class="text-xs text-muted-foreground">{$_('auth.or')}</span>
			<div class="h-px flex-1 bg-border"></div>
		</div>

		<div class="flex flex-col gap-4">
			<div class="flex flex-col gap-2">
				<label class="text-sm font-medium" for="email">{$_('auth.email')}</label>
				<Input
					id="email"
					type="email"
					placeholder="m@example.com"
					bind:value={email}
					required
					autocomplete="email"
				/>
			</div>

			<div class="flex flex-col gap-2">
				<label class="text-sm font-medium" for="password">{$_('auth.password')}</label>
				<Input
					id="password"
					type="password"
					bind:value={password}
					required
					autocomplete={mode === 'signin' ? 'current-password' : 'new-password'}
				/>
			</div>
		</div>

		{#if error}
			<p class="text-sm text-destructive">{error}</p>
		{/if}

		<Button type="submit" class="w-full" disabled={loading}>
			{$_(loading ? 'auth.loading' : mode === 'signin' ? 'auth.sign_in' : 'auth.create_account')}
		</Button>
	</form>
</div>
