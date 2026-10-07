import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'

import App from './App.vue'
import router from './router'
import { i18n } from './locales'
import './assets/style.css'

const app = createApp(App)

// Pinia is part of the frozen stack and stays installed, but F1 has no
// genuine shared state yet - deliberately zero stores until one exists.
app.use(createPinia())
app.use(router)
app.use(i18n)
app.use(ElementPlus)

app.mount('#app')
