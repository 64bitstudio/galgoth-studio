import js from '@eslint/js'
import tseslint from 'typescript-eslint'
import pluginVue from 'eslint-plugin-vue'
import vueTsEslintConfig from '@vue/eslint-config-typescript'

export default tseslint.config(
  { ignores: ['dist', 'coverage'] },
  js.configs.recommended,
  // 'essential' (no 'recommended'): reglas que previenen bugs reales.
  // Las reglas de formato estricto ('recommended') no aportan sin un
  // formateador (Prettier) que las haga cumplir automáticamente -- no
  // es parte del stack decidido en docs/definiciones/galgoth-studio-mvp.md.
  ...pluginVue.configs['flat/essential'],
  ...vueTsEslintConfig(),
  {
    rules: {
      'vue/multi-word-component-names': 'off',
    },
  },
)
