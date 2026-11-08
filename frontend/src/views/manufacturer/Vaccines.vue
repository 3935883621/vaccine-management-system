<template>
  <el-card shadow="never">
    <template #header>
      <div class="toolbar">
        <span>疫苗维护（本厂关联疫苗可编辑）</span>
        <el-button type="primary" @click="openCreate">新增疫苗</el-button>
      </div>
    </template>
    <el-table :data="vaccines" v-loading="loading">
      <el-table-column prop="name" label="疫苗名称" width="150" />
      <el-table-column prop="type" label="类型" width="90" />
      <el-table-column prop="disease" label="预防疾病" width="130" />
      <el-table-column label="剂次" width="70">
        <template #default="{ row }">{{ row.dosesRequired }} 剂</template>
      </el-table-column>
      <el-table-column label="生产厂商" min-width="160">
        <template #default="{ row }">
          <el-tag v-for="m in row.manufacturers" :key="m.manufacturerId" size="small" class="mfr-tag">
            {{ m.name }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="本厂状态" width="110">
        <template #default="{ row }">
          <el-tag :type="row.mine ? 'success' : 'info'" size="small">{{ row.mine ? '已关联' : '未关联' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" min-width="200" fixed="right">
        <template #default="{ row }">
          <template v-if="row.mine">
            <el-button size="small" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" type="warning" plain @click="onDisassociate(row)">解除关联</el-button>
          </template>
          <el-button v-else size="small" type="primary" plain @click="onAssociate(row)">关联</el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>

  <el-dialog v-model="dialogVisible" :title="form.vaccineId ? '编辑疫苗' : '新增疫苗'" width="520px">
    <el-form :model="form" label-width="110px">
      <el-form-item label="疫苗名称" required><el-input v-model="form.name" /></el-form-item>
      <el-form-item label="类型" required><el-input v-model="form.type" placeholder="如：减毒活疫苗" /></el-form-item>
      <el-form-item label="预防疾病" required><el-input v-model="form.disease" /></el-form-item>
      <el-form-item label="全程剂次" required>
        <el-input-number v-model="form.dosesRequired" :min="1" :max="10" />
      </el-form-item>
      <el-form-item label="储存温度(℃)">
        <el-input-number v-model="form.storageTempMin" :precision="1" :step="0.5" placeholder="最低" />
        <span class="sep">~</span>
        <el-input-number v-model="form.storageTempMax" :precision="1" :step="0.5" placeholder="最高" />
      </el-form-item>
      <el-form-item label="描述"><el-input v-model="form.description" type="textarea" :rows="2" /></el-form-item>
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

const vaccines = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const saving = ref(false)
const form = reactive({
  vaccineId: null,
  name: '',
  type: '',
  disease: '',
  dosesRequired: 1,
  storageTempMin: null,
  storageTempMax: null,
  description: '',
})

onMounted(load)

async function load() {
  loading.value = true
  try {
    const res = await http.get('/api/manufacturer/vaccines')
    vaccines.value = res.data
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, {
    vaccineId: null,
    name: '',
    type: '',
    disease: '',
    dosesRequired: 1,
    storageTempMin: null,
    storageTempMax: null,
    description: '',
  })
  dialogVisible.value = true
}

function openEdit(row) {
  Object.assign(form, {
    vaccineId: row.vaccineId,
    name: row.name,
    type: row.type,
    disease: row.disease,
    dosesRequired: row.dosesRequired,
    storageTempMin: row.storageTempMin,
    storageTempMax: row.storageTempMax,
    description: row.description,
  })
  dialogVisible.value = true
}

async function onSave() {
  if (!form.name || !form.type || !form.disease || !form.dosesRequired) {
    ElMessage.warning('请填写名称/类型/疾病/剂次')
    return
  }
  saving.value = true
  const payload = { ...form }
  try {
    if (form.vaccineId) {
      await http.put(`/api/manufacturer/vaccines/${form.vaccineId}`, payload)
    } else {
      await http.post('/api/manufacturer/vaccines', payload)
    }
    ElMessage.success('保存成功（自动关联本厂）')
    dialogVisible.value = false
    await load()
  } catch {
    /* 错误提示由拦截器统一处理 */
  } finally {
    saving.value = false
  }
}

async function onAssociate(row) {
  try {
    await http.post(`/api/manufacturer/vaccines/${row.vaccineId}/associate`)
    ElMessage.success('已关联本厂')
    await load()
  } catch {
    /* 错误提示由拦截器统一处理 */
  }
}

async function onDisassociate(row) {
  try {
    await ElMessageBox.confirm(`确定解除与 ${row.name} 的关联吗？（存在本厂生产批次时将被拒绝）`, '解除关联', { type: 'warning' })
  } catch {
    return
  }
  try {
    await http.delete(`/api/manufacturer/vaccines/${row.vaccineId}/associate`)
    ElMessage.success('已解除关联')
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

.mfr-tag {
  margin-right: 6px;
}

.sep {
  margin: 0 8px;
  color: #909399;
}
</style>
