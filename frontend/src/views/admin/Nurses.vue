<template>
  <el-card shadow="never">
    <template #header>
      <div class="toolbar">
        <span>护士管理</span>
        <el-button type="primary" @click="openCreate">新增护士</el-button>
      </div>
    </template>
    <el-table :data="nurses" v-loading="loading">
      <el-table-column prop="name" label="姓名" width="120" />
      <el-table-column prop="employeeNo" label="工号" width="120" />
      <el-table-column prop="title" label="职称" width="120" />
      <el-table-column prop="phone" label="联系电话" width="150" />
      <el-table-column label="操作" min-width="160">
        <template #default="{ row }">
          <el-button size="small" @click="openEdit(row)">编辑</el-button>
          <el-button size="small" type="danger" plain @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>

  <el-dialog v-model="dialogVisible" :title="form.nurseId ? '编辑护士' : '新增护士'" width="460px">
    <el-form :model="form" label-width="90px">
      <el-form-item label="姓名" required><el-input v-model="form.name" /></el-form-item>
      <el-form-item label="工号" required><el-input v-model="form.employeeNo" /></el-form-item>
      <el-form-item label="职称"><el-input v-model="form.title" /></el-form-item>
      <el-form-item label="联系电话" required><el-input v-model="form.phone" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '../../api/http'
import { useAuthStore } from '../../stores/auth'

const auth = useAuthStore()
const nurses = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const saving = ref(false)
const form = reactive({ nurseId: null, name: '', employeeNo: '', title: '', phone: '' })

onMounted(load)

async function load() {
  loading.value = true
  try {
    const res = await http.get('/api/institution-admin/nurses', {
      params: { institutionId: auth.institutionId },
    })
    nurses.value = res.data
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, { nurseId: null, name: '', employeeNo: '', title: '', phone: '' })
  dialogVisible.value = true
}

function openEdit(row) {
  Object.assign(form, row)
  dialogVisible.value = true
}

async function onSave() {
  if (!form.name || !form.employeeNo || !form.phone) {
    ElMessage.warning('请填写姓名/工号/联系电话')
    return
  }
  saving.value = true
  try {
    if (form.nurseId) {
      await http.put(`/api/institution-admin/nurses/${form.nurseId}`, {
        name: form.name,
        employeeNo: form.employeeNo,
        title: form.title,
        phone: form.phone,
      })
    } else {
      await http.post('/api/institution-admin/nurses', {
        name: form.name,
        employeeNo: form.employeeNo,
        title: form.title,
        phone: form.phone,
      })
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    await load()
  } catch {
    /* 错误提示由拦截器统一处理 */
  } finally {
    saving.value = false
  }
}

async function onDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除护士 ${row.name} 吗？（存在排班记录时将被拒绝）`, '删除确认', { type: 'warning' })
  } catch {
    return
  }
  try {
    await http.delete(`/api/institution-admin/nurses/${row.nurseId}`)
    ElMessage.success('已删除')
    await load()
  } catch {
    /* 错误提示由拦截器统一处理 */
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
