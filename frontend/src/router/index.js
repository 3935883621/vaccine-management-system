import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'

const ConsoleLayout = () => import('../layouts/ConsoleLayout.vue')

/** 各角色首页（登录后跳转/越权回退目标） */
export const roleHome = {
  USER: '/user/query',
  INSTITUTION_ADMIN: '/admin/alerts',
  MANUFACTURER: '/manufacturer/vaccines',
  SUPER_ADMIN: '/super/institutions',
}

const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('../views/Login.vue'),
    meta: { public: true, title: '登录' },
  },
  {
    path: '/',
    redirect: '/login',
  },
  // ---------------- 居民端 ----------------
  {
    path: '/user',
    component: ConsoleLayout,
    meta: { role: 'USER' },
    children: [
      { path: '', redirect: '/user/query' },
      { path: 'query', name: 'user-query', component: () => import('../views/user/VaccineQuery.vue'), meta: { title: '疫苗查询与预约' } },
      { path: 'appointments', name: 'user-appointments', component: () => import('../views/user/MyAppointments.vue'), meta: { title: '我的预约' } },
      { path: 'records', name: 'user-records', component: () => import('../views/user/VaccinationRecords.vue'), meta: { title: '接种记录' } },
    ],
  },
  // ---------------- 机构管理员端 ----------------
  {
    path: '/admin',
    component: ConsoleLayout,
    meta: { role: 'INSTITUTION_ADMIN' },
    children: [
      { path: '', redirect: '/admin/alerts' },
      { path: 'alerts', name: 'admin-alerts', component: () => import('../views/admin/AlertDashboard.vue'), meta: { title: '预警看板' } },
      { path: 'shifts', name: 'admin-shifts', component: () => import('../views/admin/Shifts.vue'), meta: { title: '班次与排班' } },
      { path: 'nurses', name: 'admin-nurses', component: () => import('../views/admin/Nurses.vue'), meta: { title: '护士管理' } },
      { path: 'supplies', name: 'admin-supplies', component: () => import('../views/admin/Supplies.vue'), meta: { title: '供货入库' } },
      { path: 'appointments', name: 'admin-appointments', component: () => import('../views/admin/Appointments.vue'), meta: { title: '接种处理' } },
    ],
  },
  // ---------------- 生产商端 ----------------
  {
    path: '/manufacturer',
    component: ConsoleLayout,
    meta: { role: 'MANUFACTURER' },
    children: [
      { path: '', redirect: '/manufacturer/vaccines' },
      { path: 'vaccines', name: 'mfr-vaccines', component: () => import('../views/manufacturer/Vaccines.vue'), meta: { title: '疫苗维护' } },
      { path: 'batches', name: 'mfr-batches', component: () => import('../views/manufacturer/Batches.vue'), meta: { title: '批次管理' } },
      { path: 'supplies', name: 'mfr-supplies', component: () => import('../views/manufacturer/Supplies.vue'), meta: { title: '供货记录' } },
    ],
  },
  // ---------------- 超级管理员端 ----------------
  {
    path: '/super',
    component: ConsoleLayout,
    meta: { role: 'SUPER_ADMIN' },
    children: [
      { path: '', redirect: '/super/institutions' },
      { path: 'institutions', name: 'sa-institutions', component: () => import('../views/superadmin/Institutions.vue'), meta: { title: '机构管理' } },
      { path: 'manufacturers', name: 'sa-manufacturers', component: () => import('../views/superadmin/Manufacturers.vue'), meta: { title: '生产商管理' } },
    ],
  },
  { path: '/:pathMatch(.*)*', redirect: '/login' },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

// 路由守卫：未登录 → 登录页；角色不匹配 → 各自首页（互不越权）
router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.meta.public) {
    return true
  }
  if (!auth.isLoggedIn) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  const requiredRole = to.matched.find((r) => r.meta.role)?.meta.role
  if (requiredRole && requiredRole !== auth.role) {
    ElMessage.warning('无权限访问该页面')
    return { path: roleHome[auth.role] || '/login' }
  }
  return true
})

export default router
