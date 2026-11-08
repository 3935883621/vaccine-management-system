<template>
  <el-card shadow="never">
    <template #header>
      <div class="toolbar">
        <span>接种机构管理（注册时自动创建机构管理员账号）</span>
        <el-button type="primary" @click="openRegister">注册机构</el-button>
      </div>
    </template>
    <el-table :data="institutions" v-loading="loading">
      <el-table-column prop="institutionId" label="ID" width="70" />
      <el-table-column prop="name" label="机构名称" width="200" />
      <el-table-column prop="region" label="区域" width="120" />
      <el-table-column prop="address" label="地址" min-width="180" />
      <el-table-column prop="contactPhone" label="联系电话" width="140" />
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="openEdit(row)">编辑</el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>

  <el-dialog v-model="registerVisible" title="注册机构" width="520px">
    <el-form :model="regForm" label-width="130px">
      <el-divider content-position="left">机构信息</el-divider>
      <el-form-item label="机构名称" required><el-input v-model="regForm.name" /></el-form-item>
      <el-form-item label="区域"><el-input v-model="regForm.region" placeholder="如：朝阳区" /></el-form-item>
      <el-form-item label="地址"><el-input v-model="regForm.address" /></el-form-item>
      <el-form-item label="联系电话"><el-input v-model="regForm.contactPhone" /></el-form-item>
      <el-divider content-position="left">管理员账号</el-divider>
      <el-form-item label="管理员用户名" required><el-input v-model="regForm.adminUsername" /></el-form-item>
      <el-form-item label="管理员密码" required><el-input v-model="regForm.adminPassword" show-password /></el-form-item>
      <el-form-item label="管理员姓名" required><el-input v-model="regForm.adminRealName" /></el-form-item>
      <el-form-item label="管理员电话"><el-input v-model="regForm.adminPhone" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="registerVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="onRegister">注册</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="editVisible" title="编辑机构" width="480px">
    <el-form :model="editForm" label-width="100px">
      <el-form-item label="机构名称" required><el-input v-model="editForm.name" /></el-form-item>
      <el-form-item label="区域"><el-input v-model="editForm.region" /></el-form-item>
      <el-form-item label="地址"><el-input v-model="editForm.address" /></el-form-item>
      <el-form-item label="联系电话"><el-input v-model="editForm.contactPhone" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="editVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="onEditSave">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '../../api/http'

const institutions = ref([])
const loading = ref(false)
const registerVisible = ref(false)
const editVisible = ref(false)
const saving = ref(false)

const regForm = reactive({
  name: '',
  region: '',
  address: '',
  contactPhone: '',
  adminUsername: '',
  adminPassword: '',
  adminRealName: '',
  adminPhone: '',
})

const editForm = reactive({
  institutionId: null,
  name: '',
  region: '',
  address: '',
  contactPhone: '',
})

onMounted(load)

async function load() {
  loading.value = true
  try {
    const res = await http.get('/api/super-admin/institutions')
    institutions.value = res.data
  } finally {
    loading.value = false
  }
}

function openRegister() {
  Object.assign(regForm, {
    name: '',
    region: '',
    address: '',
    contactPhone: '',
    adminUsername: '',
    adminPassword: '',
    adminRealName: '',
    adminPhone: '',
  })
  registerVisible.value = true
}

async function onRegister() {
  if (!regForm.name || !regForm.adminUsername || !regForm.adminPassword || !regForm.adminRealName) {
    ElMessage.warning('请填写机构名称与管理员账号信息')
    return
  }
  saving.value = true
  try {
    await http.post('/api/super-admin/institutions', { ...regForm })
    ElMessage.success('机构注册成功，管理员账号已创建')
    registerVisible.value = false
    await load()
  } catch {
    /* 错误提示由拦截器统一处理 */
  } finally {
    saving.value = false
  }
}

function openEdit(row) {
  Object.assign(editForm, {
    institutionId: row.institutionId,
    name: row.name,
    region: row.region,
    address: row.address,
    contactPhone: row.contactPhone,
  })
  editVisible.value = true
}

async function onEditSave() {
  if (!editForm.name) {
    ElMessage.warning('机构名称不能为空')
    return
  }
  saving.value = true
  try {
    await http.put(`/api/super-admin/institutions/${editForm.institutionId}`, {
      name: editForm.name,
      region: editForm.region,
      address: editForm.address,
      contactPhone: editForm.contactPhone,
    })
    ElMessage.success('保存成功')
    editVisible.value = false
    await load()
  } catch {
    /* 错误提示由拦截器统一处理 */
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
</style>
