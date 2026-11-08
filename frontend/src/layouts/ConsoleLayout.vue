<template>
  <el-container class="console">
    <el-aside width="210px" class="aside">
      <div class="logo">疫苗管理系统</div>
      <el-menu :default-active="$route.path" router background-color="#001529" text-color="#c0c4cc" active-text-color="#409eff">
        <el-menu-item v-for="m in menus" :key="m.path" :index="m.path">
          {{ m.title }}
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <span class="page-title">{{ $route.meta.title }}</span>
        <span class="user-info">
          <el-tag size="small" type="info">{{ roleText }}</el-tag>
          <span class="name">{{ auth.realName || auth.username }}</span>
          <el-button link type="primary" @click="onLogout">退出登录</el-button>
        </span>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const router = useRouter()

const roleTexts = {
  USER: '居民',
  INSTITUTION_ADMIN: '机构管理员',
  MANUFACTURER: '生产商',
  SUPER_ADMIN: '超级管理员',
}

const menusByRole = {
  USER: [
    { path: '/user/query', title: '疫苗查询与预约' },
    { path: '/user/appointments', title: '我的预约' },
    { path: '/user/records', title: '接种记录' },
  ],
  INSTITUTION_ADMIN: [
    { path: '/admin/alerts', title: '预警看板' },
    { path: '/admin/shifts', title: '班次与排班' },
    { path: '/admin/nurses', title: '护士管理' },
    { path: '/admin/supplies', title: '供货入库' },
    { path: '/admin/appointments', title: '接种处理' },
  ],
  MANUFACTURER: [
    { path: '/manufacturer/vaccines', title: '疫苗维护' },
    { path: '/manufacturer/batches', title: '批次管理' },
    { path: '/manufacturer/supplies', title: '供货记录' },
  ],
  SUPER_ADMIN: [
    { path: '/super/institutions', title: '机构管理' },
    { path: '/super/manufacturers', title: '生产商管理' },
  ],
}

const menus = computed(() => menusByRole[auth.role] || [])
const roleText = computed(() => roleTexts[auth.role] || auth.role)

function onLogout() {
  auth.logout()
  router.replace('/login')
}
</script>

<style scoped>
.console {
  height: 100%;
}

.aside {
  background: #001529;
}

.logo {
  height: 60px;
  line-height: 60px;
  text-align: center;
  color: #fff;
  font-size: 16px;
  font-weight: 600;
}

.aside :deep(.el-menu) {
  border-right: none;
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
}

.page-title {
  font-size: 16px;
  font-weight: 600;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 10px;
}

.name {
  color: #303133;
}

.main {
  background: #f0f2f5;
}
</style>
