<template>
  <div class="admin-page">
    <div class="container page">
      <div class="row">
        <div class="col-md-6 offset-md-3 col-xs-12">
          <h1 class="text-xs-center">Admin Dashboard</h1>
          <h2 class="text-xs-center" style="font-size: 1.4rem; margin-bottom: 30px; color: #555;">Manage User Roles</h2>
          
          <div v-if="successMessage" class="alert alert-success" style="padding: 12px; background-color: #d4edda; color: #155724; border-radius: 4px; margin-bottom: 20px;">
            {{ successMessage }}
          </div>
          <div v-if="errorMessage" class="alert alert-danger" style="padding: 12px; background-color: #f8d7da; color: #721c24; border-radius: 4px; margin-bottom: 20px;">
            {{ errorMessage }}
          </div>

          <form @submit.prevent="onUpdateRole">
            <fieldset class="form-group">
              <label><strong>Target Username</strong></label>
              <input
                v-model="targetUsername"
                type="text"
                class="form-control form-control-lg"
                placeholder="Enter username"
                required
              >
            </fieldset>
            <fieldset class="form-group">
              <label><strong>Select New Role</strong></label>
              <select v-model="selectedRole" class="form-control form-control-lg" required>
                <option value="User">User</option>
                <option value="Moderator">Moderator</option>
                <option value="Admin">Admin</option>
              </select>
            </fieldset>
            <button
              type="submit"
              class="btn btn-lg btn-primary pull-xs-right"
              :disabled="isSubmitting || !targetUsername.trim()"
            >
              Update Role
            </button>
          </form>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { CONFIG } from 'src/config'
import { useUserStore } from 'src/store/user'

const userStore = useUserStore()
const targetUsername = ref('')
const selectedRole = ref('User')
const isSubmitting = ref(false)
const successMessage = ref('')
const errorMessage = ref('')

async function onUpdateRole() {
  successMessage.value = ''
  errorMessage.value = ''
  isSubmitting.value = true
  
  try {
    const response = await fetch(`${CONFIG.API_HOST}/api/user/${targetUsername.value.trim()}/role`, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Token ${userStore.user?.token || ''}`
      },
      body: JSON.stringify({
        user: {
          role: selectedRole.value
        }
      })
    })

    if (response.ok) {
      successMessage.value = `Successfully updated role of "${targetUsername.value.trim()}" to "${selectedRole.value}".`
      targetUsername.value = ''
      selectedRole.value = 'User'
    } else {
      const errorData = await response.json().catch(() => ({}))
      errorMessage.value = errorData.message || 'Failed to update user role.'
    }
  } catch (error) {
    errorMessage.value = 'An unexpected error occurred while communicating with the server.'
    console.error(error)
  } finally {
    isSubmitting.value = false
  }
}
</script>