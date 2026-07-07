<script lang="ts">
	import { page } from '$app/state';
	import InfoItem from '$lib/components/InfoItem.svelte';
	import { UserCirclePlusIcon } from 'phosphor-svelte';
	import { _ } from 'svelte-i18n';
	import { analytics } from '$lib/analytics';
</script>

<InfoItem
	href="/signin?redirectTo={encodeURIComponent(page.url.pathname)}"
	onclick={() => analytics.signupPrompted('home_signin_banner')}
	icon={UserCirclePlusIcon}
	title={$_('set_player.banner_title')}
	description={$_('set_player.sign_in_prompt')}
/>
