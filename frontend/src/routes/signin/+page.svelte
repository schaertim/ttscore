<script lang="ts">
	import { onMount } from 'svelte';
	import { page } from '$app/state';
	import BackButton from '$lib/components/BackButton.svelte';
	import LoginForm from '$lib/components/login-form.svelte';
	import { analytics } from '$lib/analytics';

	// account/+page.server.ts and pro/+page.server.ts redirect here server-side (a 303,
	// before any client JS runs), so unlike the other sign-up entry points — which fire
	// signupPrompted themselves right before navigating here — these two are inferred from
	// redirectTo instead. Exact-path match avoids double-firing for the other sources, whose
	// redirectTo is the arbitrary page they clicked from, not literally /account or /pro.
	onMount(() => {
		const redirectTo = page.url.searchParams.get('redirectTo');
		if (redirectTo === '/account') {
			analytics.signupPrompted('account_gate');
		} else if (redirectTo === '/pro') {
			analytics.signupPrompted('pro_gate');
		}
	});
</script>

<BackButton />
<div class="flex min-h-[70vh] items-center justify-center">
	<LoginForm />
</div>
