<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import http from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import { passwordRule } from '@/utils/password'

const auth = useAuthStore()
const router = useRouter()

const formRef = ref<FormInstance>()
const saving = ref(false)
const form = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

const rules: FormRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    passwordRule(),
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_rule: unknown, value: string, callback: (error?: any) => void) => {
        if (value !== form.newPassword) callback(new Error('两次输入的新密码不一致'))
        else callback()
      },
      trigger: 'blur',
    },
  ],
}

async function submit() {
  const valid = await formRef.value?.validate().then(() => true).catch(() => false)
  if (!valid) return
  const username = auth.user?.username
  if (!username) {
    ElMessage.error('登录信息缺失，请重新登录')
    router.push('/login')
    return
  }
  saving.value = true
  try {
    await http.put(`/users/${encodeURIComponent(username)}/password`, {
      oldPassword: form.oldPassword,
      newPassword: form.newPassword,
    })
    ElMessage.success('密码修改成功，请重新登录')
    auth.reset()
    router.push('/login')
  } catch {
    // 错误提示已由拦截器统一弹出
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="pwd-page">
    <el-card class="pwd-card" shadow="always">
      <div class="pwd-title">修改密码</div>
      <el-alert
        v-if="auth.user?.mustChangePwd"
        title="首次登录或密码已被重置，请先修改密码后再继续使用"
        type="warning"
        :closable="false"
        style="margin-bottom: 18px"
      />
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="账号">
          <el-input :model-value="auth.user?.username || auth.user?.name || '-'" disabled />
        </el-form-item>
        <el-form-item label="原密码" prop="oldPassword">
          <el-input v-model="form.oldPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="form.newPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input v-model="form.confirmPassword" type="password" show-password />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="saving" @click="submit">确认修改</el-button>
          <el-button @click="auth.reset(), router.push('/login')">返回登录</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.pwd-page {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--el-fill-color-light);
}

.pwd-card {
  width: 440px;
  border-radius: 10px;
}

.pwd-title {
  font-size: 18px;
  font-weight: 700;
  text-align: center;
  margin-bottom: 20px;
  color: var(--el-text-color-primary);
}
</style>
