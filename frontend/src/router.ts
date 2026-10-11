import type { RouteParams, RouteRecordRaw } from 'vue-router'
import { createRouter, createWebHistory } from 'vue-router'
import { isAuthorized } from './store/user'

export type AppRouteNames =
  | 'global-feed'
  | 'my-feed'
  | 'tag'
  | 'article'
  | 'create-article'
  | 'edit-article'
  | 'login'
  | 'register'
  | 'profile'
  | 'profile-favorites'
  | 'settings'
  | 'about'
  | 'not-found'

export const routes: RouteRecordRaw[] = [
  {
    name: 'about',
    path: '/about',
    component: () => import('./pages/About.vue'),
  },
  {
    name: 'global-feed',
    path: '/',
    component: () => import('./pages/Home.vue'),
  },
  {
    name: 'my-feed',
    path: '/my-feeds',
    component: () => import('./pages/Home.vue'),
  },
  {
    name: 'tag',
    path: '/tag/:tag',
    component: () => import('./pages/Home.vue'),
  },
  {
    name: 'article',
    path: '/article/:slug',
    component: () => import('./pages/Article.vue'),
  },
  {
    name: 'edit-article',
    path: '/article/:slug/edit',
    component: () => import('./pages/EditArticle.vue'),
  },
  {
    name: 'create-article',
    path: '/article/create',
    component: () => import('./pages/EditArticle.vue'),
  },
  {
    name: 'login',
    path: '/login',
    component: () => import('./pages/Login.vue'),
    beforeEnter: () => !isAuthorized(),
  },
  {
    name: 'register',
    path: '/register',
    component: () => import('./pages/Register.vue'),
    beforeEnter: () => !isAuthorized(),
  },
  {
    name: 'profile',
    path: '/profile/:username',
    component: () => import('./pages/Profile.vue'),
  },
  {
    name: 'profile-favorites',
    path: '/profile/:username/favorites',
    component: () => import('./pages/Profile.vue'),
  },
  {
    name: 'settings',
    path: '/settings',
    component: () => import('./pages/Settings.vue'),
  },
  {
    name: 'not-found',
    path: '/:pathMatch(.*)*',
    component: () => import('./pages/NotFound.vue'),
  },
]
export const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior(to, _from, savedPosition) {
    if (savedPosition) return savedPosition
    if (to.hash) return { el: to.hash }
    return { top: 0 }
  },
})

router.afterEach(to => {
  document.title = to.name === 'about' ? 'Inside Conduit — Java & Spring' : 'Conduit'
})

export async function routerPush(name: AppRouteNames, params?: RouteParams): ReturnType<typeof router.push> {
  return params === undefined
    ? await router.push({ name })
    : await router.push({ name, params })
}
