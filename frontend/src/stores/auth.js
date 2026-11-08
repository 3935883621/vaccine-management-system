import { defineStore } from 'pinia'

/**
 * 登录态：token/角色/用户信息，localStorage 持久化。
 * 角色取值：USER / INSTITUTION_ADMIN / MANUFACTURER / SUPER_ADMIN
 */
export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem('vaccine_token') || '',
    role: localStorage.getItem('vaccine_role') || '',
    username: localStorage.getItem('vaccine_username') || '',
    realName: localStorage.getItem('vaccine_real_name') || '',
    institutionId: localStorage.getItem('vaccine_institution_id') || '',
  }),
  getters: {
    isLoggedIn: (state) => !!state.token,
  },
  actions: {
    setLogin(data) {
      this.token = data.token
      this.role = data.role
      this.username = data.username || ''
      this.realName = data.realName || ''
      this.institutionId = data.institutionId ? String(data.institutionId) : ''
      localStorage.setItem('vaccine_token', this.token)
      localStorage.setItem('vaccine_role', this.role)
      localStorage.setItem('vaccine_username', this.username)
      localStorage.setItem('vaccine_real_name', this.realName)
      localStorage.setItem('vaccine_institution_id', this.institutionId)
    },
    logout() {
      this.token = ''
      this.role = ''
      this.username = ''
      this.realName = ''
      this.institutionId = ''
      localStorage.removeItem('vaccine_token')
      localStorage.removeItem('vaccine_role')
      localStorage.removeItem('vaccine_username')
      localStorage.removeItem('vaccine_real_name')
      localStorage.removeItem('vaccine_institution_id')
    },
  },
})
