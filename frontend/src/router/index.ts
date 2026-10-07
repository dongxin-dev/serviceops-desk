import { createRouter, createWebHistory } from 'vue-router'

import TicketListView from '@/views/ticket/TicketListView.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', redirect: '/tickets' },
    {
      path: '/tickets',
      name: 'tickets',
      component: TicketListView,
      // Detail is a Drawer on top of the list, deliberately not a route.
      meta: { titleKey: 'header.tickets' },
    },
  ],
})

export default router
