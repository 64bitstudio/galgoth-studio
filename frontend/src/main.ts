import { createApp } from 'vue'
import { createPinia } from 'pinia'
import '@fontsource-variable/inter'
import '@fontsource/jetbrains-mono/400.css'
import '@fontsource/jetbrains-mono/700.css'
import './design-system/tokens/tokens.css'
import './design-system/tokens/reset.css'
import App from './App.vue'
import router from './app/router'

createApp(App).use(createPinia()).use(router).mount('#app')
