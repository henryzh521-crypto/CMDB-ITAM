<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { changePassword } from '@/api/auth'
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
      validator: (_rule: unknown, value: string, callback: (error?: Error) => void) => {
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
    router.replace('/m/login')
    return
  }
  saving.value = true
  try {
    await changePassword(username, form.oldPassword, form.newPassword)
    ElMessage.success('密码修改成功，请重新登录')
    auth.reset()
    router.replace('/m/login')
  } catch {
    // 错误提示已由拦截器统一弹出
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="m-chpwd">
    <el-alert
      v-if="auth.user?.mustChangePwd"
      title="首次登录必须修改初始密码"
      description="修改完成后将自动退出并重新登录。"
      type="warning"
      :closable="false"
      show-icon
    />
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent>
      <el-form-item label="原密码" prop="oldPassword">
        <el-input
          v-model="form.oldPassword"
          type="password"
          show-password
          size="large"
          placeholder="请输入原密码"
          :disabled="saving"
        />
      </el-form-item>
      <el-form-item label="新密码" prop="newPassword">
        <el-input
          v-model="form.newPassword"
          type="password"
          show-password
          size="large"
          placeholder="至少 6 位"
          :disabled="saving"
        />
      </el-form-item>
      <el-form-item label="确认新密码" prop="confirmPassword">
        <el-input
          v-model="form.confirmPassword"
          type="password"
          show-password
          size="large"
          placeholder="请再次输入新密码"
          :disabled="saving"
          @keyup.enter="submit"
        />
      </el-form-item>
      <el-button
        type="primary"
        size="large"
        class="m-chpwd-btn"
        :loading="saving"
        @click="submit"
      >
        确认修改
      </el-button>
    </el-form>
  </div>
</template>

<style scoped>
.m-chpwd {
  padding: 16px 12px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.m-chpwd-btn {
  width: 100%;
}
</style>
