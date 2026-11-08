<template>
  <el-card shadow="never">
    <template #header>
      <div class="toolbar">
        <span>生产商管理（注册时自动创建登录账号）</span>
        <el-button type="primary" @click="openRegister">注册生产商</el-button>
      </div>
    </template>
    <el-table :data="manufacturers" v-loading="loading">
      <el-table-column prop="manufacturerId" label="ID" width="70" />
      <el-table-column prop="name" label="生产商名称" width="200" />
      <el-table-column prop="licenseNo" label="生产许可证号" width="170" />
      <el-table-column prop="contactPhone" label="联系电话" width="140" />
      <el-table-column prop="address" label="地址" min-width="180" />
      <el-table-column prop="username" label="登录账号" width="130" />
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="openEdit(row)">编辑</el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>

  <el-dialog v-model="registerVisible" title="注册生产商" width="480px">
    <el-form :model="regForm" label-width="120px">
      <el-form-item label="生产商名称" required><el-input v-model="regForm.name" /></el-form-item>
      <el-form-item label="生产许可证号" required><el-input v-model="regForm.licenseNo" /></el-form-item>
      <el-form-item label="联系电话"><el-input v-model="regForm.contactPhone" /></el-form-item>
      <el-form-item label="地址"><el-input v-model="regForm.address" /></el-form-item>
      <el-divider content-position="left">登录账号</el-divider>
      <el-form-item label="登录用户名" required><el-input v-model="regForm.username" /></el-form-item>
      <el-form-item label="登录密码" required><el-input v-model="regForm.password" show-password /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="registerVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="onRegister">注册</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="editVisible" title="编辑生产商" width="480px">
    <el-form :model="editForm" label-width="120px">
      <el-form-item label="生产商名称" required><el-input v-model="editForm.name" /></el-form-item>
      <el-form-item label="生产许可证号" required><el-input v-model="editForm.licenseNo" /></el-form-item>
      <el-form-item label="联系电话"><el-input v-model="editForm.contactPhone" /></el-form-item>
      <el-form-item label="地址"><el-input v-model="editForm.address" /></el-form-item>
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

const manufacturers = ref([])
const loading = ref(false)
const registerVisible = ref(false)
const editVisible = ref(false)
const saving = ref(false)

const regForm = reactive({
  name: '',
  licenseNo: '',
  contactPhone: '',
  address: '',
  username: '',
  password: '',
})

const editForm = reactive({
  manufacturerId: null,
  name: '',
  licenseNo: '',
  contactPhone: '',
  address: '',
})

onMounted(load)

async function load() {
  loading.value = true
  try {
    const res = await http.get('/api/super-admin/manufacturers')
    manufacturers.value = res.data
  } finally {
    loading.value = false
  }
}

function openRegister() {
  Object.assign(regForm, {
    name: '',
    licenseNo: '',
    contactPhone: '',
    address: '',
    username: '',
    password: '',
  })
  registerVisible.value = true
}

async function onRegister() {
  if (!regForm.name || !regForm.licenseNo || !regForm.username || !regForm.password) {
    ElMessage.warning('请填写生产商名称、许可证号与登录账号')
    return
  }
  saving.value = true
  try {
    await http.post('/api/super-admin/manufacturers', { ...regForm })
    ElMessage.success('生产商注册成功，登录账号已创建')
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
    manufacturerId: row.manufacturerId,
    name: row.name,
    licenseNo: row.licenseNo,
    contactPhone: row.contactPhone,
    address: row.address,
  })
  editVisible.value = true
}

async function onEditSave() {
  if (!editForm.name || !editForm.licenseNo) {
    ElMessage.warning('名称与许可证号不能为空')
    return
  }
  saving.value = true
  try {
    await http.put(`/api/super-admin/manufacturers/${editForm.manufacturerId}`, {
      name: editForm.name,
      licenseNo: editForm.licenseNo,
      contactPhone: editForm.contactPhone,
      address: editForm.address,
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
