<template>
  <div class="login-page">
    <el-card class="login-card">
      <h1 class="title">疫苗管理系统</h1>
      <p class="subtitle">疫苗预约 · 库存管理 · 批次追溯</p>
      <el-form :model="form" label-width="70px" @keyup.enter="onLogin">
        <el-form-item label="角色">
          <el-select v-model="form.role" placeholder="请选择登录角色" style="width: 100%">
            <el-option v-for="(t, r) in roleTexts" :key="r" :label="t" :value="r" />
          </el-select>
        </el-form-item>
        <el-form-item label="用户名">
          <el-input v-model="form.username" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" show-password placeholder="请输入密码" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" style="width: 100%" :loading="loading" @click="onLogin">
            登 录
          </el-button>
        </el-form-item>
        <el-form-item v-if="form.role === 'USER'">
          <el-button link type="primary" @click="regVisible = true">没有账号？居民注册</el-button>
        </el-form-item>
      </el-form>
      <el-divider content-position="center">演示账号</el-divider>
      <div class="demo-tips">
        <p>居民 zhangsan / 机构管理员 inst_admin1 / 生产商 huaxia / 超管 superadmin（密码均为 123456）</p>
      </div>
    </el-card>

    <!-- 居民注册 -->
    <el-dialog v-model="regVisible" title="居民注册" width="480px">
      <el-form :model="regForm" label-width="90px">
        <el-form-item label="用户名" required><el-input v-model="regForm.username" /></el-form-item>
        <el-form-item label="密码" required><el-input v-model="regForm.password" type="password" show-password /></el-form-item>
        <el-form-item label="姓名" required><el-input v-model="regForm.realName" /></el-form-item>
        <el-form-item label="身份证号" required><el-input v-model="regForm.idCard" /></el-form-item>
        <el-form-item label="手机号" required><el-input v-model="regForm.phone" /></el-form-item>
        <el-form-item label="性别">
          <el-radio-group v-model="regForm.gender">
            <el-radio :value="1">男</el-radio>
            <el-radio :value="2">女</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="出生日期">
          <el-date-picker v-model="regForm.birthDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="regVisible = false">取消</el-button>
        <el-button type="primary" :loading="regLoading" @click="onRegister">注册</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import http from '../api/http'
import { useAuthStore } from '../stores/auth'
import { roleHome } from '../router'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

const roleTexts = {
  USER: '居民',
  INSTITUTION_ADMIN: '机构管理员',
  MANUFACTURER: '生产商',
  SUPER_ADMIN: '超级管理员',
}

const form = reactive({ role: 'USER', username: '', password: '' })
const loading = ref(false)

async function onLogin() {
  if (!form.role || !form.username || !form.password) {
    ElMessage.warning('请填写完整的登录信息')
    return
  }
  loading.value = true
  try {
    const res = await http.post('/api/auth/login', {
      username: form.username,
      password: form.password,
      role: form.role,
    })
    auth.setLogin(res.data)
    ElMessage.success(`欢迎，${res.data.realName || res.data.username}`)
    router.replace(route.query.redirect || roleHome[auth.role] || '/')
  } finally {
    loading.value = false
  }
}

// ---------------- 居民注册 ----------------
const regVisible = ref(false)
const regLoading = ref(false)
const regForm = reactive({
  username: '',
  password: '',
  realName: '',
  idCard: '',
  phone: '',
  gender: 1,
  birthDate: '',
})

async function onRegister() {
  if (!regForm.username || !regForm.password || !regForm.realName || !regForm.idCard || !regForm.phone) {
    ElMessage.warning('请填写完整信息（用户名/密码/姓名/身份证/手机号）')
    return
  }
  regLoading.value = true
  try {
    await http.post('/api/auth/register', { ...regForm })
    ElMessage.success('注册成功，请登录')
    regVisible.value = false
    form.username = regForm.username
    form.password = regForm.password
  } finally {
    regLoading.value = false
  }
}
</script>

<style scoped>
.login-page {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1f6feb 0%, #2b9d8f 100%);
}

.login-card {
  width: 400px;
}

.title {
  text-align: center;
  margin-bottom: 6px;
}

.subtitle {
  text-align: center;
  color: #909399;
  font-size: 13px;
  margin-bottom: 20px;
}

.demo-tips {
  color: #909399;
  font-size: 12px;
  line-height: 1.8;
}
</style>
